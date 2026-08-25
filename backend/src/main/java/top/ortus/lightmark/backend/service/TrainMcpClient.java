package top.ortus.lightmark.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Component
public class TrainMcpClient {
    private static final Logger log = LoggerFactory.getLogger(TrainMcpClient.class);
    private static final String MCP_URL = "http://150.230.223.11:9000/mcp";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<String> sessionId = new AtomicReference<>();
    private final AtomicInteger requestId = new AtomicInteger(2);

    public TrainMcpClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);
        this.restClient = RestClient.builder()
            .baseUrl(MCP_URL)
            .requestFactory(requestFactory)
            .build();
    }

    @PostConstruct
    public void init() {
        refreshSessionId();
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    public synchronized void refreshSessionId() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", "initialize");
        body.put("params", Map.of(
            "protocolVersion", "2025-03-26",
            "capabilities", Map.of(),
            "clientInfo", Map.of("name", "lightmark-backend", "version", "1.0")
        ));

        try {
            String newSessionId = restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .body(body)
                .exchange((request, response) -> response.getHeaders().getFirst(SESSION_HEADER));
            if (newSessionId != null && !newSessionId.isBlank()) {
                sessionId.set(newSessionId);
                sendInitializedNotification(newSessionId);
            }
        } catch (RuntimeException ignored) {
            // Remote 12306 MCP availability should not prevent the backend from starting.
        }
    }

    public synchronized Map<String, Object> callTool(String name, Map<String, Object> arguments) {
        ensureSession();
        try {
            return doCallTool(name, arguments);
        } catch (RuntimeException ex) {
            refreshSessionId();
            try {
                return doCallTool(name, arguments);
            } catch (RuntimeException ignored) {
                return Map.of("success", false, "count", 0);
            }
        }
    }

    public synchronized Map<String, Object> callToolFast(String name, Map<String, Object> arguments) {
        ensureSession();
        try {
            return doCallTool(name, arguments);
        } catch (RuntimeException ex) {
            log.warn("12306 MCP tool {} failed: {}", name, ex.getMessage());
            return Map.of("success", false, "count", 0);
        }
    }

    private void sendInitializedNotification(String newSessionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("method", "notifications/initialized");
        body.put("params", Map.of());
        try {
            restClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .header(SESSION_HEADER, newSessionId)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException ignored) {
            // Some MCP servers do not return a body for notifications.
        }
    }

    private Map<String, Object> doCallTool(String name, Map<String, Object> arguments) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", requestId.incrementAndGet());
        body.put("method", "tools/call");
        body.put("params", Map.of("name", name, "arguments", arguments));

        String responseBody = restClient.post()
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .header(SESSION_HEADER, sessionId.get())
            .body(body)
            .retrieve()
            .body(String.class);

        JsonNode response = parseJsonRpcResponse(responseBody);
        String text = response == null ? null : response.at("/result/content/0/text").asText(null);
        if (text == null || text.isBlank()) {
            log.warn("12306 MCP tool {} returned empty content", name);
            return Map.of("success", false, "count", 0);
        }
        try {
            Map<String, Object> parsed = parseToolContent(text);
            log.info("12306 MCP tool {} success={}, count={}", name, parsed.get("success"), parsed.get("count"));
            if (Boolean.FALSE.equals(parsed.get("success"))) {
                log.warn("12306 MCP tool {} returned failure: error={}, detail={}",
                    name, parsed.get("error"), parsed.get("detail"));
            }
            return parsed;
        } catch (Exception ex) {
            log.warn("12306 MCP tool {} returned unparsable content: {}", name, summarize(text));
            throw new IllegalArgumentException("12306接口返回数据解析失败");
        }
    }

    private Map<String, Object> parseToolContent(String text) throws Exception {
        String json = normalizeJsonText(text);
        return objectMapper.readValue(json, new TypeReference<>() {});
    }

    private JsonNode parseJsonRpcResponse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String json = body.trim();
        if (json.startsWith("event:") || json.startsWith("data:")) {
            json = body.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .filter(line -> !line.isBlank() && !"[DONE]".equals(line))
                .findFirst()
                .orElse("");
        }
        if (!json.startsWith("{")) {
            Matcher matcher = JSON_OBJECT_PATTERN.matcher(json);
            json = matcher.find() ? matcher.group() : "";
        }
        if (json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeJsonText(String text) {
        String json = text == null ? "" : text.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        if (json.startsWith("event:") || json.startsWith("data:")) {
            json = json.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .filter(line -> !line.isBlank() && !"[DONE]".equals(line))
                .findFirst()
                .orElse("");
        }
        if (!json.startsWith("{")) {
            Matcher matcher = JSON_OBJECT_PATTERN.matcher(json);
            json = matcher.find() ? matcher.group() : json;
        }
        return json;
    }

    private String summarize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private void ensureSession() {
        if (sessionId.get() == null || sessionId.get().isBlank()) {
            refreshSessionId();
        }
    }
}
