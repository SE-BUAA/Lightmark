package top.ortus.lightmark.content.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import top.ortus.lightmark.common.security.JwtTokenService;

import java.util.List;
import java.util.Map;

/** 通过 user-service 内部接口获取脱敏资料，失败时返回默认资料，避免跨库查询。 */
@Component
public class UserProfileClient {
    private final RestClient client;
    private final JwtTokenService jwtTokenService;

    public UserProfileClient(@Value("${lightmark.services.user-url:http://user-service:8081}") String baseUrl,
                             JwtTokenService jwtTokenService) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(2000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.jwtTokenService = jwtTokenService;
    }

    public Map<String, Object> getProfile(long userId) {
        try {
            // user-service 拦截器要求 /internal/** 携带有效 JWT,使用服务身份令牌(与 order-service 一致)
            Map<?, ?> response = client.get().uri("/internal/user/{id}", userId)
                    .header("Authorization", "Bearer " + serviceToken())
                    .retrieve().body(Map.class);
            Object data = response == null ? null : response.get("data");
            if (data instanceof Map<?, ?> map) {
                // 通配符 Map 不能直接调用带默认值的泛型方法，显式读取可避免类型污染。
                Object nickname = map.get("nickname");
                Object avatar = map.get("avatar");
                return Map.of("nickname", nickname == null ? "旅行用户" : String.valueOf(nickname),
                        "avatar", avatar == null ? "" : String.valueOf(avatar));
            }
        } catch (RuntimeException ignored) {
            // 依赖服务不可用时使用默认资料，社区主体内容仍可正常返回。
        }
        return Map.of("nickname", "旅行用户", "avatar", "");
    }

    private String serviceToken() {
        return jwtTokenService.createToken(0L, "content-service", List.of("SERVICE"));
    }
}
