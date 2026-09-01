package top.ortus.lightmark.user.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserPasswordUpdateRequest {
    @NotBlank
    private String oldPassword;
    @NotBlank
    @Size(min = 8, max = 20)
    private String newPassword;

    public UserPasswordUpdateRequest() {
    }

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
