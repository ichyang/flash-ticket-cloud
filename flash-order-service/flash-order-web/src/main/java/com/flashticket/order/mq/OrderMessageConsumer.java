package com.flashticket.order.mq;

import com.flashticket.order.config.FlashMqConfig;
import com.flashticket.order.dao.FlashTicketOrderMapper;
import com.flashticket.order.entity.FlashTicketOrder;
import com.flashticket.order.service.FlashTicketOrderService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * 订单消息消费者
 *
 * 监听两个队列：
 * 1. flash.order.process.queue → 异步处理订单创建
 * 2. flash.order.dlx.queue     → 处理超时未支付订单取消
 */
@Component
public class OrderMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageConsumer.class);

    @Autowired
    private FlashTicketOrderService orderService;

    @Autowired
    private FlashTicketOrderMapper orderMapper;

    @Autowired
    private MessageConverter messageConverter;

    /**
     * 处理异步订单创建
     * 从 MQ 获取消息，手动确认模式
     */
    @RabbitListener(queues = FlashMqConfig.ORDER_PROCESS_QUEUE, ackMode = "MANUAL")
    @Transactional
    public void handleOrderProcess(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            OrderMessageProducer.OrderProcessMessage orderMsg =
                    (OrderMessageProducer.OrderProcessMessage) messageConverter.fromMessage(message);

            log.info("收到订单处理消息: {}", orderMsg);

            // 实际执行订单创建（调用现有的 saveOrder 核心逻辑）
            // 这里用 orderService 的主方法创建订单
            // 注意：消息里传的是精简参数，完整流程需要从购物车拿数据
            // 实际生产环境会传完整的 cartItemIds 列表
            // 此处只做消息处理演示

            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.info("订单处理消息 ACK 完成: deliveryTag={}", deliveryTag);

        } catch (Exception e) {
            log.error("订单处理失败，发送到死信队列: deliveryTag={}", deliveryTag, e);
            try {
                // 第三个参数 requeue=false → 进入死信队列
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("basicNack 失败", ex);
            }
        }
    }

    /**
     * 处理超时未支付订单（来自死信队列）
     * 将订单状态从"待支付"改为"超时关闭"，并回滚 Redis 库存
     */
    @RabbitListener(queues = FlashMqConfig.ORDER_DLX_QUEUE, ackMode = "MANUAL")
    @Transactional
    public void handleDelayCancel(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            OrderMessageProducer.DelayCancelMessage cancelMsg =
                    (OrderMessageProducer.DelayCancelMessage) messageConverter.fromMessage(message);

            log.info("收到超时取消消息: orderNo={}", cancelMsg.getOrderNo());

            // 查询订单
            FlashTicketOrder order = orderMapper.selectByOrderNo(cancelMsg.getOrderNo());
            if (order == null) {
                log.warn("订单不存在: orderNo={}", cancelMsg.getOrderNo());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 只有"待支付"状态的订单才取消
            if (order.getOrderStatus() == 0) {
                // 调用取消方法
                orderService.cancelOrder(cancelMsg.getOrderNo(), order.getUserId());
                log.info("超时订单已取消: orderNo={}", cancelMsg.getOrderNo());
            } else {
                log.info("订单状态不是待支付，跳过取消: orderNo={}, status={}",
                        cancelMsg.getOrderNo(), order.getOrderStatus());
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("超时取消处理失败", e);
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("basicNack 失败", ex);
            }
        }
    }
}
