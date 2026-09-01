package top.ortus.lightmark.user.dto.auth;

import jakarta.validation.constraints.NotBlank;

public class EmailVerificationSendRequest {

    @NotBlank
    private String email;

    @NotBlank
    private String captchaCode;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }
}
