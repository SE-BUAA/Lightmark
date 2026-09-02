package top.ortus.lightmark.user.dto.auth;

import jakarta.validation.constraints.NotBlank;

public class AuthLoginRequest {
    @NotBlank
    private String account;
    @NotBlank
    private String password;
    @NotBlank
    private String captchaCode;
    private Boolean privacyAccepted;

    public AuthLoginRequest() {
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }

    public Boolean getPrivacyAccepted() {
        return privacyAccepted;
    }

    public void setPrivacyAccepted(Boolean privacyAccepted) {
        this.privacyAccepted = privacyAccepted;
    }
}
