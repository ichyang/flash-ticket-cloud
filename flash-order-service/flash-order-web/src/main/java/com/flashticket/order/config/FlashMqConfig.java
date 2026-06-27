package com.flashticket.order.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 *
 * 设计：
 * - 订单延迟队列（15分钟后检测超时未支付）
 * - 订单处理队列（异步削峰）
 * - 死信队列（处理失败的消息）
 */
@Configuration
public class FlashMqConfig {

    private static final Logger log = LoggerFactory.getLogger(FlashMqConfig.class);

    // ==================== 订单处理队列 ====================

    /** 订单处理队列 */
    public static final String ORDER_PROCESS_QUEUE = "flash.order.process.queue";
    /** 订单处理交换机 */
    public static final String ORDER_EXCHANGE = "flash.order.exchange";
    /** 订单处理路由键 */
    public static final String ORDER_PROCESS_ROUTING_KEY = "flash.order.process";

    // ==================== 延迟队列（超时取消） ====================

    /** 延迟队列（15分钟过期） */
    public static final String ORDER_DELAY_QUEUE = "flash.order.delay.queue";
    /** 死信队列 */
    public static final String ORDER_DLX_QUEUE = "flash.order.dlx.queue";
    /** 死信交换机 */
    public static final String ORDER_DLX_EXCHANGE = "flash.order.dlx.exchange";
    /** 订单延迟时间（毫秒） */
    public static final long DELAY_TIME = 15 * 60 * 1000L; // 15分钟

    // ==================== 交换机 ====================

    /**
     * 订单业务交换机（Topic 类型，支持灵活路由）
     */
    @Bean
    public TopicExchange orderExchange() {
        return ExchangeBuilder.topicExchange(ORDER_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange orderDlxExchange() {
        return ExchangeBuilder.directExchange(ORDER_DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    // ==================== 队列 ====================

    /**
     * 订单处理队列
     * 下游消费者从该队列获取订单消息，异步执行订单创建
     */
    @Bean
    public Queue orderProcessQueue() {
        return QueueBuilder.durable(ORDER_PROCESS_QUEUE)
                .build();
    }

    /**
     * 延迟队列
     * 消息在 DELAY_TIME 后无人消费 → 进入死信交换机
     * 消费者收到死信消息后执行超时取消订单
     */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(ORDER_DELAY_QUEUE)
                .deadLetterExchange(ORDER_DLX_EXCHANGE)
                .deadLetterRoutingKey("order.timeout")
                .ttl((int) DELAY_TIME)
                .build();
    }

    /**
     * 死信队列
     * 接收超时未支付的订单，进行取消处理
     */
    @Bean
    public Queue orderDlxQueue() {
        return QueueBuilder.durable(ORDER_DLX_QUEUE)
                .build();
    }

    // ==================== 绑定 ====================

    @Bean
    public Binding orderProcessBinding() {
        return BindingBuilder.bind(orderProcessQueue())
                .to(orderExchange())
                .with(ORDER_PROCESS_ROUTING_KEY);
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderExchange())
                .with("flash.order.delay");
    }

    @Bean
    public Binding orderDlxBinding() {
        return BindingBuilder.bind(orderDlxQueue())
                .to(orderDlxExchange())
                .with("order.timeout");
    }

    // ==================== 消息转换器 ====================

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        // 消息发送确认回调
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.warn("消息发送失败: correlationId={}, cause={}",
                        correlationData != null ? correlationData.getId() : "null", cause);
            }
        });
        // 消息无法路由的回调
        template.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            log.warn("消息无法路由: exchange={}, routingKey={}, replyText={}",
                    exchange, routingKey, replyText);
        });
        return template;
    }
}
