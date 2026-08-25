package top.ortus.lightmark.backend.dto.module;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class OrderDTO {
    private String id;
    private String order_no;
    private String user_id;
    private String order_type;
    private BigDecimal total_amount;
    private int points_deduct;
    private BigDecimal pay_amount;
    private String payment_method;
    private String source;
    private int status;
    private LocalDateTime pay_deadline;
    private LocalDateTime pay_time;
    private String cancel_reason;
    private LocalDateTime create_time;
    private LocalDateTime update_time;

    public OrderDTO() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrder_no() {
        return order_no;
    }

    public void setOrder_no(String order_no) {
        this.order_no = order_no;
    }

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public String getOrder_type() {
        return order_type;
    }

    public void setOrder_type(String order_type) {
        this.order_type = order_type;
    }

    public BigDecimal getTotal_amount() {
        return total_amount;
    }

    public void setTotal_amount(BigDecimal total_amount) {
        this.total_amount = total_amount;
    }

    public int getPoints_deduct() {
        return points_deduct;
    }

    public void setPoints_deduct(int points_deduct) {
        this.points_deduct = points_deduct;
    }

    public BigDecimal getPay_amount() {
        return pay_amount;
    }

    public void setPay_amount(BigDecimal pay_amount) {
        this.pay_amount = pay_amount;
    }

    public String getPayment_method() {
        return payment_method;
    }

    public void setPayment_method(String payment_method) {
        this.payment_method = payment_method;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public LocalDateTime getPay_deadline() {
        return pay_deadline;
    }

    public void setPay_deadline(LocalDateTime pay_deadline) {
        this.pay_deadline = pay_deadline;
    }

    public LocalDateTime getPay_time() {
        return pay_time;
    }

    public void setPay_time(LocalDateTime pay_time) {
        this.pay_time = pay_time;
    }

    public String getCancel_reason() {
        return cancel_reason;
    }

    public void setCancel_reason(String cancel_reason) {
        this.cancel_reason = cancel_reason;
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

    public Long getCreateEpochMs() {
        return create_time == null ? null : create_time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public Long getUpdateEpochMs() {
        return update_time == null ? null : update_time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}