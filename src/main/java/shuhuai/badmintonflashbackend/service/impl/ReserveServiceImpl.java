package shuhuai.badmintonflashbackend.service.impl;

import org.redisson.api.RBucket;
import org.redisson.api.RSemaphore;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shuhuai.badmintonflashbackend.constant.MqNames;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.mapper.IReservationMapper;
import shuhuai.badmintonflashbackend.mq.message.ReserveMessage;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.IRateLimitService;
import shuhuai.badmintonflashbackend.service.IReserveService;

import java.time.Duration;

@Service
public class ReserveServiceImpl implements IReserveService {
    private final RedissonClient redisson;
    private final IRateLimitService rateLimitService;
    private final RabbitTemplate rabbitTemplate;
    private final IReservationMapper reservationMapper;

    @Autowired
    public ReserveServiceImpl(RedissonClient redisson, IRateLimitService rateLimitService, RabbitTemplate rabbitTemplate,
                              IReservationMapper reservationMapper) {
        this.redisson = redisson;
        this.rateLimitService = rateLimitService;
        this.rabbitTemplate = rabbitTemplate;
        this.reservationMapper = reservationMapper;
    }

    @Override
    public void reserve(Integer userId, Integer slotId, Integer sessionId) {
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
            System.out.println("快点快点看");
            throw new BaseException(ResponseCode.OUT_OF_STOCK);
        }

        // MQ
        ReserveMessage message = new ReserveMessage(userId, slotId, sessionId);
        rabbitTemplate.convertAndSend(
                MqNames.RESERVE_EXCHANGE,
                MqNames.RESERVE_ROUTING_KEY,
                message
        );
    }
}
