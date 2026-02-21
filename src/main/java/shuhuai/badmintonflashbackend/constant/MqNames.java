package shuhuai.badmintonflashbackend.constant;


public class MqNames {
    /** 预约主交换机（生产消息写入入口） */
    public static final String RESERVE_EXCHANGE = "reserve.direct";
    /** 预约主队列（正常消费） */
    public static final String RESERVE_QUEUE = "reserve.queue";
    /** 主交换机 -> 主队列路由键 */
    public static final String RESERVE_ROUTING_KEY = "reserve";

    /** 死信交换机（消费失败后的消息入口） */
    public static final String RESERVE_DLX_EXCHANGE = "reserve.dlx";
    /** 死信队列（用于排障/补偿处理） */
    public static final String RESERVE_DLQ = "reserve.dlq";
    /** 死信交换机 -> 死信队列路由键 */
    public static final String RESERVE_DLQ_ROUTING_KEY = "reserve.dlq";
}
