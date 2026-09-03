package top.ortus.lightmark.backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class AIClient {

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String apiKey;
    private final String model;

    public AIClient(RestTemplateBuilder restTemplateBuilder,
                    @Value("${lightmark.ai.api-url:https://api.deepseek.com/chat/completions}") String apiUrl,
                    @Value("${lightmark.ai.api-key:}") String apiKey,
                    @Value("${DEEPSEEK_API_KEY:}") String deepSeekApiKey,
                    @Value("${lightmark.ai.model:deepseek-chat}") String model) {
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(60))
                .build();
        // Keep itinerary AI calls compatible with the customer-service client:
        // deployments may provide either a base URL (/v1) or a full endpoint.
        this.apiUrl = normalizeChatCompletionsUrl(apiUrl);
        this.apiKey = StringUtils.hasText(apiKey) ? apiKey : deepSeekApiKey;
        this.model = model;
    }

    public Optional<String> chat(String prompt) {
        if (!StringUtils.hasText(apiUrl) || !StringUtils.hasText(apiKey)) {
            log.info("AI config missing, skip external model call");
            return Optional.empty();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", new Object[]{
                            Map.of("role", "system", "content", "你是拾光旅行的 AI 助手。请严格按照用户要求输出，涉及结构化结果时必须返回可解析 JSON。"),
                            Map.of("role", "user", "content", prompt)
                    },
                    "temperature", 0.7
            );
            String response = restTemplate.postForObject(apiUrl, new HttpEntity<>(body, headers), String.class);
            return StringUtils.hasText(response) ? Optional.of(response) : Optional.empty();
        } catch (Exception ex) {
            log.warn("AI model call failed: {}", ex.getMessage());
            return Optional.empty();
        }
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
}
