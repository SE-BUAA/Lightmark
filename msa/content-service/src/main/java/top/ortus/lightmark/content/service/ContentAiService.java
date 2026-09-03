package top.ortus.lightmark.content.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** 内容域 AI 适配器。外部服务异常时返回可解释的降级内容，不影响主业务。 */
@Service
public class ContentAiService {
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String url;
    private final String key;
    private final String model;

    public ContentAiService(ObjectMapper mapper,
                            @Value("${lightmark.ai.api-url:https://api.deepseek.com/chat/completions}") String url,
                            @Value("${lightmark.ai.api-key:}") String key,
                            @Value("${DEEPSEEK_API_KEY:}") String envKey,
                            @Value("${lightmark.ai.model:deepseek-chat}") String model) {
        this.mapper = mapper; this.url = normalizeChatCompletionsUrl(url); this.key = StringUtils.hasText(key) ? key : envKey; this.model = model;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    public Map<String, Object> chat(String message, String systemPrompt) {
        String fallback = "AI 服务暂时不可用，请稍后重试。你的问题是：" + message;
        if (!StringUtils.hasText(key)) return Map.of("content", fallback, "model", model, "degraded", true);
        try {
            Map<String, Object> body = Map.of("model", model, "temperature", 0.0, "messages", List.of(
                    Map.of("role", "system", "content", StringUtils.hasText(systemPrompt) ? systemPrompt : "你是拾光旅行助手。"),
                    Map.of("role", "user", "content", message)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + key).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException("AI HTTP " + response.statusCode());
            JsonNode content = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) throw new IllegalStateException("AI response missing content");
            return Map.of("content", content.asText(), "model", model, "degraded", false);
        } catch (Exception ignored) {
            return Map.of("content", fallback, "model", model, "degraded", true);
        }
    }

    private String normalizeChatCompletionsUrl(String value) {
        if (!StringUtils.hasText(value)) return "";
        String normalized = value.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized.endsWith("/chat/completions") ? normalized : normalized + "/chat/completions";
    }
}
