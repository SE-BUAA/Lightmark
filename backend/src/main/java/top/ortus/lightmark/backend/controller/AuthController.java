package top.ortus.lightmark.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.ortus.lightmark.backend.common.ApiResponse;
import top.ortus.lightmark.backend.dto.UserDTO;
import top.ortus.lightmark.backend.dto.auth.AdminLoginRequest;
import top.ortus.lightmark.backend.dto.auth.AuthLoginRequest;
import top.ortus.lightmark.backend.dto.auth.AuthRegisterRequest;
import top.ortus.lightmark.backend.dto.auth.AuthTokenDTO;
import top.ortus.lightmark.backend.dto.auth.EmailVerificationSendRequest;
import top.ortus.lightmark.backend.service.AuthService;
import top.ortus.lightmark.backend.service.CaptchaService;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public void captcha(HttpServletResponse response, HttpSession session) throws IOException {
        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        captchaService.writeCaptchaImage(response.getOutputStream(), session);
    }

    @PostMapping("/verification/email/send")
    public ApiResponse<Boolean> sendEmailVerification(@Valid @RequestBody EmailVerificationSendRequest request,
                                                      HttpSession session) {
        authService.sendEmailVerificationCode(request.getEmail(), request.getCaptchaCode(), session);
        return ApiResponse.ok(true);
    }

    @PostMapping("/register")
    public ApiResponse<UserDTO> register(@Valid @RequestBody AuthRegisterRequest request, HttpSession session) {
        return ApiResponse.ok(authService.register(request, session));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenDTO> login(@Valid @RequestBody AuthLoginRequest request,
                                           HttpSession session,
                                           HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, session, httpRequest));
    }

    /**
     * 管理后台登录：不校验图形验证码与隐私协议，但校验密码与 ADMIN 角色。
     */
    @PostMapping("/admin/login")
    public ApiResponse<AuthTokenDTO> adminLogin(@Valid @RequestBody AdminLoginRequest request,
                                                HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.adminLogin(request, httpRequest));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        return ApiResponse.ok(true);
    }
}
