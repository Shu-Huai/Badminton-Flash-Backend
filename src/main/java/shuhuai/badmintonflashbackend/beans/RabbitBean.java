package shuhuai.badmintonflashbackend.beans;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;
import shuhuai.badmintonflashbackend.constant.MqNames;

@Configuration
public class RabbitBean {
    @Bean
    public DirectExchange reserveExchange() {
        return new DirectExchange(MqNames.RESERVE_EXCHANGE);
    }

    @Bean
    public Queue reserveQueue() {
        return new Queue(MqNames.RESERVE_QUEUE, true);
    }

    @Bean
    public Binding reserveBinding() {
        return BindingBuilder.bind(reserveQueue())
                .to(reserveExchange())
                .with(MqNames.RESERVE_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf,
                                         Jackson2JsonMessageConverter conv) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
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
        return factory;
    }
}