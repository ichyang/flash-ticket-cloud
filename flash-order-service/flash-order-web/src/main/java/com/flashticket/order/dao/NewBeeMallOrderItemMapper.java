/**
 * 严肃声明：
 * 开源版本请务必保留此注释头信息，若删除我方将保留所有法律责任追究！
 * 本软件已申请软件著作权，受国家版权局知识产权以及国家计算机软件著作权保护！
 * 可正常分享和学习源码，不得用于违法犯罪活动，违者必究！
 * Copyright (c) 2022 程序员十三 all rights reserved.
 * 版权所有，侵权必究！
 */
package com.flashticket.order.dao;

import com.flashticket.order.entity.FlashTicketOrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FlashTicketOrderItemMapper {
    int deleteByPrimaryKey(Long orderItemId);

    int insert(FlashTicketOrderItem record);

    int insertSelective(FlashTicketOrderItem record);

    FlashTicketOrderItem selectByPrimaryKey(Long orderItemId);

    /**
     * 根据订单id获取订单项列表
     *
     * @param orderId
     * @return
     */
    List<FlashTicketOrderItem> selectByOrderId(Long orderId);

    /**
     * 根据订单ids获取订单项列表
     *
     * @param orderIds
     * @return
     */
    List<FlashTicketOrderItem> selectByOrderIds(@Param("orderIds") List<Long> orderIds);

    /**
     * 批量insert订单项数据
     *
     * @param orderItems
     * @return
     */
    int insertBatch(@Param("orderItems") List<FlashTicketOrderItem> orderItems);

    int updateByPrimaryKeySelective(FlashTicketOrderItem record);

    int updateByPrimaryKey(FlashTicketOrderItem record);
}