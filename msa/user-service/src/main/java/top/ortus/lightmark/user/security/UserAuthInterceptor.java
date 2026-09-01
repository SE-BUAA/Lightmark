package top.ortus.lightmark.user.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.common.security.UserIdentity;

public class UserAuthInterceptor implements HandlerInterceptor {

    private final JwtTokenService jwtTokenService;

    public UserAuthInterceptor(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = bearerToken(request);
        Long userId = token == null ? null : jwtTokenService.resolveUserId(token);
        if (userId == null) {
            throw new ApiException(401, "unauthorized");
        }
        request.setAttribute("userId", userId);
        request.setAttribute("userIdentity", jwtTokenService.resolveIdentity(token));
        if (request.getRequestURI().startsWith("/api/admin/")
                && jwtTokenService.resolveIdentity(token) != UserIdentity.ADMIN) {
            throw new ApiException(403, "admin permission required");
        }
        return true;
    }

    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
