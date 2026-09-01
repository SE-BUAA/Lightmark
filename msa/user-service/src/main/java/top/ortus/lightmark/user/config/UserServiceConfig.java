package top.ortus.lightmark.user.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.user.security.UserAuthInterceptor;

@Configuration
public class UserServiceConfig implements WebMvcConfigurer {

    private final JwtTokenService jwtTokenService;

    public UserServiceConfig(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UserAuthInterceptor(jwtTokenService))
                .addPathPatterns("/api/user/**", "/api/admin/**", "/internal/user/**");
    }
}
