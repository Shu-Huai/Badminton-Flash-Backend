package shuhuai.badmintonflashbackend.service;

import shuhuai.badmintonflashbackend.model.vo.PayOrderResultVO;
import shuhuai.badmintonflashbackend.model.vo.WechatPayCreateVO;

public interface IPayService {
    WechatPayCreateVO createWechatPay(Integer userId, Integer reservationId);

    void mockWechatPaySuccess(Integer userId, String outTradeNo);

    void refund(Integer userId, Integer reservationId);

    PayOrderResultVO getPayResult(Integer userId, Integer reservationId);
}
