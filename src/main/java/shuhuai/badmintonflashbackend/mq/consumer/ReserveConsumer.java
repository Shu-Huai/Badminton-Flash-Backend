package shuhuai.badmintonflashbackend.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.constant.MqNames;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;
import shuhuai.badmintonflashbackend.entity.Reservation;
import shuhuai.badmintonflashbackend.mapper.IReservationMapper;
import shuhuai.badmintonflashbackend.mq.message.ReserveMessage;

@Slf4j
@Component
public class ReserveConsumer {
    private final IReservationMapper reservationMapper;

    @Autowired
    public ReserveConsumer(IReservationMapper reservationMapper) {
        this.reservationMapper = reservationMapper;
    }

    @RabbitListener(queues = MqNames.RESERVE_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void handle(ReserveMessage message) {
        log.info("收到预约消息: {}", message);

        try {
            // 幂等检查（如 traceId）可选
            Reservation exist = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getSlotId, message.getSlotId()));
            if (exist != null) {
                log.warn("重复消费或已有记录 userId={}, slotId={}", message.getUserId(), message.getSlotId());
                return;
            }
            Reservation reservation = new Reservation();
            reservation.setUserId(message.getUserId());
            reservation.setSlotId(message.getSlotId());
            reservation.setStatus(ReservationStatus.PENDING_PAYMENT);
            reservationMapper.insert(reservation);
            log.info("预约成功落库 userId={}, slotId={}", message.getUserId(), message.getSlotId());
        } catch (Exception e) {
            log.error("预约入库失败: {}", e.getMessage(), e);
            // 可手动重试或丢入死信队列
            throw new RuntimeException("预约入库失败", e);
        }
    }
}
