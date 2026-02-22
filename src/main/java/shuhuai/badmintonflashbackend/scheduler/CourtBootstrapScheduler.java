package shuhuai.badmintonflashbackend.scheduler;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.service.impl.AdminServiceImpl;

import java.util.concurrent.TimeUnit;

@Component
public class CourtBootstrapScheduler {
    private static final Logger log = LoggerFactory.getLogger(CourtBootstrapScheduler.class);

    private final AdminServiceImpl adminService;
    private final RedissonClient redisson;

    public CourtBootstrapScheduler(AdminServiceImpl adminService, RedissonClient redisson) {
        this.adminService = adminService;
        this.redisson = redisson;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileCourtsOnStartup() {
        RLock lock = redisson.getLock(RedisKeys.courtBootstrapLockKey());
        boolean locked;
        try {
            locked = lock.tryLock(0, 180, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("启动球场配置对账任务获取锁被中断");
            return;
        }
        if (!locked) {
            log.info("启动球场配置对账任务被其他实例执行，当前实例跳过");
            return;
        }
        try {
            adminService.reconcileCourtsByConfigAtStartup();
        } catch (Exception e) {
            log.error("启动球场配置对账任务执行失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
