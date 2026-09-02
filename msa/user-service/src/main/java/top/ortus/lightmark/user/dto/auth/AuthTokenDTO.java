package top.ortus.lightmark.user.dto.auth;

import java.util.List;

public class AuthTokenDTO {
    private String token;
    private String userId;
    private String nickname;
    private String avatar;
    private String identity;
    private List<String> roles;

    public AuthTokenDTO() {
    }

    public AuthTokenDTO(String token, String userId, String nickname) {
        this.token = token;
        this.userId = userId;
        this.nickname = nickname;
    }

    public AuthTokenDTO(String token, String userId, String nickname, String avatar, String identity, List<String> roles) {
        this.token = token;
        this.userId = userId;
        this.nickname = nickname;
        this.avatar = avatar;
        this.identity = identity;
        this.roles = roles;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}