package top.ortus.lightmark.user.dao;

import java.time.LocalDateTime;

public class AuthVerificationCode {
    private String id;
    private String target;
    private String channel;
    private String code;
    private LocalDateTime expire_time;
    private LocalDateTime consumed_time;
    private Integer send_count;
    private LocalDateTime create_time;
    private LocalDateTime update_time;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public LocalDateTime getExpire_time() {
        return expire_time;
    }

    public void setExpire_time(LocalDateTime expire_time) {
        this.expire_time = expire_time;
    }

    public LocalDateTime getConsumed_time() {
        return consumed_time;
    }

    public void setConsumed_time(LocalDateTime consumed_time) {
        this.consumed_time = consumed_time;
    }

    public Integer getSend_count() {
        return send_count;
    }

    public void setSend_count(Integer send_count) {
        this.send_count = send_count;
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
}
