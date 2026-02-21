package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shuhuai.badmintonflashbackend.config.WechatPayConfig;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.enm.PayChannel;
import shuhuai.badmintonflashbackend.enm.PayOrderStatus;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;
import shuhuai.badmintonflashbackend.entity.PayOrder;
import shuhuai.badmintonflashbackend.entity.Reservation;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.mapper.IPayOrderMapper;
import shuhuai.badmintonflashbackend.mapper.IReservationMapper;
import shuhuai.badmintonflashbackend.model.vo.PayOrderResultVO;
import shuhuai.badmintonflashbackend.model.vo.WechatPayCreateVO;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.IAdminService;
import shuhuai.badmintonflashbackend.service.IPayService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PayServiceImpl implements IPayService {
    private static final long AUTO_MOCK_SUCCESS_DELAY_MS = 1500L;
    private static final long PAY_CREATE_LOCK_LEASE_SECONDS = 5L;

    private final IPayOrderMapper payOrderMapper;
    private final IReservationMapper reservationMapper;
    private final IAdminService adminService;
    private final WechatPayConfig wechatPayConfig;
    private final RedissonClient redisson;

    public PayServiceImpl(IPayOrderMapper payOrderMapper, IReservationMapper reservationMapper,
                          IAdminService adminService, WechatPayConfig wechatPayConfig,
                          RedissonClient redisson) {
        this.payOrderMapper = payOrderMapper;
        this.reservationMapper = reservationMapper;
        this.adminService = adminService;
        this.wechatPayConfig = wechatPayConfig;
        this.redisson = redisson;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatPayCreateVO createWechatPay(Integer userId, Integer reservationId) {
        RLock lock = redisson.getLock(RedisKeys.payCreateLockKey(reservationId));
        boolean locked;
        try {
            locked = lock.tryLock(0, PAY_CREATE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException(ResponseCode.FAILED);
        }
        if (!locked) {
            throw new BaseException(ResponseCode.FAILED);
        }
        try {
            Reservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getId, reservationId)
                    .eq(Reservation::getUserId, userId));
            if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
                throw new BaseException(ResponseCode.PARAM_ERROR);
            }

            PayOrder existingPayingOrder = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                    .eq(PayOrder::getReservationId, reservationId)
                    .eq(PayOrder::getPayChannel, PayChannel.WECHAT)
                    .eq(PayOrder::getStatus, PayOrderStatus.PAYING)
                    .orderByDesc(PayOrder::getId)
                    .last("limit 1"));
            if (existingPayingOrder != null) {
                LocalDateTime now = DateTimes.now();
                if (existingPayingOrder.getExpireTime() != null && existingPayingOrder.getExpireTime().isBefore(now)) {
                    payOrderMapper.update(null, new LambdaUpdateWrapper<PayOrder>()
                            .set(PayOrder::getStatus, PayOrderStatus.FAILED)
                            .eq(PayOrder::getId, existingPayingOrder.getId())
                            .eq(PayOrder::getStatus, PayOrderStatus.PAYING));
                } else {
                    log.info("复用已有未完成支付单 reservationId={}, outTradeNo={}",
                            reservationId, existingPayingOrder.getOutTradeNo());
                    triggerAutoMockSuccessIfEnabled(userId, existingPayingOrder.getOutTradeNo());
                    return buildMockCreateVO(reservationId, existingPayingOrder.getOutTradeNo());
                }
            }

            int timeoutMinute = Integer.parseInt(adminService.getConfigValue(ConfigKey.PAY_TIMEOUT_MINUTE));
            LocalDateTime expireTime = DateTimes.now().plusMinutes(timeoutMinute);
            String outTradeNo = "WX" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            BigDecimal amount;
            try {
                amount = new BigDecimal(adminService.getConfigValue(ConfigKey.PAY_AMOUNT));
            } catch (Exception e) {
                throw new BaseException(ResponseCode.PARAM_ERROR);
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BaseException(ResponseCode.PARAM_ERROR);
            }

            PayOrder payOrder = new PayOrder();
            payOrder.setReservationId(reservationId);
            payOrder.setOutTradeNo(outTradeNo);
            payOrder.setPayChannel(PayChannel.WECHAT);
            payOrder.setAmount(amount);
            payOrder.setStatus(PayOrderStatus.PAYING);
            payOrder.setExpireTime(expireTime);
            payOrderMapper.insert(payOrder);

            // 真实调用示例（当前不执行）：
            // 1) 使用 com.github.wechatpay-apiv3.wechatpay-java 构建 JSAPIServiceExtension
            // 2) 组装 PrepayRequest(appid/mchid/outTradeNo/amount/notifyUrl/payer.openid)
            // 3) 调用 service.prepay(request) 获取 prepay_id
            log.info("模拟微信下单，不调用真实微信接口 appId={}, mchId={}, notifyUrl={}, outTradeNo={}, reservationId={}, amount={}",
                    wechatPayConfig.getAppId(), wechatPayConfig.getMchId(), wechatPayConfig.getNotifyUrl(),
                    outTradeNo, reservationId, amount);

            triggerAutoMockSuccessIfEnabled(userId, outTradeNo);
            return buildMockCreateVO(reservationId, outTradeNo);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void mockWechatPaySuccess(Integer userId, String outTradeNo) {
        PayOrder payOrder = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getOutTradeNo, outTradeNo)
                .eq(PayOrder::getPayChannel, PayChannel.WECHAT));
        if (payOrder == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }

        Reservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getId, payOrder.getReservationId())
                .eq(Reservation::getUserId, userId));
        if (reservation == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (payOrder.getStatus() == PayOrderStatus.SUCCESS) {
            return;
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }

        int updatedPay = payOrderMapper.update(null, new LambdaUpdateWrapper<PayOrder>()
                .set(PayOrder::getStatus, PayOrderStatus.SUCCESS)
                .set(PayOrder::getThirdTradeNo, "mock_wx_trade_" + outTradeNo)
                .eq(PayOrder::getId, payOrder.getId())
                .eq(PayOrder::getStatus, PayOrderStatus.PAYING));
        if (updatedPay <= 0) {
            return;
        }
        int updatedReservation = reservationMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                .set(Reservation::getStatus, ReservationStatus.CONFIRMED)
                .eq(Reservation::getId, reservation.getId())
                .eq(Reservation::getStatus, ReservationStatus.PENDING_PAYMENT));
        if (updatedReservation <= 0) {
            throw new BaseException(ResponseCode.FAILED);
        }

        // 真实回调示例（当前不执行）：
        // 1) 验签微信支付回调请求
        // 2) 解密通知报文
        // 3) 校验 outTradeNo / amount / mchid 后做状态更新
        log.info("模拟微信支付成功回调 outTradeNo={}, reservationId={}", outTradeNo, reservation.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Integer userId, Integer reservationId) {
        Reservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getId, reservationId)
                .eq(Reservation::getUserId, userId));
        if (reservation == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            return;
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        PayOrder payOrder = payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getReservationId, reservationId)
                .eq(PayOrder::getPayChannel, PayChannel.WECHAT)
                .eq(PayOrder::getStatus, PayOrderStatus.SUCCESS)
                .orderByDesc(PayOrder::getId)
                .last("limit 1"));
        if (payOrder == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }

        // 真实微信退款调用示例（当前不执行）：
        // 1) 通过 wechatpay-java 组装退款请求(outTradeNo/outRefundNo/amount)
        // 2) 调用退款接口并校验返回
        log.info("模拟微信退款，不调用真实微信接口 outTradeNo={}, reservationId={}, amount={}",
                payOrder.getOutTradeNo(), reservationId, payOrder.getAmount());

        int updatedPay = payOrderMapper.update(null, new LambdaUpdateWrapper<PayOrder>()
                .set(PayOrder::getStatus, PayOrderStatus.REFUNDED)
                .eq(PayOrder::getId, payOrder.getId())
                .eq(PayOrder::getStatus, PayOrderStatus.SUCCESS));
        if (updatedPay <= 0) {
            return;
        }
        int updatedReservation = reservationMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                .set(Reservation::getStatus, ReservationStatus.CANCELLED)
                .eq(Reservation::getId, reservationId)
                .eq(Reservation::getStatus, ReservationStatus.CONFIRMED));
        if (updatedReservation <= 0) {
            throw new BaseException(ResponseCode.FAILED);
        }

        RSet<Integer> dedup = redisson.getSet(RedisKeys.dedupKey(reservation.getSlotId()));
        dedup.remove(userId);
        RSemaphore sem = redisson.getSemaphore(RedisKeys.semKey(reservation.getSlotId()));
        if (sem.isExists()) {
            sem.release();
        }
    }

    private WechatPayCreateVO buildMockCreateVO(Integer reservationId, String outTradeNo) {
        return new WechatPayCreateVO(
                reservationId,
                outTradeNo,
                "mock_prepay_id_" + outTradeNo,
                UUID.randomUUID().toString().replace("-", ""),
                String.valueOf(System.currentTimeMillis() / 1000),
                "prepay_id=mock_prepay_id_" + outTradeNo,
                "RSA",
                "mock_pay_sign"
        );
    }

    @Transactional(rollbackFor = Exception.class)
    protected void triggerAutoMockSuccessIfEnabled(Integer userId, String outTradeNo) {
        if (!Boolean.TRUE.equals(wechatPayConfig.getAutoMockSuccess())) {
            return;
        }
        CompletableFuture.runAsync(
                () -> {
                    try {
                        mockWechatPaySuccess(userId, outTradeNo);
                    } catch (Exception e) {
                        log.error("自动模拟支付成功失败 outTradeNo={}, error={}", outTradeNo, e.getMessage(), e);
                    }
                },
                CompletableFuture.delayedExecutor(AUTO_MOCK_SUCCESS_DELAY_MS, TimeUnit.MILLISECONDS)
        );
    }

    @Override
    public PayOrderResultVO getPayResult(Integer userId, Integer reservationId) {
        Reservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getId, reservationId)
                .eq(Reservation::getUserId, userId));
        if (reservation == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }

        List<PayOrder> payOrders = payOrderMapper.selectList(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getReservationId, reservationId)
                .orderByDesc(PayOrder::getId));
        if (payOrders.isEmpty()) {
            return new PayOrderResultVO(
                    reservationId, null, null, null, reservation.getStatus(),
                    null, null, null
            );
        }
        PayOrder payOrder = payOrders.getFirst();
        return new PayOrderResultVO(
                reservationId,
                payOrder.getOutTradeNo(),
                payOrder.getPayChannel(),
                payOrder.getStatus(),
                reservation.getStatus(),
                payOrder.getAmount(),
                payOrder.getExpireTime(),
                payOrder.getUpdateTime()
        );
    }
}
