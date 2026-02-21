package shuhuai.badmintonflashbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import shuhuai.badmintonflashbackend.enm.PayChannel;
import shuhuai.badmintonflashbackend.enm.PayOrderStatus;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayOrderResultVO {
    private Integer reservationId;
    private String outTradeNo;
    private PayChannel payChannel;
    private PayOrderStatus payStatus;
    private ReservationStatus reservationStatus;
    private BigDecimal amount;
    private LocalDateTime expireTime;
    private LocalDateTime updateTime;
}
