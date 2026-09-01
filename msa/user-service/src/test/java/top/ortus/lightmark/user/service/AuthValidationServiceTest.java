package top.ortus.lightmark.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.user.config.LightmarkAuthProperties;
import top.ortus.lightmark.user.dao.UserRepositoryImpl;
import top.ortus.lightmark.user.dto.auth.AuthLoginRequest;
import top.ortus.lightmark.user.dto.auth.AuthRegisterRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AuthValidationServiceTest {
    private AuthValidationService service;

    @BeforeEach
    void setUp() {
        service = new AuthValidationService(new LightmarkAuthProperties(), mock(UserRepositoryImpl.class));
    }

    @Test
    void normalizesEmailAccountAndPhone() {
        assertEquals("person@qq.com", service.normalizeAccount(" Person@QQ.COM "));
        assertEquals("+86", service.normalizeCountryCode("86"));
        assertEquals("13800138000", service.normalizePhone("138-0013 8000"));
    }

    @Test
    void rejectsUnsupportedEmailAndWeakPassword() {
        ApiException email = assertThrows(ApiException.class,
                () -> service.normalizeAndValidateEmail("person@example.com"));
        assertEquals(400, email.getCode());
        ApiException password = assertThrows(ApiException.class,
                () -> service.validatePassword("weakpass"));
        assertEquals(400, password.getCode());
    }

    @Test
    void validatesRegistrationAndLoginRequiredFields() {
        AuthRegisterRequest register = new AuthRegisterRequest();
        register.setEmail("person@qq.com");
        register.setNickname("person");
        register.setPassword("Password!1");
        register.setVerificationCode("123456");
        register.setCaptchaCode("ABCD");
        service.validateRegistrationRequest(register);

        AuthLoginRequest login = new AuthLoginRequest();
        login.setAccount("person@qq.com");
        login.setPassword("Password!1");
        login.setCaptchaCode("ABCD");
        service.validateLoginRequest(login);

        login.setCaptchaCode("");
        assertThrows(ApiException.class, () -> service.validateLoginRequest(login));
    }
}
