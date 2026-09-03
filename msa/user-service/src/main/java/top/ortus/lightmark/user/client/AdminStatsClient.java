package top.ortus.lightmark.user.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import top.ortus.lightmark.common.security.JwtTokenService;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 后台看板跨域统计客户端:经各域 internal 接口聚合 order/product 数据。
 * 依赖故障/超时返回空值,看板降级为空数据而不是 500(与降级策略一致)。
 */
@Component
public class AdminStatsClient {

    private final RestClient orderClient;
    private final RestClient productClient;
    private final JwtTokenService jwtTokenService;
    private final ObjectMapper mapper;

    public AdminStatsClient(@Value("${lightmark.services.order-url:http://order-service:8083}") String orderBaseUrl,
                            @Value("${lightmark.services.product-url:http://product-service:8082}") String productBaseUrl,
                            JwtTokenService jwtTokenService,
                            ObjectMapper mapper) {
        this.orderClient = build(orderBaseUrl);
        this.productClient = build(productBaseUrl);
        this.jwtTokenService = jwtTokenService;
        this.mapper = mapper;
    }

    private static RestClient build(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(2000);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private String authHeader() {
        return "Bearer " + jwtTokenService.createToken(0L, "user-service", List.of("SERVICE"));
    }

    public Map<String, Object> orderSummary() {
        Map<String, Object> data = getMap(orderClient, "/internal/admin/stats/orders/summary");
        return data == null ? Collections.emptyMap() : data;
    }

    public List<Map<String, Object>> orderTrends() {
        List<Map<String, Object>> data = getList(orderClient, "/internal/admin/stats/orders/trends?days=7");
        return data == null ? Collections.emptyList() : data;
    }

    public List<Map<String, Object>> hotProducts() {
        List<Map<String, Object>> data = getList(productClient, "/internal/product/hot?limit=10");
        return data == null ? Collections.emptyList() : data;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(RestClient client, String path) {
        JsonNode node = get(client, path);
        if (node == null) {
            return null;
        }
        return mapper.convertValue(node, Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(RestClient client, String path) {
        JsonNode node = get(client, path);
        if (node == null || !node.isArray()) {
            return null;
        }
        return mapper.convertValue(node, List.class);
    }

    private JsonNode get(RestClient client, String path) {
        try {
            String raw = client.get().uri(path)
                    .header("Authorization", authHeader())
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                return null;
            }
            JsonNode root = mapper.readTree(raw);
            JsonNode data = root.path("data");
            return (data.isMissingNode() || data.isNull()) ? null : data;
        } catch (Exception ignored) {
            // 依赖域故障/超时:返回 null,由看板降级为空值
            return null;
        }
    }
}
