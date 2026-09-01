package top.ortus.lightmark.user.service;

import org.springframework.stereotype.Service;
import top.ortus.lightmark.user.config.LightmarkAuthProperties;
import top.ortus.lightmark.user.dao.AuthVerificationCode;
import top.ortus.lightmark.user.dao.AuthVerificationCodeRepository;
import top.ortus.lightmark.common.exception.ApiException;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class VerificationCodeService {

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_PHONE = "PHONE";

    private final LightmarkAuthProperties authProperties;
    private final AuthVerificationCodeRepository authVerificationCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public VerificationCodeService(LightmarkAuthProperties authProperties,
                                   AuthVerificationCodeRepository authVerificationCodeRepository) {
        this.authProperties = authProperties;
        this.authVerificationCodeRepository = authVerificationCodeRepository;
    }

    public String generateAndSave(String target, String channel) {
        AuthVerificationCode existing = authVerificationCodeRepository.findActiveByTargetAndChannel(target, channel);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null && existing.getUpdate_time() != null
                && existing.getUpdate_time().plusSeconds(authProperties.getVerification().getResendSeconds()).isAfter(now)) {
            throw new ApiException(429, "verification code requested too frequently");
        }
        int sendCount = existing == null || existing.getSend_count() == null ? 0 : existing.getSend_count();
        if (sendCount >= authProperties.getVerification().getMaxSendPerHour()
                && existing != null
                && existing.getCreate_time() != null
                && existing.getCreate_time().plusHours(1).isAfter(now)) {
            throw new ApiException(429, "verification code send limit reached");
        }
        String code = generateCode();
        AuthVerificationCode payload = new AuthVerificationCode();
        payload.setTarget(target);
        payload.setChannel(channel);
        payload.setCode(code);
        payload.setExpire_time(now.plusMinutes(authProperties.getVerification().getExpireMinutes()));
        payload.setConsumed_time(null);
        payload.setSend_count(sendCount + 1);
        payload.setCreate_time(existing == null ? now : existing.getCreate_time());
        payload.setUpdate_time(now);
        authVerificationCodeRepository.upsert(payload);
        return code;
    }

    public void verifyOrThrow(String target, String channel, String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException(400, "verificationCode is required");
        }
        AuthVerificationCode existing = authVerificationCodeRepository.findActiveByTargetAndChannel(target, channel);
        if (existing == null) {
            throw new ApiException(400, "verification code not found");
        }
        if (existing.getExpire_time() == null || existing.getExpire_time().isBefore(LocalDateTime.now())) {
            throw new ApiException(400, "verification code expired");
        }
        if (!code.trim().equals(existing.getCode())) {
            throw new ApiException(400, "verification code invalid");
        }
        if (existing.getId() != null) {
            authVerificationCodeRepository.consume(existing.getId());
        }
    }

    private String generateCode() {
        int length = Math.max(4, authProperties.getVerification().getCodeLength());
        int bound = (int) Math.pow(10, length);
        int min = (int) Math.pow(10, length - 1);
        return String.valueOf(min + secureRandom.nextInt(bound - min));
    }
}
