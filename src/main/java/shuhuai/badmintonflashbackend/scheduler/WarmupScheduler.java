package shuhuai.badmintonflashbackend.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.service.IAdminService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.*;
import java.util.List;

@Slf4j
@Component
public class WarmupScheduler {
    private final IFlashSessionMapper sessionMapper;
    private final IAdminService adminService;
    private final RedissonClient redisson;

    @Autowired
    public WarmupScheduler(IFlashSessionMapper sessionMapper, IAdminService adminService, RedissonClient redisson) {
        this.sessionMapper = sessionMapper;
        this.adminService = adminService;
        this.redisson = redisson;
    }


    @Scheduled(cron = "0 * * * * ?", zone = "${app.timezone}")
    public void warmupNearFutureSlots() {
        // 读取提前分钟数（WARMUP_MINUTE）
        int warmupMinutes = Integer.parseInt(adminService.getConfigValue(ConfigKey.WARMUP_MINUTE));
        LocalTime now = DateTimes.nowMinute();
        LocalTime upper = now.plusMinutes(warmupMinutes);

        List<FlashSession> flashSessions = sessionMapper.selectList(Wrappers.<FlashSession>lambdaQuery()
                .ge(FlashSession::getFlashTime, now)
                .le(FlashSession::getFlashTime, upper));
        for (FlashSession flashSession : flashSessions) {
            adminService.warmupSession(flashSession);
            log.info("Warmup session {} at {}", flashSession.getId(), flashSession.getFlashTime());
        }
    }

    /**
     * 开闸
     */
    @Scheduled(cron = "0 * * * * ?", zone = "${app.timezone}")
    public void openGate() {
        LocalDate today = DateTimes.nowDate();
        LocalTime now = DateTimes.nowTime();
        List<FlashSession> flashSessions = sessionMapper.selectList(Wrappers.<FlashSession>lambdaQuery()
                .le(FlashSession::getFlashTime, now));
        for (FlashSession flashSession : flashSessions) {
            // 闸门
            RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(flashSession.getId()));
            if ("1".equals(gate.get())) {
                continue;
            }
            adminService.warmupSession(flashSession);
            if (!isWarmupDone(today, flashSession.getId())) {
                continue;
            }
            gate.set("1", Duration.ofSeconds(DateTimes.ttlToEndOfTodaySeconds()));
        }
    }

    private boolean isWarmupDone(LocalDate day, Integer sessionId) {
        return redisson.getBucket(RedisKeys.warmupSessionDoneKey(day, sessionId)).isExists();
    }
}
