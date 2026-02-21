package shuhuai.badmintonflashbackend.service.impl;

import org.redisson.api.RBucket;
import org.redisson.api.RSemaphore;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shuhuai.badmintonflashbackend.constant.MqNames;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.mq.ReservePublishCallbackHandler;
import shuhuai.badmintonflashbackend.mq.message.ReserveMessage;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.IRateLimitService;
import shuhuai.badmintonflashbackend.service.IReserveService;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ReserveServiceImpl implements IReserveService {
    private static final long PUBLISH_CONFIRM_TIMEOUT_MS = 3000L;
    private static final long PENDING_TTL_SECONDS = 300L;

    private final RedissonClient redisson;
    private final IRateLimitService rateLimitService;
    private final RabbitTemplate rabbitTemplate;
    private final ReservePublishCallbackHandler publishCallbackHandler;

    @Autowired
    public ReserveServiceImpl(RedissonClient redisson, IRateLimitService rateLimitService,
                              RabbitTemplate rabbitTemplate, ReservePublishCallbackHandler publishCallbackHandler) {
        this.redisson = redisson;
        this.rateLimitService = rateLimitService;
        this.rabbitTemplate = rabbitTemplate;
        this.publishCallbackHandler = publishCallbackHandler;
    }

    @Override
    public void reserve(Integer userId, Integer slotId, Integer sessionId) {
        // 快路径：Redis 校验 slot 与 session 的归属关系，避免串场抢票
        RBucket<Integer> slotSession = redisson.getBucket(RedisKeys.slotSessionKey(slotId));
        Integer cachedSessionId = slotSession.get();
        if (cachedSessionId == null || !cachedSessionId.equals(sessionId)) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }

        // 用户维度限流：每分钟最多 5 次尝试
        boolean allowed = rateLimitService.tryConsume(userId.toString(), 5, Duration.ofMinutes(1));
        if (!allowed) {
            throw new BaseException(ResponseCode.TOO_MANY_REQUESTS);
        }

        // 检查开闸
        RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(sessionId));
        if (!"1".equals(gate.get())) {
            throw new BaseException(ResponseCode.UNGATED);
        }

        // 检查去重
        RSet<Integer> dedup = redisson.getSet(RedisKeys.dedupKey(slotId));
        boolean firstTry = dedup.add(userId);
        if (!firstTry) {
            throw new BaseException(ResponseCode.DUP_REQ);
        }

        // 抢库存
        RSemaphore sem = redisson.getSemaphore(RedisKeys.semKey(slotId));
        boolean got = sem.tryAcquire();
        if (!got) {
            dedup.remove(userId); // 回滚去重
            throw new BaseException(ResponseCode.OUT_OF_STOCK);
        }

        String traceId = UUID.randomUUID().toString();
        redisson.getBucket(RedisKeys.reservePendingKey(traceId))
                .set(userId + ":" + slotId, Duration.ofSeconds(PENDING_TTL_SECONDS));

        ReserveMessage message = new ReserveMessage(userId, slotId, sessionId, traceId);
        CorrelationData correlationData = new CorrelationData(traceId);
        try {
            rabbitTemplate.convertAndSend(
                    MqNames.RESERVE_EXCHANGE,
                    MqNames.RESERVE_ROUTING_KEY,
                    message,
                    rawMessage -> {
                        rawMessage.getMessageProperties().setMessageId(traceId);
                        rawMessage.getMessageProperties().setHeader("traceId", traceId);
                        return rawMessage;
                    },
                    correlationData
            );
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(PUBLISH_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck()) {
                publishCallbackHandler.compensateByTraceId(traceId,
                        "sync-confirm-failed:" + (confirm == null ? "null" : confirm.getReason()));
                throw new BaseException(ResponseCode.FAILED);
            }
        } catch (Exception e) {
            publishCallbackHandler.compensateByTraceId(traceId, "sync-send-exception");
            throw new BaseException(ResponseCode.FAILED);
        }
    }
}
