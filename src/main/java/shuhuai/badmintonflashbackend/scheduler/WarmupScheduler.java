package shuhuai.badmintonflashbackend.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.service.AdminService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.*;
import java.util.List;

@Component
public class WarmupScheduler {
    private final IFlashSessionMapper sessionMapper;
    private final AdminService adminService;
    private final RedissonClient redisson;

    @Autowired
    public WarmupScheduler(IFlashSessionMapper sessionMapper, AdminService adminService, RedissonClient redisson) {
        this.sessionMapper = sessionMapper;
        this.adminService = adminService;
        this.redisson = redisson;
    }


    @Scheduled(cron = "0 * * * * ?", zone = "${app.timezone}")
    public void warmupNearFutureSlots() {
        // 读取提前分钟数（WARMUP_MINUTE）
        int warmupMinutes = Integer.parseInt(adminService.getConfigValue(ConfigKey.WARMUP_MINUTE));
        // 当前分钟 + 提前分钟数 = 开始时间
        LocalTime targetStart = DateTimes.nowMinute().plusMinutes(warmupMinutes);
        // 查询所有开始时间为开始时间的场次
        List<FlashSession> flashSessions = sessionMapper.selectList(Wrappers.<FlashSession>lambdaQuery()
                .eq(FlashSession::getFlashTime, targetStart));
        // 如果没有场次，直接返回
        if (flashSessions.isEmpty()) {
            return;
        }
        // 调用 AdminService 预热场次
        for (FlashSession flashSession : flashSessions) {
            adminService.warmupSession(flashSession);
        }
    }

    /**
     * 开闸
     */
    @Scheduled(cron = "0 * * * * ?", zone = "${app.timezone}")
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
            gate.set("1", Duration.ofSeconds(DateTimes.ttlToEndOfTodaySeconds()));
        }
    }
}
