package top.ortus.lightmark.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import top.ortus.lightmark.common.exception.ApiException;

/**
 * Shared JWT verifier for business services.
 *
 * The user service signs tokens. Product/order/content services only use this
 * interceptor to verify the same token and never query the user table.
 */
public final class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private final JwtTokenService tokenService;

    public JwtAuthenticationInterceptor(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = bearerToken(request);
        Long userId = token == null ? null : tokenService.resolveUserId(token);
        if (userId == null) {
            throw new ApiException(401, "unauthorized");
        }
        request.setAttribute("userId", userId);
        request.setAttribute("userIdentity", tokenService.resolveIdentity(token));
        return true;
    }

    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }
}
