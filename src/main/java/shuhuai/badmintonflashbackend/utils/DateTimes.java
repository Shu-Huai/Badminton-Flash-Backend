package shuhuai.badmintonflashbackend.utils;

import java.time.*;

public final class DateTimes {
    private DateTimes() {}

    /** 默认时区：上海 */
    public static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    /**
     * 当前分钟（去掉秒、纳秒）
     */
    public static LocalTime nowMinute() {
        return LocalTime.now(SHANGHAI).withSecond(0).withNano(0);
    }

    /**
     * 计算到当日结束（次日 00:00）的剩余秒数
     */
    public static long ttlToEndOfDaySeconds(LocalDate date) {
        LocalDateTime now = LocalDateTime.now(SHANGHAI);
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        return Duration.between(now, end).getSeconds();
    }

    /**
     * 把日期格式化为 yyyyMMdd 字符串
     */
    public static String yyyymmdd(LocalDate date) {
        return date.toString().replace("-", "");
    }
}