package top.ortus.lightmark.user.dto.user;

import top.ortus.lightmark.user.dto.UserDTO;
import top.ortus.lightmark.common.security.UserIdentity;

import java.util.List;

public class UserCurrentDTO {
    private String id;
    private String phone;
    private String email;
    private String nickname;
    private String avatar;
    private Integer gender;
    private java.time.LocalDate birth_date;
    private int points;
    private short level;
    private String identity;
    private List<String> roles;
    private List<String> permissions;

    public UserCurrentDTO() {
    }

    public UserCurrentDTO(UserDTO user, UserIdentity identity, List<String> permissions) {
        if (user != null) {
            this.id = user.getId();
            this.phone = user.getPhone();
            this.email = user.getEmail();
            this.nickname = user.getNickname();
            this.avatar = user.getAvatar();
            this.gender = user.getGender();
            this.birth_date = user.getBirth_date();
            this.points = user.getPoints();
            this.level = user.getLevel();
        }
        this.identity = identity == null ? UserIdentity.USER.name() : identity.name();
        this.roles = List.of(this.identity);
        this.permissions = permissions;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public java.time.LocalDate getBirth_date() {
        return birth_date;
    }

    public void setBirth_date(java.time.LocalDate birth_date) {
        this.birth_date = birth_date;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public short getLevel() {
        return level;
    }

    public void setLevel(short level) {
        this.level = level;
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

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
