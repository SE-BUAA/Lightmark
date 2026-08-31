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

import java.time.Duration;
import java.util.Map;

@Component
public class ProductClient {

    private final WebClient webClient;
    private final Duration timeout;

    public ProductClient(@Qualifier("productWebClient") WebClient webClient,
                         @Value("${order.clients.timeout-ms:3000}") long timeoutMs) {
        this.webClient = webClient;
        this.timeout = Duration.ofMillis(timeoutMs);
    }

    @Retry(name = "product-service")
    @CircuitBreaker(name = "product-service", fallbackMethod = "productFallback")
    public Map<String, Object> getProduct(String productId) {
        ApiResponse<Map<String, Object>> response = webClient.get()
                .uri("/internal/product/{id}", productId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {})
                .block(timeout);
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new ApiException(503, response == null ? "product service unavailable" : response.getMsg());
        }
        return response.getData();
    }

    @Retry(name = "product-service")
    @CircuitBreaker(name = "product-service", fallbackMethod = "stockFallback")
    public void adjustStock(String productId, int delta) {
        ApiResponse<Boolean> response = webClient.post()
                .uri("/internal/product/{id}/stock", productId)
                .bodyValue(Map.of("delta", delta))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<Boolean>>() {})
                .block(timeout);
        if (response == null || response.getCode() != 0 || !Boolean.TRUE.equals(response.getData())) {
            throw new ApiException(503, response == null ? "product service unavailable" : response.getMsg());
        }
    }

    @SuppressWarnings("unused")
    private Map<String, Object> productFallback(String productId, Throwable throwable) {
        throw new ApiException(503, "服务繁忙，请稍后再试");
    }

    @SuppressWarnings("unused")
    private void stockFallback(String productId, int delta, Throwable throwable) {
        throw new ApiException(503, "服务繁忙，请稍后再试");
    }
}
