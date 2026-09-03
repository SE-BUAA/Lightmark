package top.ortus.lightmark.user.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import top.ortus.lightmark.common.security.JwtTokenService;
import top.ortus.lightmark.user.converter.UserConverter;
import top.ortus.lightmark.user.dao.User;
import top.ortus.lightmark.user.dao.UserRepositoryImpl;
import top.ortus.lightmark.user.dao.UserLoginLogRepository;
import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.user.dto.auth.AdminLoginRequest;
import top.ortus.lightmark.user.dto.auth.AuthLoginRequest;
import top.ortus.lightmark.user.dto.auth.AuthRegisterRequest;
import top.ortus.lightmark.user.dto.auth.AuthTokenDTO;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.common.security.UserIdentity;
import top.ortus.lightmark.user.utils.UserIdFormatter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证服务类，处理用户注册、登录等认证相关功能
 */
@Service
public class AuthService {

    private final UserRepositoryImpl userRepositoryImpl;
    private final JwtTokenService jwtTokenService;
    private final CaptchaService captchaService;
    private final AuthValidationService authValidationService;
    private final VerificationCodeService verificationCodeService;
    private final QqSmtpEmailService qqSmtpEmailService;
    private final UserLoginLogRepository userLoginLogRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 构造函数
     */
    public AuthService(UserRepositoryImpl userRepositoryImpl,
                       JwtTokenService jwtTokenService,
                       CaptchaService captchaService,
                       AuthValidationService authValidationService,
                       VerificationCodeService verificationCodeService,
                       QqSmtpEmailService qqSmtpEmailService,
                       UserLoginLogRepository userLoginLogRepository) {
        this.userRepositoryImpl = userRepositoryImpl;
        this.jwtTokenService = jwtTokenService;
        this.captchaService = captchaService;
        this.authValidationService = authValidationService;
        this.verificationCodeService = verificationCodeService;
        this.qqSmtpEmailService = qqSmtpEmailService;
        this.userLoginLogRepository = userLoginLogRepository;
    }

    public void sendEmailVerificationCode(String email, String captchaCode, HttpSession session) {
        captchaService.verifyOrThrow(captchaCode, session);
        String normalizedEmail = authValidationService.normalizeAndValidateEmail(email);
        String code = verificationCodeService.generateAndSave(normalizedEmail, VerificationCodeService.CHANNEL_EMAIL);
        qqSmtpEmailService.sendVerificationCode(normalizedEmail, code);
    }

    /**
     * 用户注册
     */
    public UserDTO register(AuthRegisterRequest request, HttpSession session) {
        authValidationService.validateRegistrationRequest(request);
        captchaService.verifyOrThrow(request.getCaptchaCode(), session);
        ensurePrivacyAccepted(request.getPrivacyAccepted(), "注册前请先阅读并同意隐私政策");

        String email = authValidationService.normalizeAndValidateEmail(request.getEmail());
        String nickname = authValidationService.normalizeNickname(request.getNickname());
        authValidationService.ensureEmailAvailable(email);
        authValidationService.ensureNicknameAvailable(nickname);
        verificationCodeService.verifyOrThrow(email, VerificationCodeService.CHANNEL_EMAIL, request.getVerificationCode());

        User user = new User();
        user.setEmail(email);
        user.setNickname(nickname);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setAvatar("");
        user.setPoints(0);
        user.setLevel((short) 0);
        user.setStatus(0);
        user.setRegister_source("EMAIL");
        user.setCreate_time(LocalDateTime.now());
        user.setUpdate_time(LocalDateTime.now());
        user.setDeleted(false);

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String countryCode = authValidationService.normalizeCountryCode(request.getCountryCode());
            String phone = authValidationService.normalizePhone(request.getPhone());
            String fullPhone = countryCode + phone;
            authValidationService.ensurePhoneAvailable(fullPhone);
            user.setPhone(fullPhone);
            user.setCountry_code(countryCode);
        }

        userRepositoryImpl.insert(user);
        User created = userRepositoryImpl.findByEmail(email);
        userRepositoryImpl.assignRole(created.getId(), 2);
        return UserConverter.toDto(created);
    }

    /**
     * 用户登录
     */
    public AuthTokenDTO login(AuthLoginRequest request, HttpSession session, HttpServletRequest httpRequest) {
        authValidationService.validateLoginRequest(request);
        captchaService.verifyOrThrow(request.getCaptchaCode(), session);
        ensurePrivacyAccepted(request.getPrivacyAccepted(), "登录前请先阅读并同意隐私政策");

        User user = authValidationService.findLoginUser(request.getAccount());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(401, "invalid credentials");
        }

        user.setLast_login_time(LocalDateTime.now());
        user.setLast_login_ip(resolveClientIp(httpRequest));
        user.setUpdate_time(LocalDateTime.now());
        userRepositoryImpl.update(user);
        userLoginLogRepository.append(Long.parseLong(user.getId()), user.getLast_login_ip(),
                httpRequest == null ? null : httpRequest.getHeader("User-Agent"));

        UserIdentity identity = userRepositoryImpl.findIdentityByUserId(user.getId());
        List<String> roles = List.of(identity.name());
        String token = jwtTokenService.createToken(Long.valueOf(user.getId()), user.getNickname(), identity);
        return new AuthTokenDTO(token, UserIdFormatter.format16(user.getId()), user.getNickname(), user.getAvatar(), identity.name(), roles);
    }

    /**
     * 管理后台登录：不校验图形验证码与隐私协议，但必须校验密码与 ADMIN 角色。
     */
    public AuthTokenDTO adminLogin(AdminLoginRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.getAccount() == null || request.getAccount().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            throw new ApiException(400, "invalid request");
        }
        User user = authValidationService.findLoginUser(request.getAccount());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(401, "invalid credentials");
        }
        UserIdentity identity = userRepositoryImpl.findIdentityByUserId(user.getId());
        if (identity != UserIdentity.ADMIN) {
            throw new ApiException(403, "该账号无后台权限");
        }

        user.setLast_login_time(LocalDateTime.now());
        user.setLast_login_ip(resolveClientIp(httpRequest));
        user.setUpdate_time(LocalDateTime.now());
        userRepositoryImpl.update(user);
        userLoginLogRepository.append(Long.parseLong(user.getId()), user.getLast_login_ip(),
                httpRequest == null ? null : httpRequest.getHeader("User-Agent"));

        String token = jwtTokenService.createToken(Long.valueOf(user.getId()), user.getNickname(), identity);
        return new AuthTokenDTO(token, UserIdFormatter.format16(user.getId()), user.getNickname(), user.getAvatar(), identity.name(), List.of(identity.name()));
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void ensurePrivacyAccepted(Boolean privacyAccepted, String message) {
        if (!Boolean.TRUE.equals(privacyAccepted)) {
            throw new ApiException(400, message);
        }
    }
}
