package top.ortus.lightmark.user.dto;

import java.time.LocalDateTime;
import java.time.LocalDate;

public class UserDTO {
    private String id;
    private String phone;
    private String email;
    private String nickname;

    private String avatar;
    private Integer gender;
    private LocalDate birth_date;
    private int points;
    private short level;
    private int status;
    private String register_source;
    private LocalDateTime last_login_time;
    private String last_login_ip;
    private LocalDateTime create_time;
    private LocalDateTime update_time;
    private boolean deleted;

    public UserDTO() {
    }

    public UserDTO(String id, String phone, String email, String nickname, String avatar, Integer gender, LocalDate birth_date,
                   int points, short level, int status, String register_source, LocalDateTime last_login_time, String last_login_ip,
                   LocalDateTime create_time, LocalDateTime update_time, boolean deleted) {
        this.id = id;
        this.phone = phone;
        this.email = email;
        this.nickname = nickname;
        this.avatar = avatar;
        this.gender = gender;
        this.birth_date = birth_date;
        this.points = points;
        this.level = level;
        this.status = status;
        this.register_source = register_source;
        this.last_login_time = last_login_time;
        this.last_login_ip = last_login_ip;
        this.create_time = create_time;
        this.update_time = update_time;
        this.deleted = deleted;
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

    public LocalDate getBirth_date() {
        return birth_date;
    }

    public void setBirth_date(LocalDate birth_date) {
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

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
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

    public LocalDateTime getCreate_time() {
        return create_time;
    }

    public void setCreate_time(LocalDateTime create_time) {
        this.create_time = create_time;
    }

    public LocalDateTime getUpdate_time() {
        return update_time;
    }

    public void setUpdate_time(LocalDateTime update_time) {
        this.update_time = update_time;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
