package top.ortus.lightmark.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import top.ortus.lightmark.backend.config.lightmarkAuthProperties;
import top.ortus.lightmark.backend.dao.User;
import top.ortus.lightmark.backend.dao.UserRepositoryImpl;
import top.ortus.lightmark.backend.dto.auth.AuthLoginRequest;
import top.ortus.lightmark.backend.dto.auth.AuthRegisterRequest;
import top.ortus.lightmark.backend.exception.ApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthValidationServiceUnitTest {

    private UserRepositoryImpl userRepository;
    private AuthValidationService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepositoryImpl.class);
        lightmarkAuthProperties properties = new lightmarkAuthProperties();
        service = new AuthValidationService(properties, userRepository);
    }

    @Test
    void normalizeAccountShouldTrimAndLowercaseEmail() {
        assertThat(service.normalizeAccount("  User@QQ.COM  ")).isEqualTo("user@qq.com");
    }

    @Test
    void normalizeAccountShouldKeepNicknameCase() {
        assertThat(service.normalizeAccount("  LightMarkUser  ")).isEqualTo("LightMarkUser");
    }

    @Test
    void normalizeNicknameShouldTrim() {
        assertThat(service.normalizeNickname("  小李  ")).isEqualTo("小李");
    }

    @Test
    void normalizeNicknameShouldRejectBlankAndTooLongValues() {
        assertBadRequest(() -> service.normalizeNickname(" "));
        assertBadRequest(() -> service.normalizeNickname("a".repeat(31)));
    }

    @Test
    void validatePasswordShouldAcceptStrongPassword() {
        assertThatCode(() -> service.validatePassword("Aa123456!")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short!A", "abcdefgh!", "ABCDEFGH!", "Abcdefgh", "Aa12345678901234567890!"})
    void validatePasswordShouldRejectInvalidPassword(String password) {
        assertBadRequest(() -> service.validatePassword(password));
    }

    @Test
    void normalizeAndValidateEmailShouldAcceptAllowedAndEduDomains() {
        assertThat(service.normalizeAndValidateEmail("  USER@QQ.COM  ")).isEqualTo("user@qq.com");
        assertThat(service.normalizeAndValidateEmail("student@buaa.edu.cn")).isEqualTo("student@buaa.edu.cn");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-email", "user@", "user@example.invalid"})
    void normalizeAndValidateEmailShouldRejectInvalidOrUnsupportedEmail(String email) {
        assertBadRequest(() -> service.normalizeAndValidateEmail(email));
    }

    @Test
    void normalizeCountryCodeShouldAddPlus() {
        assertThat(service.normalizeCountryCode("86")).isEqualTo("+86");
        assertThat(service.normalizeCountryCode("+1")).isEqualTo("+1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "+0", "abcd", "+12345"})
    void normalizeCountryCodeShouldRejectInvalidValues(String countryCode) {
        assertBadRequest(() -> service.normalizeCountryCode(countryCode));
    }

    @Test
    void normalizePhoneShouldRemoveSeparators() {
        assertThat(service.normalizePhone("138 0000-0000")).isEqualTo("13800000000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "1234567890123456", "1380000abcd"})
    void normalizePhoneShouldRejectInvalidValues(String phone) {
        assertBadRequest(() -> service.normalizePhone(phone));
    }

    @Test
    void validateRegistrationRequestShouldRequireVerificationAndCaptcha() {
        AuthRegisterRequest request1 = validRegisterRequest();
        request1.setVerificationCode("");
        assertBadRequest(() -> service.validateRegistrationRequest(request1));

        AuthRegisterRequest request2 = validRegisterRequest();
        request2.setCaptchaCode("");
        assertBadRequest(() -> service.validateRegistrationRequest(request2));
    }

    @Test
    void validateLoginRequestShouldRequireCaptcha() {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setAccount("user@qq.com");
        request.setPassword("Aa123456!");
        request.setCaptchaCode("");

        assertBadRequest(() -> service.validateLoginRequest(request));
    }

    @Test
    void ensureAvailabilityShouldRejectExistingIdentityValues() {
        User existing = new User();
        when(userRepository.findByNicknameOrNull("小李")).thenReturn(existing);
        when(userRepository.findByEmailOrNull("user@qq.com")).thenReturn(existing);
        when(userRepository.findByPhoneOrNull("13800000000")).thenReturn(existing);

        assertConflict(() -> service.ensureNicknameAvailable("小李"));
        assertConflict(() -> service.ensureEmailAvailable("user@qq.com"));
        assertConflict(() -> service.ensurePhoneAvailable("13800000000"));
    }

    @Test
    void findLoginUserShouldRouteByEmailOrNickname() {
        service.findLoginUser("USER@QQ.COM");
        verify(userRepository).findByEmailOrNull("user@qq.com");

        service.findLoginUser("普通用户");
        verify(userRepository).findByNicknameOrNull("普通用户");
    }

    private AuthRegisterRequest validRegisterRequest() {
        AuthRegisterRequest request = new AuthRegisterRequest();
        request.setEmail("user@qq.com");
        request.setNickname("普通用户");
        request.setPassword("Aa123456!");
        request.setVerificationCode("123456");
        request.setCaptchaCode("ABCD");
        return request;
    }

    private void assertBadRequest(ThrowingRunnable runnable) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(400);
    }

    private void assertConflict(ThrowingRunnable runnable) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(ApiException.class)
            .extracting("code")
            .isEqualTo(409);
    }

    private interface ThrowingRunnable {
        void run();
    }
}
