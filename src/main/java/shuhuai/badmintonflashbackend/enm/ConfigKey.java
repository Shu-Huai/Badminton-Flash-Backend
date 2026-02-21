package shuhuai.badmintonflashbackend.enm;

/**
 * @author Shuhuai
 * &#064;description  配置项的键
 * &#064;date  2024/10/27 19:11
 */
public enum ConfigKey {
    // 预热提前的分钟数
    WARMUP_MINUTE,
    // 未支付自动取消分钟数
    PAY_TIMEOUT_MINUTE,
    // 生成当天slot_time的时间
    GENERATE_TIME_SLOT_TIME,
    // 球场数量
    COURT_COUNT,
    // 球场命名规范
    COURT_NAME_FORMAT,
}
