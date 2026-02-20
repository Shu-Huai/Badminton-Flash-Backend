package shuhuai.badmintonflashbackend.scheduler;

import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.service.AdminService;
import shuhuai.badmintonflashbackend.service.ITimeSlotService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
 * 每日定时任务：根据配置时间生成当日时间槽
 */
@Component
public class DailySlotGenerateScheduler {
    @Resource
    private AdminService adminService;
    @Resource
    private ITimeSlotService timeSlotService;
    @Resource
    private RedissonClient redisson;

    /**
     * 每分钟执行一次，命中配置时间时生成
     */
    @Scheduled(cron = "0 * * * * ?", zone = "${app.timezone}")
    public void maybeGenerateTodaySlots() {
        // 获取配置时间
        LocalTime target = LocalTime.parse(adminService.getConfigValue(ConfigKey.GENERATE_TIME_SLOT_TIME));
        // 获取当前时间
        LocalTime nowMin = DateTimes.nowMinute();
        // 未到配置时间，直接返回；到点后若未完成可继续补执行
        if (nowMin.isBefore(target)) {
            return;
        }
        LocalDate today = DateTimes.nowDate();
        long ttl = Math.max(DateTimes.ttlToEndOfDaySeconds(today), 60L);

        RBucket<String> done = redisson.getBucket(RedisKeys.slotGenDoneKey(today));
        if (done.isExists()) {
            return;
        }

        // 分布式短锁：只允许一个实例执行，失败不写 done，后续可重试
        RLock lock = redisson.getLock(RedisKeys.slotGenLockKey(today));
        boolean locked;
        try {
            // waitTime 为 0 表示不等待，直接尝试获取锁
            // leaseTime 为 120s，超过 120s 未完成则认为失败，其他实例可重试
            locked = lock.tryLock(0, 120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!locked) {
            return;
        }
        try {
            // 再检查 done，避免并发执行
            if (done.isExists()) {
                return;
            }
            // 查询有效 Session 并生成当日 TimeSlot（先删后建的实现放在 service 内）
            timeSlotService.generateForDate(today);
            done.set("1", Duration.ofSeconds(ttl));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
