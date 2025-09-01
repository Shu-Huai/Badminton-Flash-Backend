package shuhuai.badmintonflashbackend.service.impl;

import org.redisson.api.RBucket;
import org.redisson.api.RSemaphore;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.service.IRateLimitService;
import shuhuai.badmintonflashbackend.service.IReserveService;
import shuhuai.badmintonflashbackend.service.ITimeSlotService;

import java.time.Duration;

@Service
public class ReserveServiceImpl implements IReserveService {
    private final RedissonClient redisson;
    private final ITimeSlotService timeSlotService;
    private final IRateLimitService rateLimitService;

    @Autowired
    public ReserveServiceImpl(RedissonClient redisson, ITimeSlotService timeSlotService, IRateLimitService rateLimitService) {
        this.redisson = redisson;
        this.timeSlotService = timeSlotService;
        this.rateLimitService = rateLimitService;
    }
    @Override
    public void reserve(Integer userId, Integer slotId, Integer sessionId) {
        // 用户维度限流：每分钟最多 5 次尝试
        boolean allowed = rateLimitService.tryConsume(userId.toString(), 5, Duration.ofMinutes(1));
        if (!allowed) {
            throw new RuntimeException("访问频率过高，请稍后再试");
        }

        // 检查开闸
        RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(sessionId));
        if (!"1".equals(gate.get())) {
            throw new RuntimeException("未开闸");
        }

        // 检查去重
        RSet<Integer> dedup = redisson.getSet(RedisKeys.dedupKey(slotId));
        boolean firstTry = dedup.add(userId);
        if (!firstTry) {
            throw new RuntimeException("你已经预约过该时段了");
        }

        // 抢库存
        RSemaphore sem = redisson.getSemaphore(RedisKeys.semKey(slotId));
        boolean got = sem.tryAcquire();
        if (!got) {
            dedup.remove(userId); // 回滚去重
            throw new RuntimeException("场地已抢光");
        }

        // MQ

    }
}
