package top.ortus.lightmark.content.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.ortus.lightmark.common.security.JwtAuthenticationInterceptor;
import top.ortus.lightmark.common.security.JwtTokenService;

/** Protects future content internal endpoints with the shared JWT verifier. */
@Configuration
public class ContentServiceSecurityConfig implements WebMvcConfigurer {

    private final JwtTokenService jwtTokenService;

    public ContentServiceSecurityConfig(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtAuthenticationInterceptor(jwtTokenService))
                .addPathPatterns("/internal/**");
    }
}
