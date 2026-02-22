package shuhuai.badmintonflashbackend.scheduler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.service.IAdminService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.LocalTime;

/**
 * 每日定时任务：根据配置时间生成当日时间槽
 */
@Slf4j
@Component
public class DailySlotGenerateScheduler {
    @Resource
    private IAdminService adminService;

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
        for (FlashSession flashSession : adminService.getSessions()) {
            adminService.generateSlot(flashSession.getId());
            log.info("Generated slots for session {}", flashSession.getId());
        }
    }
}
