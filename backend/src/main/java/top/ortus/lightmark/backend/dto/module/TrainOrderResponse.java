package top.ortus.lightmark.backend.dto.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainOrderResponse {
    private String orderNo;
    private Integer status;
    private BigDecimal payAmount;
    private LocalDateTime createTime;
    private LocalDateTime expireTime;
    private Long expireEpochMs;
    private String pickupCode;
}
