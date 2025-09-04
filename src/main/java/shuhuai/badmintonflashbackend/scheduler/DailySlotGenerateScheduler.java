package shuhuai.badmintonflashbackend.scheduler;

import jakarta.annotation.Resource;
import org.redisson.api.RBucket;
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
    @Scheduled(cron = "0 * * * * ?")
    public void maybeGenerateTodaySlots() {
        // 获取配置时间
        String raw = adminService.getConfigValue(ConfigKey.GENERATE_TIME_SLOT_TIME);
        LocalTime target = LocalTime.parse(raw);
        // 获取当前时间
        LocalTime nowMin = DateTimes.nowMinute();

        if (!nowMin.equals(target)) {
            return; // 不在配置分钟就直接返回
        }

        LocalDate today = LocalDate.now(DateTimes.SHANGHAI);
        long ttl = DateTimes.ttlToEndOfDaySeconds(today);

        // 幂等开关：slotgen，采用 setIfAbsent(value, ttl) 原子设置 + 过期
        RBucket<String> flag = redisson.getBucket(RedisKeys.slotGenKey());
        boolean first = flag.setIfAbsent("1", Duration.ofSeconds(ttl));
        if (!first) {
            return; // 已有标记，本分钟已处理过，直接返回
        }
        // 查询有效 Session 并生成当日 TimeSlot（先删后建的实现放在 service 内）
        timeSlotService.generateForDate(today);
    }
}