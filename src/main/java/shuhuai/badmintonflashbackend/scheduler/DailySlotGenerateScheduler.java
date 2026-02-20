package shuhuai.badmintonflashbackend.scheduler;

import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.service.AdminService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.LocalTime;

/**
 * 每日定时任务：根据配置时间生成当日时间槽
 */
@Component
public class DailySlotGenerateScheduler {
    @Resource
    private AdminService adminService;

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
        }
    }
}
