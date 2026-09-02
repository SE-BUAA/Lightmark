package top.ortus.lightmark.user.service;

import org.springframework.stereotype.Service;
import top.ortus.lightmark.user.config.LightmarkAuthProperties;
import top.ortus.lightmark.user.dao.User;
import top.ortus.lightmark.user.dao.UserRepositoryImpl;
import top.ortus.lightmark.user.dto.auth.AuthLoginRequest;
import top.ortus.lightmark.user.dto.auth.AuthRegisterRequest;
import top.ortus.lightmark.common.exception.ApiException;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class AuthValidationService {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*[!#@\\$%\\^&*()_])[A-Za-z\\d!#@\\$%\\^&*()_]{8,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNTRY_CODE_PATTERN = Pattern.compile("^\\+?[1-9]\\d{0,3}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{6,15}$");

    private final LightmarkAuthProperties authProperties;
    private final UserRepositoryImpl userRepositoryImpl;

    public AuthValidationService(LightmarkAuthProperties authProperties, UserRepositoryImpl userRepositoryImpl) {
        this.authProperties = authProperties;
        this.userRepositoryImpl = userRepositoryImpl;
    }

    public String normalizeAccount(String account) {
        if (account == null || account.isBlank()) {
            throw new ApiException(400, "account is required");
        }
        String normalized = account.trim();
        if (normalized.contains("@")) {
            return normalized.toLowerCase(Locale.ROOT);
        }
        return normalized;
    }

    public String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new ApiException(400, "nickname is required");
        }
        String normalized = nickname.trim();
        if (normalized.length() > 30) {
            throw new ApiException(400, "nickname must be at most 30 characters");
        }
        return normalized;
    }

    public void validatePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new ApiException(400, "password is required");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ApiException(400, "password must be 8-20 chars and include uppercase, lowercase, and special character");
        }
    }

    public String normalizeAndValidateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ApiException(400, "email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(400, "email format is invalid");
        }
        int atIndex = normalized.lastIndexOf('@');
        String domain = atIndex >= 0 ? normalized.substring(atIndex + 1) : "";
        boolean allowed = authProperties.getVerification().getAllowedEmailDomains().stream()
                .anyMatch(item -> item.equalsIgnoreCase(domain));
        if (!allowed && authProperties.getVerification().isAllowEduCn() && domain.endsWith(".edu.cn")) {
            allowed = true;
        }
        if (!allowed) {
            throw new ApiException(400, "email domain is not supported");
        }
        return normalized;
    }

    public String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new ApiException(400, "countryCode is required");
        }
        String normalized = countryCode.trim();
        if (!COUNTRY_CODE_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(400, "countryCode is invalid");
        }
        return normalized.startsWith("+") ? normalized : "+" + normalized;
    }

    public String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new ApiException(400, "phone is required");
        }
        String normalized = phone.trim().replace(" ", "").replace("-", "");
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new ApiException(400, "phone is invalid");
        }
        return normalized;
    }

    public void validateRegistrationRequest(AuthRegisterRequest request) {
        if (request == null) {
            throw new ApiException(400, "request is required");
        }
        normalizeAndValidateEmail(request.getEmail());
        normalizeNickname(request.getNickname());
        validatePassword(request.getPassword());
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            normalizeCountryCode(request.getCountryCode());
            normalizePhone(request.getPhone());
        }
        if (request.getVerificationCode() == null || request.getVerificationCode().isBlank()) {
            throw new ApiException(400, "verificationCode is required");
        }
        if (request.getCaptchaCode() == null || request.getCaptchaCode().isBlank()) {
            throw new ApiException(400, "captchaCode is required");
        }
    }

    public void validateLoginRequest(AuthLoginRequest request) {
        if (request == null) {
            throw new ApiException(400, "request is required");
        }
        normalizeAccount(request.getAccount());
        validatePassword(request.getPassword());
        if (request.getCaptchaCode() == null || request.getCaptchaCode().isBlank()) {
            throw new ApiException(400, "captchaCode is required");
        }
    }

    public void ensureNicknameAvailable(String nickname) {
        if (userRepositoryImpl.findByNicknameOrNull(nickname) != null) {
            throw new ApiException(409, "nickname already exists");
        }
    }

    public void ensureEmailAvailable(String email) {
        if (userRepositoryImpl.findByEmailOrNull(email) != null) {
            throw new ApiException(409, "email already exists");
        }
    }

    public void ensurePhoneAvailable(String phone) {
        if (userRepositoryImpl.findByPhoneOrNull(phone) != null) {
            throw new ApiException(409, "phone already exists");
        }
    }

    public User findLoginUser(String account) {
        String normalized = normalizeAccount(account);
        if (normalized.contains("@")) {
            return userRepositoryImpl.findByEmailOrNull(normalized);
        }
        // Keep both legacy login modes: phone number and nickname.
        User byPhone = userRepositoryImpl.findByPhoneOrNull(normalized);
        return byPhone != null ? byPhone : userRepositoryImpl.findByNicknameOrNull(normalized);
    }
}
