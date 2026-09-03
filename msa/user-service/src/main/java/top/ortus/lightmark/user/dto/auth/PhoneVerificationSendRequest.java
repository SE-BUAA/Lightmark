package top.ortus.lightmark.user.dto.auth;

import jakarta.validation.constraints.NotBlank;

public class PhoneVerificationSendRequest {

    @NotBlank
    private String countryCode;

    @NotBlank
    private String phone;

    @NotBlank
    private String captchaCode;

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCaptchaCode() {
        return captchaCode;
    }

    public void setCaptchaCode(String captchaCode) {
        this.captchaCode = captchaCode;
    }
}
