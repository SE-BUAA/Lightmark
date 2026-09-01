package top.ortus.lightmark.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import top.ortus.lightmark.order.tools.ApiException;
import top.ortus.lightmark.order.tools.ApiResponse;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Component
public class UserClient {

    private final WebClient webClient;
    private final Duration timeout;

    public UserClient(@Qualifier("userWebClient") WebClient webClient,
                      @Value("${order.clients.timeout-ms:3000}") long timeoutMs) {
        this.webClient = webClient;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    @Retry(name = "user-service")
    @CircuitBreaker(name = "user-service", fallbackMethod = "pointsFallback")
    public void adjustPoints(Long userId, String action, String orderId, String source, BigDecimal paidAmount) {
        ApiResponse<Boolean> response = webClient.post()
                .uri("/internal/user/{id}/points", userId)
                .bodyValue(Map.of(
                        "action", action,
                        "orderId", orderId,
                        "source", source,
                        "paidAmount", paidAmount == null ? BigDecimal.ZERO : paidAmount
                ))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Boolean>>() {})
                .block(timeout);
        if (response == null || response.getCode() != 0 || !Boolean.TRUE.equals(response.getData())) {
            throw new ApiException(503, response == null ? "user service unavailable" : response.getMsg());
        }
    }

    @SuppressWarnings("unused")
    private void pointsFallback(Long userId, String action, String orderId, String source, BigDecimal paidAmount, Throwable throwable) {
        throw new ApiException(503, "积分服务暂不可用");
    }
}
