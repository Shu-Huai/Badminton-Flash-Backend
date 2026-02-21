package shuhuai.badmintonflashbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WechatPayCreateVO {
    private Integer reservationId;
    private String outTradeNo;
    private String prepayId;
    private String nonceStr;
    private String timeStamp;
    private String packageValue;
    private String signType;
    private String paySign;
}
