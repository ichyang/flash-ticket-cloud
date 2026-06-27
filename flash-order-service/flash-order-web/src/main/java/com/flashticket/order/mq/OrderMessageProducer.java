package com.flashticket.order.mq;

import com.flashticket.order.config.FlashMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 * 订单消息生产者
 *
 * 职责：
 * 1. 秒杀扣减 Redis 成功后，发送异步消息到订单处理队列
 * 2. 同时发送延迟消息，15分钟后检测是否超时未支付
 */
@Component
public class OrderMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送订单处理消息（异步创建订单）
     *
     * @param userId    用户 ID
     * @param goodsId   商品 ID
     * @param count     购买数量
     * @param addressId 地址 ID
     */
    public void sendOrderProcessMessage(Long userId, Long goodsId, Integer count, Long addressId) {
        OrderProcessMessage message = new OrderProcessMessage();
        message.setUserId(userId);
        message.setGoodsId(goodsId);
        message.setCount(count);
        message.setAddressId(addressId);
        message.setCreateTime(new Date());

        rabbitTemplate.convertAndSend(
                FlashMqConfig.ORDER_EXCHANGE,
                FlashMqConfig.ORDER_PROCESS_ROUTING_KEY,
                message,
                msg -> {
                    msg.getMessageProperties().setMessageId(UUID.randomUUID().toString());
                    return msg;
                }
        );

        log.info("订单处理消息已发送: userId={}, goodsId={}, count={}", userId, goodsId, count);
    }

    /**
     * 发送延迟取消消息（15分钟后检测超时）
     */
    public void sendDelayCancelMessage(String orderNo) {
        DelayCancelMessage message = new DelayCancelMessage();
        message.setOrderNo(orderNo);
        message.setCreateTime(new Date());

        rabbitTemplate.convertAndSend(
                FlashMqConfig.ORDER_EXCHANGE,
                "flash.order.delay",
                message
        );

        log.info("延迟取消消息已发送: orderNo={}", orderNo);
    }

    // ==================== 消息体 ====================

    public static class OrderProcessMessage implements Serializable {
        private Long userId;
        private Long goodsId;
        private Integer count;
        private Long addressId;
        private Date createTime;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getGoodsId() { return goodsId; }
        public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        public Long getAddressId() { return addressId; }
        public void setAddressId(Long addressId) { this.addressId = addressId; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }

        @Override
        public String toString() {
            return "OrderProcessMessage{userId=" + userId + ", goodsId=" + goodsId +
                    ", count=" + count + ", addressId=" + addressId + '}';
        }
    }

    public static class DelayCancelMessage implements Serializable {
        private String orderNo;
        private Date createTime;

        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Date getCreateTime() { return createTime; }
        public void setCreateTime(Date createTime) { this.createTime = createTime; }

        @Override
        public String toString() {
            return "DelayCancelMessage{orderNo='" + orderNo + '\'' + '}';
        }
    }
}
