package top.ortus.lightmark.user.dto.user;

import jakarta.validation.constraints.Size;

public class UserAvatarUpdateRequest {
    @Size(max = 500)
    private String avatarUrl;

    public UserAvatarUpdateRequest() {
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
