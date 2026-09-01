package top.ortus.lightmark.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.ortus.lightmark.common.security.JwtAuthenticationInterceptor;
import top.ortus.lightmark.common.security.JwtTokenService;

/** Protects future order internal endpoints with the shared JWT verifier. */
@Configuration
public class OrderServiceSecurityConfig implements WebMvcConfigurer {

    private final JwtTokenService jwtTokenService;

    public OrderServiceSecurityConfig(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtAuthenticationInterceptor(jwtTokenService))
                .addPathPatterns("/internal/**");
    }
}
