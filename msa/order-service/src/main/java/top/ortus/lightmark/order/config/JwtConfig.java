package top.ortus.lightmark.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.ortus.lightmark.common.security.JwtTokenService;

/** Order service validates user-service tokens with the shared secret. */
@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenService jwtTokenService(
            @Value("${lightmark.auth.jwt.secret:}") String secret,
            @Value("${lightmark.auth.jwt.issuer:lightmark}") String issuer,
            @Value("${lightmark.auth.jwt.expire-minutes:120}") long expireMinutes) {
        return new JwtTokenService(secret, issuer, expireMinutes);
    }
}
