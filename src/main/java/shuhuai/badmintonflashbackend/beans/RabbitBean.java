package shuhuai.badmintonflashbackend.beans;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;
import shuhuai.badmintonflashbackend.constant.MqNames;
import shuhuai.badmintonflashbackend.mq.ReservePublishCallbackHandler;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitBean {
    @Bean
    public DirectExchange reserveExchange() {
        return new DirectExchange(MqNames.RESERVE_EXCHANGE);
    }

    @Bean
    public Queue reserveQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", MqNames.RESERVE_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", MqNames.RESERVE_DLQ_ROUTING_KEY);
        return new Queue(MqNames.RESERVE_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding reserveBinding() {
        return BindingBuilder.bind(reserveQueue())
                .to(reserveExchange())
                .with(MqNames.RESERVE_ROUTING_KEY);
    }

    @Bean
    public DirectExchange reserveDlxExchange() {
        return new DirectExchange(MqNames.RESERVE_DLX_EXCHANGE);
    }

    @Bean
    public Queue reserveDlq() {
        return new Queue(MqNames.RESERVE_DLQ, true);
    }

    @Bean
    public Binding reserveDlqBinding() {
        return BindingBuilder.bind(reserveDlq())
                .to(reserveDlxExchange())
                .with(MqNames.RESERVE_DLQ_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf,
                                         Jackson2JsonMessageConverter conv,
                                         ReservePublishCallbackHandler callbackHandler) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
        t.setMandatory(true);
        t.setConfirmCallback(callbackHandler::onConfirm);
        t.setReturnsCallback(callbackHandler::onReturned);
        return t;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory cf,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            Jackson2JsonMessageConverter conv) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, cf); // 继承 boot 的并发、ack 等配置
        factory.setMessageConverter(conv);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(consumerRetryInterceptor());
        return factory;
    }

    @Bean
    public Advice consumerRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 5000)
                .recoverer((_, cause) -> {
                    throw new AmqpRejectAndDontRequeueException("reserve consume failed after retries", cause);
                })
                .build();
    }
}
