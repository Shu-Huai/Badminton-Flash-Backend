package shuhuai.badmintonflashbackend.controller;

import org.springframework.web.bind.annotation.*;
import shuhuai.badmintonflashbackend.auth.RequireRole;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.model.vo.PayOrderResultVO;
import shuhuai.badmintonflashbackend.model.vo.WechatPayCreateVO;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.IPayService;
import shuhuai.badmintonflashbackend.utils.TokenValidator;

@RestController
@RequestMapping("/pay")
@RequireRole(UserRole.USER)
public class PayController {
    private final IPayService payService;

    public PayController(IPayService payService) {
        this.payService = payService;
    }

    @PostMapping("/wechat/{reservationId}")
    public Response<WechatPayCreateVO> createWechatPay(@PathVariable Integer reservationId) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        return new Response<>(payService.createWechatPay(userId, reservationId));
    }

    @PostMapping("/wechat/mock-success/{outTradeNo}")
    @RequireRole(value = UserRole.ADMIN, dbCheck = true)
    public Response<Void> mockWechatPaySuccess(@PathVariable String outTradeNo) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        payService.mockWechatPaySuccess(userId, outTradeNo);
        return new Response<>();
    }

    @PostMapping("/refund/{reservationId}")
    public Response<Void> refund(@PathVariable Integer reservationId) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        payService.refund(userId, reservationId);
        return new Response<>();
    }

    @GetMapping("/reservation/{reservationId}")
    public Response<PayOrderResultVO> getPayResult(@PathVariable Integer reservationId) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        return new Response<>(payService.getPayResult(userId, reservationId));
    }
}
