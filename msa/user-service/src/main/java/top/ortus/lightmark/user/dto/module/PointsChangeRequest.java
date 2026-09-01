package top.ortus.lightmark.user.dto.module;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 服务间积分变更请求。orderId 只保存为引用编号，不建立跨服务外键。
 */
public class PointsChangeRequest {
    @NotNull
    private Integer amount;
    @NotNull
    private Integer type;
    @NotBlank
    private String source;
    private String orderId;

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
}
