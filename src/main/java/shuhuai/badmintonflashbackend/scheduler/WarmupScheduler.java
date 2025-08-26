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
            List<TimeSlot> slots = timeSlotMapper.selectList(Wrappers.<TimeSlot>lambdaQuery()
                    .eq(TimeSlot::getSessionId, flashSession.getId()));
            if (slots.isEmpty()) {
                continue;
            }

            for (TimeSlot slot : slots) {
                Integer slotId = slot.getId();
                // 幂等：warmup:done:{slotId}
                RBucket<String> warmFlag = redisson.getBucket(RedisKeys.warmupDoneKey(slotId));
                boolean first = warmFlag.setIfAbsent("1", Duration.ofSeconds(ttlSec));
                if (!first) {
                    // 该 slot 已预热过，跳过
                    continue;
                }
                // semaphore: sem:{slotId}（许可=1；若你有容量字段可替换）
                RSemaphore sem = redisson.getSemaphore(RedisKeys.semKey(slotId));
                sem.trySetPermits(1);
                sem.expire(Duration.ofSeconds(ttlSec));

                // 3) 清空并设置 TTL（为确保存在后能设置 TTL，做一次 add/remove）
                RSet<Long> dedup = redisson.getSet(RedisKeys.dedupKey(slotId));
                dedup.delete();                          // 清空
                dedup.add(-1L);                          // 确保 key 存在
                dedup.expire(Duration.ofSeconds(ttlSec));
            }


            // 闸门
            RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(flashSession.getId()));
            gate.set("0", Duration.ofSeconds(ttlSec));
            // 闸门时间
            long startEpoch = ZonedDateTime
                    .of(today, flashSession.getFlashTime(), DateTimes.SHANGHAI)
                    .toEpochSecond();
            RBucket<Long> gateTime = redisson.getBucket(RedisKeys.gateTimeKey(flashSession.getId()));
            gateTime.set(startEpoch, Duration.ofSeconds(ttlSec));
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