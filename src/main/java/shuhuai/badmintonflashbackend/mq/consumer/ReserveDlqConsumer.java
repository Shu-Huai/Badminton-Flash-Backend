package shuhuai.badmintonflashbackend.mq.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.constant.MqNames;
import shuhuai.badmintonflashbackend.mq.ReservePublishCallbackHandler;
import shuhuai.badmintonflashbackend.mq.message.ReserveMessage;

@Slf4j
@Component
public class ReserveDlqConsumer {
    private final ReservePublishCallbackHandler publishCallbackHandler;

    public ReserveDlqConsumer(ReservePublishCallbackHandler publishCallbackHandler) {
        this.publishCallbackHandler = publishCallbackHandler;
    }

    @RabbitListener(queues = MqNames.RESERVE_DLQ, containerFactory = "rabbitListenerContainerFactory")
    public void handleDlq(ReserveMessage message, Message rawMessage) {
        String traceId = null;
        if (message != null) {
            traceId = message.getTraceId();
        }
        if (traceId == null || traceId.isEmpty()) {
            traceId = rawMessage.getMessageProperties().getMessageId();
        }
        if ((traceId == null || traceId.isEmpty()) && rawMessage.getMessageProperties().getHeaders() != null) {
            Object traceHeader = rawMessage.getMessageProperties().getHeaders().get("traceId");
            traceId = traceHeader == null ? null : String.valueOf(traceHeader);
        }
        if (traceId == null || traceId.isEmpty()) {
            log.error("DLQ 消息缺少 traceId，无法执行最终补偿，message={}", message);
            return;
        }
        publishCallbackHandler.compensateByTraceId(traceId, "dlq-final-fail");
        log.warn("DLQ 最终失败补偿已执行 traceId={}", traceId);
    }
}
