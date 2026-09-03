package top.ortus.lightmark.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.ortus.lightmark.backend.dto.AiDTO;
import top.ortus.lightmark.backend.dto.chat.ChatContextDTO;
import top.ortus.lightmark.backend.dto.chat.ChatMessageDTO;
import top.ortus.lightmark.backend.tools.UpdateEmailTool;
import top.ortus.lightmark.backend.tools.UpdateNicknameTool;
import top.ortus.lightmark.backend.tools.WebSearchTool;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeepSeekNativeChatService {
    private static final int MAX_HISTORY_SIZE = 40;
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final UpdateNicknameTool updateNicknameTool;
    private final UpdateEmailTool updateEmailTool;
    private final WebSearchTool webSearchTool;

    private final String apiUrl;
    private final String apiKey;
    private final String model;

    private final ConcurrentHashMap<String, List<Map<String, Object>>> conversations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> systemPrompts = new ConcurrentHashMap<>();

    public DeepSeekNativeChatService(ObjectMapper objectMapper,
                                     UpdateNicknameTool updateNicknameTool,
                                     UpdateEmailTool updateEmailTool,
                                     WebSearchTool webSearchTool,
                                     @Value("${lightmark.ai.api-url:https://api.deepseek.com/chat/completions}") String apiUrl,
                                     @Value("${lightmark.ai.api-key:}") String apiKey,
                                     @Value("${DEEPSEEK_API_KEY:}") String deepSeekApiKey,
                                     @Value("${lightmark.ai.model:deepseek-chat}") String model) {
        this.objectMapper = objectMapper;
        this.updateNicknameTool = updateNicknameTool;
        this.updateEmailTool = updateEmailTool;
        this.webSearchTool = webSearchTool;
        this.apiUrl = normalizeChatCompletionsUrl(apiUrl);
        this.apiKey = StringUtils.hasText(apiKey) ? apiKey : deepSeekApiKey;
        this.model = model;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public AiDTO chat(String sessionId, String userMessage, String systemPrompt) {
        String safeSessionId = sessionId == null ? "" : sessionId;
        List<Map<String, Object>> history = conversations.computeIfAbsent(safeSessionId, id -> new ArrayList<>());

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            systemPrompts.put(safeSessionId, systemPrompt);
        }

        history.add(userMessage(userMessage == null ? "" : userMessage));
        trimHistory(history);

        String effectiveSystemPrompt = buildTimeAwareSystemPrompt(systemPrompts.getOrDefault(safeSessionId, ""));

        String content = runToolLoop(history, effectiveSystemPrompt);

        AiDTO aiDTO = new AiDTO();
        aiDTO.setContent(content == null ? "" : content);
        aiDTO.setModel(model);
        return aiDTO;
    }

    public void streamChat(String sessionId, String userMessage, SseEmitter emitter) {
        new Thread(() -> {
            try {
                AiDTO dto = chat(sessionId, userMessage, null);
                String aiMessage = dto.getContent() == null ? "" : dto.getContent();
                int chunkSize = 100;
                for (int start = 0; start < aiMessage.length(); start += chunkSize) {
                    int end = Math.min(aiMessage.length(), start + chunkSize);
                    String piece = aiMessage.substring(start, end);
                    emitter.send(SseEmitter.event().name("partial").data(piece));
                }
                emitter.send(SseEmitter.event().name("complete").data("__complete__"));
                emitter.complete();
            } catch (Exception ex) {
                try {
                    emitter.send(SseEmitter.event().name("error").data("AI error: " + ex.getMessage()));
                } catch (Exception ignore) {}
                emitter.completeWithError(ex);
            }
        }).start();
    }

    public ChatContextDTO getContext(String sessionId) {
        String safeSessionId = sessionId == null ? "" : sessionId;
        List<Map<String, Object>> history = conversations.getOrDefault(safeSessionId, List.of());
        List<ChatMessageDTO> items = new ArrayList<>();
        for (Map<String, Object> m : history) {
            String role = String.valueOf(m.getOrDefault("role", ""));
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            String content = extractContentText(m.get("content"));
            items.add(new ChatMessageDTO(role, content));
        }
        return new ChatContextDTO(systemPrompts.getOrDefault(safeSessionId, ""), items);
    }

    public boolean resetContext(String sessionId) {
        String safeSessionId = sessionId == null ? "" : sessionId;
        conversations.remove(safeSessionId);
        systemPrompts.remove(safeSessionId);
        return true;
    }

    private String runToolLoop(List<Map<String, Object>> history, String effectiveSystemPrompt) {
        for (int i = 0; i < 6; i++) {
            JsonNode response = callDeepSeek(buildRequestBody(history, effectiveSystemPrompt));
            JsonNode messageNode = response.path("choices").path(0).path("message");
            if (messageNode.isMissingNode() || messageNode.isNull()) {
                throw new IllegalStateException("deepseek_response_missing_message");
            }

            Map<String, Object> assistantMessage = objectMapper.convertValue(messageNode, Map.class);
            history.add(assistantMessage);
            trimHistory(history);

            JsonNode toolCalls = messageNode.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                return extractContentText(assistantMessage.get("content"));
            }

            for (JsonNode toolCall : toolCalls) {
                Map<String, Object> toolMsg = executeToolCall(toolCall);
                history.add(toolMsg);
                trimHistory(history);
            }
        }
        throw new IllegalStateException("tool_loop_exceeded");
    }

    private Map<String, Object> executeToolCall(JsonNode toolCall) {
        String toolCallId = toolCall.path("id").asText("");
        JsonNode functionNode = toolCall.path("function");
        String toolName = functionNode.path("name").asText("");
        String arguments = functionNode.path("arguments").asText("{}");

        Map<String, Object> args;
        try {
            args = objectMapper.readValue(arguments, Map.class);
        } catch (IOException ex) {
            args = new HashMap<>();
        }

        Map<String, Object> toolResult = switch (toolName) {
            case "update_nickname" -> updateNicknameTool.updateNickname(
                    asString(args.get("userId")),
                    asString(args.get("nickname"))
            );
            case "update_email" -> updateEmailTool.updateEmail(
                    asString(args.get("userId")),
                    asString(args.get("email"))
            );
            case "search_web" -> webSearchTool.searchWeb(asString(args.get("query")));
            default -> Map.of("success", false, "message", "unknown_tool:" + toolName);
        };

        Map<String, Object> toolMessage = new HashMap<>();
        toolMessage.put("role", "tool");
        toolMessage.put("tool_call_id", toolCallId);
        toolMessage.put("content", toJson(toolResult));
        return toolMessage;
    }

    private Map<String, Object> buildRequestBody(List<Map<String, Object>> history, String effectiveSystemPrompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMessage(effectiveSystemPrompt));
        messages.addAll(history);

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("tools", toolDefinitions());
        body.put("tool_choice", "auto");
        return body;
    }

    private JsonNode callDeepSeek(Map<String, Object> requestBody) {
        if (!StringUtils.hasText(apiUrl) || !StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("deepseek_config_missing");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(requestBody), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " - " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("deepseek_call_failed: " + ex.getMessage(), ex);
        }
    }

    private List<Map<String, Object>> toolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(toolDef("search_web", "Search the web for information.", Map.of(
                "type", "object",
                "properties", Map.of("query", Map.of("type", "string", "description", "Search keywords")),
                "required", List.of("query")
        )));

        tools.add(toolDef("update_email", "Update the user's email by user id.", Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of("type", "string"),
                        "email", Map.of("type", "string")
                ),
                "required", List.of("userId", "email")
        )));

        tools.add(toolDef("update_nickname", "Update the user's nickname by user id.", Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of("type", "string"),
                        "nickname", Map.of("type", "string")
                ),
                "required", List.of("userId", "nickname")
        )));

        return tools;
    }

    private Map<String, Object> toolDef(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new HashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> tool = new HashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private Map<String, Object> systemMessage(String prompt) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "system");
        message.put("content", prompt);
        return message;
    }

    private Map<String, Object> userMessage(String text) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", text);
        return message;
    }

    private String buildTimeAwareSystemPrompt(String systemPrompt) {
        String now = LocalDateTime.now(ZONE_ID).format(TIME_FORMATTER);
        String timeContext = "当前请求时间（Asia/Shanghai）: " + now;
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return timeContext;
        }
        return systemPrompt + "\n\n" + timeContext;
    }

    private void trimHistory(List<Map<String, Object>> history) {
        while (history.size() > MAX_HISTORY_SIZE) {
            history.remove(0);
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeChatCompletionsUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("json_serialize_failed", ex);
        }
    }

    private String extractContentText(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Object text = map.get("text");
                    if (text != null) {
                        sb.append(text);
                    }
                } else if (item != null) {
                    sb.append(item);
                }
            }
            return sb.toString();
        }
        return String.valueOf(content);
    }
}
