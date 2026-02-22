package shuhuai.badmintonflashbackend.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.constant.MqNames;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;
import shuhuai.badmintonflashbackend.entity.Reservation;
import shuhuai.badmintonflashbackend.mapper.IReservationMapper;
import shuhuai.badmintonflashbackend.mq.ReservePublishCallbackHandler;
import shuhuai.badmintonflashbackend.mq.message.ReserveMessage;

@Slf4j
@Component
public class ReserveConsumer {
    private final IReservationMapper reservationMapper;
    private final ReservePublishCallbackHandler publishCallbackHandler;

    @Autowired
    public ReserveConsumer(IReservationMapper reservationMapper, ReservePublishCallbackHandler publishCallbackHandler) {
        this.reservationMapper = reservationMapper;
        this.publishCallbackHandler = publishCallbackHandler;
    }

    @RabbitListener(queues = MqNames.RESERVE_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void handle(ReserveMessage message) {
        log.info("收到预约消息: {}", message);

        try {
            Reservation reservation = new Reservation();
            reservation.setUserId(message.getUserId());
            reservation.setSlotId(message.getSlotId());
            reservation.setTraceId(message.getTraceId());
            reservation.setStatus(ReservationStatus.PENDING_PAYMENT);
            reservationMapper.insert(reservation);
            publishCallbackHandler.clearPending(message.getTraceId());
            log.info("预约成功落库 userId={}, slotId={}", message.getUserId(), message.getSlotId());
        } catch (DuplicateKeyException e) {
            Reservation existed = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getSlotId, message.getSlotId())
                    .in(Reservation::getStatus, ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED));
            boolean hadPending = publishCallbackHandler.clearPending(message.getTraceId());
            if (existed != null && message.getUserId() != null && message.getUserId().equals(existed.getUserId())) {
                // 同一用户 + 同一 slot：可视为消息重复投递（幂等）
                log.warn("幂等命中，重复消息已忽略 userId={}, slotId={}", message.getUserId(), message.getSlotId());
                return;
            }
            // 不同用户冲突到同一 slot：这是业务冲突，不是幂等成功
            if (hadPending) {
                publishCallbackHandler.releaseReserveResource(
                        message.getUserId(),
                        message.getSlotId(),
                        message.getTraceId(),
                        "consume-duplicate-conflict");
            }
            log.warn("slot 已被占用，消息按业务冲突处理 userId={}, slotId={}", message.getUserId(), message.getSlotId());
        } catch (Exception e) {
            log.error("预约入库失败: {}", e.getMessage(), e);
            // 可手动重试或丢入死信队列
            throw new RuntimeException("预约入库失败", e);
        }
    }
}
