package top.ortus.lightmark.user.dto.module;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class AdminLogDTO {
    private String id;
    private String admin_id;
    private String operation;
    private String params;
    private String result;
    private String ip;
    private LocalDateTime create_time;

    public AdminLogDTO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAdmin_id() {
        return admin_id;
    }

    public void setAdmin_id(String admin_id) {
        this.admin_id = admin_id;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public LocalDateTime getCreate_time() {
        return create_time;
    }

    public void setCreate_time(LocalDateTime create_time) {
        this.create_time = create_time;
    }

    @JsonProperty("adminId")
    public String getAdminId() {
        return admin_id;
    }

    @JsonProperty("target")
    public String getTarget() {
        return params;
    }

    @JsonProperty("createTime")
    public LocalDateTime getCreateTime() {
        return create_time;
    }
}
