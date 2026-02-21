package shuhuai.badmintonflashbackend.mq;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RSemaphore;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.entity.Reservation;
import shuhuai.badmintonflashbackend.mapper.IReservationMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Slf4j
@Component
public class ReservePublishCallbackHandler {
    private final RedissonClient redisson;
    private final IReservationMapper reservationMapper;

    public ReservePublishCallbackHandler(RedissonClient redisson, IReservationMapper reservationMapper) {
        this.redisson = redisson;
        this.reservationMapper = reservationMapper;
    }

    public void onConfirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack || correlationData == null) {
            return;
        }
        compensateByTraceId(correlationData.getId(), "confirm-nack:" + cause);
    }

    public void onReturned(ReturnedMessage returned) {
        if (returned == null || returned.getMessage() == null) {
            return;
        }
        Message message = returned.getMessage();
        String traceId = message.getMessageProperties().getMessageId();
        if (traceId == null || traceId.isEmpty()) {
            Object header = message.getMessageProperties().getHeaders().get("traceId");
            traceId = header == null ? null : String.valueOf(header);
        }
        if (traceId == null || traceId.isEmpty()) {
            return;
        }
        compensateByTraceId(traceId, "returned");
    }

    public void compensateByTraceId(String traceId, String reason) {
        if (traceId == null || traceId.isEmpty()) {
            return;
        }
        RBucket<String> pendingBucket = redisson.getBucket(RedisKeys.reservePendingKey(traceId));
        String pending = pendingBucket.getAndDelete();
        if (pending == null || pending.isEmpty()) {
            return;
        }
        String[] parts = pending.split(":", 2);
        if (parts.length != 2) {
            return;
        }
        Integer userId;
        Integer slotId;
        try {
            userId = Integer.parseInt(parts[0]);
            slotId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return;
        }
        Reservation existed = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getSlotId, slotId));
        if (existed != null) {
            log.warn("预约发布补偿跳过：slot 已落库 traceId={}, reason={}, userId={}, slotId={}",
                    traceId, reason, userId, slotId);
            return;
        }
        RSet<Integer> dedup = redisson.getSet(RedisKeys.dedupKey(slotId));
        dedup.remove(userId);
        RSemaphore semaphore = redisson.getSemaphore(RedisKeys.semKey(slotId));
        semaphore.release();
        log.warn("预约发布补偿完成 traceId={}, reason={}, userId={}, slotId={}", traceId, reason, userId, slotId);
    }

    public void clearPending(String traceId) {
        if (traceId == null || traceId.isEmpty()) {
            return;
        }
        redisson.getBucket(RedisKeys.reservePendingKey(traceId)).delete();
    }
}
