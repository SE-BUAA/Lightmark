package top.ortus.lightmark.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.ortus.lightmark.common.exception.ApiException;
import top.ortus.lightmark.user.config.LightmarkAuthProperties;
import top.ortus.lightmark.user.dao.AuthVerificationCode;
import top.ortus.lightmark.user.dao.AuthVerificationCodeRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VerificationCodeServiceTest {
    private AuthVerificationCodeRepository repository;
    private VerificationCodeService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuthVerificationCodeRepository.class);
        service = new VerificationCodeService(new LightmarkAuthProperties(), repository);
    }

    @Test
    void generatesAndPersistsCodeWithConfiguredLength() {
        when(repository.findActiveByTargetAndChannel("a@qq.com", "EMAIL")).thenReturn(null);
        String code = service.generateAndSave("a@qq.com", "EMAIL");
        assertEquals(6, code.length());
        verify(repository).upsert(any(AuthVerificationCode.class));
    }

    @Test
    void rejectsTooFrequentRequestAndInvalidOrExpiredCode() {
        AuthVerificationCode existing = new AuthVerificationCode();
        existing.setUpdate_time(LocalDateTime.now());
        existing.setSend_count(1);
        when(repository.findActiveByTargetAndChannel("a@qq.com", "EMAIL")).thenReturn(existing);
        ApiException frequent = assertThrows(ApiException.class,
                () -> service.generateAndSave("a@qq.com", "EMAIL"));
        assertEquals(429, frequent.getCode());

        existing.setUpdate_time(LocalDateTime.now().minusMinutes(5));
        existing.setExpire_time(LocalDateTime.now().plusMinutes(5));
        existing.setCode("123456");
        assertThrows(ApiException.class, () -> service.verifyOrThrow("a@qq.com", "EMAIL", "000000"));
        existing.setExpire_time(LocalDateTime.now().minusSeconds(1));
        assertThrows(ApiException.class, () -> service.verifyOrThrow("a@qq.com", "EMAIL", "123456"));
    }

    @Test
    void consumesMatchingCodeExactlyOnce() {
        AuthVerificationCode existing = new AuthVerificationCode();
        existing.setId("7");
        existing.setCode("123456");
        existing.setExpire_time(LocalDateTime.now().plusMinutes(5));
        when(repository.findActiveByTargetAndChannel("a@qq.com", "EMAIL")).thenReturn(existing);
        service.verifyOrThrow("a@qq.com", "EMAIL", "123456");
        verify(repository).consume("7");
        assertNotNull(existing.getExpire_time());
    }
}
