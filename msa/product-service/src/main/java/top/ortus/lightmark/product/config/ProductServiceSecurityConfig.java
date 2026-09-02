package top.ortus.lightmark.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.ortus.lightmark.common.security.JwtAuthenticationInterceptor;
import top.ortus.lightmark.common.security.JwtTokenService;

/** Protects future product-to-product internal endpoints without table access. */
@Configuration
public class ProductServiceSecurityConfig implements WebMvcConfigurer {

    private final JwtTokenService jwtTokenService;

    public ProductServiceSecurityConfig(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JwtAuthenticationInterceptor(jwtTokenService))
                .addPathPatterns("/internal/**");
    }
}
