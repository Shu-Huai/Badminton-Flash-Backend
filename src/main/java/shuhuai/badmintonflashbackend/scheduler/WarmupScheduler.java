package shuhuai.badmintonflashbackend.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.redisson.api.RBucket;
import org.redisson.api.RSemaphore;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.entity.TimeSlot;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.mapper.ITimeSlotMapper;
import shuhuai.badmintonflashbackend.service.AdminService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.*;
import java.util.List;

@Component
public class WarmupScheduler {
    private final IFlashSessionMapper sessionMapper;
    private final AdminService adminService;
    private final ITimeSlotMapper timeSlotMapper;
    private final RedissonClient redisson;

    @Autowired
    public WarmupScheduler(IFlashSessionMapper sessionMapper, AdminService adminService,
                           ITimeSlotMapper timeSlotMapper, RedissonClient redisson) {
        this.sessionMapper = sessionMapper;
        this.adminService = adminService;
        this.timeSlotMapper = timeSlotMapper;
        this.redisson = redisson;
    }


    @Scheduled(cron = "0 * * * * ?")
    public void warmupNearFutureSlots() {
        // 读取提前分钟数（WARMUP_MINUTE）
        String raw = adminService.getConfigValue(ConfigKey.WARMUP_MINUTE);
        int warmupMinutes = Integer.parseInt(raw);

        // 计算 today / now_min / target_start
        LocalDate today = LocalDate.now(DateTimes.SHANGHAI);
        LocalTime nowMin = DateTimes.nowMinute();                 // 去掉秒和纳秒
        LocalTime targetStart = nowMin.plusMinutes(warmupMinutes);
        long ttlSec = DateTimes.ttlToEndOfDaySeconds(today);

        List<FlashSession> flashSessions = sessionMapper.selectList(Wrappers.<FlashSession>lambdaQuery()
                .eq(FlashSession::getFlashTime, targetStart));

        if (flashSessions.isEmpty()) {
            return;
        }

        for (FlashSession flashSession : flashSessions) {
            adminService.warmupSession(flashSession);
        }
    }

    /**
     * 开闸
     */
    @Scheduled(cron = "0 * * * * ?")
    public void openGate() {
        LocalTime nowMin = DateTimes.nowMinute();
        List<FlashSession> flashSessions = sessionMapper.selectList(Wrappers.<FlashSession>lambdaQuery()
                .eq(FlashSession::getFlashTime, nowMin));
        if (flashSessions.isEmpty()) {
            return;
        }
        for (FlashSession flashSession : flashSessions) {
            // 闸门
            RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(flashSession.getId()));
            gate.set("1");
        }
    }
}