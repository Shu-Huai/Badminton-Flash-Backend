package shuhuai.badmintonflashbackend.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Component
public class WechatPayConfig {
    @Value("${wechat.pay.appId}")
    private String appId;

    @Value("${wechat.pay.mchId}")
    private String mchId;

    @Value("${wechat.pay.notifyUrl}")
    private String notifyUrl;

    @Value("${wechat.pay.apiV3Key}")
    private String apiV3Key;

    @Value("${wechat.pay.privateKeyPath}")
    private String privateKeyPath;

    @Value("${wechat.pay.merchantSerialNumber}")
    private String merchantSerialNumber;

    @Value("${wechat.pay.autoMockSuccess:false}")
    private Boolean autoMockSuccess;
}
