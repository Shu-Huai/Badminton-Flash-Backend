package shuhuai.badmintonflashbackend.utils;

import java.time.*;
import java.util.Objects;

@SuppressWarnings("unused")
public final class DateTimes {
    private static volatile ZoneId APP_ZONE;

    public static void setAppZone(ZoneId zoneId) {
        APP_ZONE = Objects.requireNonNull(zoneId);
    }

    /**
     * 获取应用时区
     */
    public static ZoneId zone() {
        return APP_ZONE;
    }

    /**
     * 当前时间（应用时区）
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(APP_ZONE);
    }

    /**
     * 当前时间（指定时区）
     */
    public static LocalDateTime now(ZoneId zoneId) {
        return LocalDateTime.now(zoneId);
    }

    /**
     * 当前日期（应用时区）
     */
    public static LocalDate nowDate() {
        return LocalDate.now(APP_ZONE);
    }

    /**
     * 当前时间（指定时区）
     */
    public static LocalTime nowTime(ZoneId zoneId) {
        return LocalTime.now(zoneId);
    }

    /**
     * 当前时间（应用时区）
     */
    public static LocalTime nowTime() {
        return LocalTime.now(APP_ZONE);
    }

    /**
     * 当前分钟（去掉秒、纳秒）
     */
    public static LocalTime nowMinute() {
        return LocalTime.now(APP_ZONE).withSecond(0).withNano(0);
    }

    /**
     * 计算到指定日期结束（次日 00:00）的剩余秒数
     */
    public static long ttlToEndOfDaySeconds(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return Duration.between(now, end).getSeconds();
    }

    /**
     * 计算到当日结束（次日 00:00）的剩余秒数
     */
    public static long ttlToEndOfTodaySeconds() {
        return ttlToEndOfDaySeconds(nowDate());
    }

    /**
     * 把日期格式化为 yyyyMMdd 字符串
     */
    public static String yyyymmdd(LocalDate date) {
        return date.toString().replace("-", "");
    }
}
