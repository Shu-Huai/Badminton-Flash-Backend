package shuhuai.badmintonflashbackend.constant;

import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.*;

/**
 * Redis 键生成器
 * 所有 Key 都以 "bf:" 前缀开头，统一管理，避免硬编码
 */
@SuppressWarnings("unused")
@Component
public class RedisKeys {
    private static final String PREFIX = "bf:";

    /** slotId 对应的信号量 */
    public static String semKey(Integer slotId) {
        return PREFIX + "sem:" + slotId;
    }


    /** slotId 对应的去重集合 */
    public static String dedupKey(Integer slotId) {
        return PREFIX + "dedup:" + slotId;
    }

    /** slotId 对应的 sessionId 映射 */
    public static String slotSessionKey(Integer slotId) {
        return PREFIX + "slot:session:" + slotId;
    }

    /** sessionId 对应的闸门 */
    public static String gateKey(Integer sessionId) {
        return PREFIX + "gate:" + sessionId;
    }

    /** sessionId 对应的闸门时间 */
    public static String gateTimeKey(Integer sessionId) {
        return PREFIX + "gate:time:" + sessionId;
    }

    /** slotId 对应的预热完成标记 */
    public static String warmupDoneKey(Integer slotId) {
        return PREFIX + "warmup:done:" + slotId;
    }

    /** 指定日期 + session 的预热完成标记 */
    public static String warmupSessionDoneKey(LocalDate day, Integer sessionId) {
        return PREFIX + "warmup:done:" + DateTimes.yyyymmdd(day) + ":" + sessionId;
    }

    /** 指定日期 + session 的预热任务锁 */
    public static String warmupSessionLockKey(LocalDate day, Integer sessionId) {
        return PREFIX + "warmup:lock:" + DateTimes.yyyymmdd(day) + ":" + sessionId;
    }

    /** 指定日期 + session 的 slots 已生成标记 */
    public static String slotGenDoneKey(LocalDate day, Integer sessionId) {
        return PREFIX + "slotgen:done:" + DateTimes.yyyymmdd(day) + ":" + sessionId;
    }

    /** 指定日期 + session 的 slots 生成任务锁 */
    public static String slotGenLockKey(LocalDate day, Integer sessionId) {
        return PREFIX + "slotgen:lock:" + DateTimes.yyyymmdd(day) + ":" + sessionId;
    }

    /** 限流键 */
    public static String limitKey(String userKey) {
        return PREFIX + "limit:" + userKey;
    }

    /**
     * 计算距离当天 23:59:59 的秒数
     * @param day 目标日期
     */
    public long ttlToEndOfDaySeconds(LocalDate day) {
        ZonedDateTime end = day.atTime(LocalTime.MAX.withNano(0))
                .atZone(DateTimes.zone());
        ZonedDateTime now = ZonedDateTime.now(DateTimes.zone());
        long sec = Duration.between(now, end).getSeconds();
        return Math.max(sec, 60); // 至少留 1 分钟
    }
}
