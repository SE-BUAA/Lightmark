package top.ortus.lightmark.user.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理后台登录请求。
 * 后台登录不校验图形验证码与隐私协议，但后端仍会校验密码与 ADMIN 角色。
 */
public class AdminLoginRequest {

    @NotBlank
    private String account;
    @NotBlank
    private String password;

    public AdminLoginRequest() {
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
}
