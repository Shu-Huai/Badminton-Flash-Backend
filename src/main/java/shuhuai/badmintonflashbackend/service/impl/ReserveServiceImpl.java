package shuhuai.badmintonflashbackend.service.impl;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.http.ResponseEntity;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.service.IReserveService;
import shuhuai.badmintonflashbackend.service.ITimeSlotService;

public class ReserveServiceImpl implements IReserveService {
    private final RedissonClient redisson;
    private final ITimeSlotService timeSlotService;
    public ReserveServiceImpl(RedissonClient redisson, ITimeSlotService timeSlotService) {
        this.redisson = redisson;
        this.timeSlotService = timeSlotService;
    }
    @Override
    public void reserve(Integer userId, Integer slotId, Integer sessionId) {
        // 检查开闸
        RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(sessionId));
        if (!"1".equals(gate.get())) {
            throw new RuntimeException("未开闸");
        }


    }
}
