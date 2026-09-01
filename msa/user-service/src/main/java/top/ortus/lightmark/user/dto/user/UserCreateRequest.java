package top.ortus.lightmark.user.dto.user;

import java.time.LocalDateTime;
import java.time.LocalDate;

public class UserCreateRequest {
    private String phone;
    private String email;
    private String password;
    private String nickname;
    private String avatar;
    private Integer gender;
    private LocalDate birth_date;
    private Integer points;
    private Short level;
    private Integer status;
    private String register_source;
    private LocalDateTime last_login_time;
    private String last_login_ip;

    public UserCreateRequest() {
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        if (phone == null) {
            this.phone = null;
            return;
        }
        String v = phone.trim();
        this.phone = v.isEmpty() ? null : v;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if (email == null) {
            this.email = null;
            return;
        }
        String v = email.trim();
        this.email = v.isEmpty() ? null : v;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public LocalDate getBirth_date() {
        return birth_date;
    }

    public void setBirth_date(LocalDate birth_date) {
        this.birth_date = birth_date;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public Short getLevel() {
        return level;
    }

    public void setLevel(Short level) {
        this.level = level;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRegister_source() {
        return register_source;
    }

    public void setRegister_source(String register_source) {
        this.register_source = register_source;
    }

    public LocalDateTime getLast_login_time() {
        return last_login_time;
    }

    public void setLast_login_time(LocalDateTime last_login_time) {
        this.last_login_time = last_login_time;
    }

    public String getLast_login_ip() {
        return last_login_ip;
    }

    public void setLast_login_ip(String last_login_ip) {
        this.last_login_ip = last_login_ip;
    }
}
