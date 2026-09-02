package top.ortus.lightmark.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import top.ortus.lightmark.order.tools.security.JwtTokenService;

@Configuration
public class OrderInfrastructureConfig {

    @Bean
    WebClient productWebClient(@Value("${order.clients.product-base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    WebClient userWebClient(@Value("${order.clients.user-base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    JwtTokenService orderJwtTokenService(@Value("${lightmark.jwt.secret}") String secret,
                                    @Value("${lightmark.jwt.issuer}") String issuer,
                                    @Value("${lightmark.jwt.expire-minutes}") long expireMinutes) {
        return new JwtTokenService(secret, issuer, expireMinutes);
    }
}
