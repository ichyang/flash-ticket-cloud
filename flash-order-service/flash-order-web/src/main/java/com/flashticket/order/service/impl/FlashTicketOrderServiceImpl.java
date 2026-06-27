/**
 * 严肃声明：
 * 开源版本请务必保留此注释头信息，若删除我方将保留所有法律责任追究！
 * 本软件已申请软件著作权，受国家版权局知识产权以及国家计算机软件著作权保护！
 * 可正常分享和学习源码，不得用于违法犯罪活动，违者必究！
 * Copyright (c) 2022 程序员十三 all rights reserved.
 * 版权所有，侵权必究！
 */
package com.flashticket.order.service.impl;

import io.seata.spring.annotation.GlobalTransactional;
import com.flashticket.common.enums.ServiceResultEnum;
import com.flashticket.common.dto.PageQueryUtil;
import com.flashticket.common.dto.PageResult;
import com.flashticket.common.dto.Result;
import com.flashticket.common.enums.FlashTicketOrderStatusEnum;
import com.flashticket.common.enums.PayStatusEnum;
import com.flashticket.common.enums.PayTypeEnum;
import com.flashticket.common.exception.FlashTicketException;
import com.flashticket.common.util.BeanUtil;
import com.flashticket.common.util.NumberUtil;
import com.flashticket.ticket.dto.FlashTicketGoodsDTO;
import com.flashticket.ticket.dto.StockNumDTO;
import com.flashticket.ticket.dto.UpdateStockNumDTO;
import com.flashticket.ticket.openfeign.FlashTicketGoodsServiceFeign;
import com.flashticket.order.controller.vo.FlashTicketOrderDetailVO;
import com.flashticket.order.controller.vo.FlashTicketOrderItemVO;
import com.flashticket.order.controller.vo.FlashTicketOrderListVO;
import com.flashticket.order.dao.FlashTicketOrderAddressMapper;
import com.flashticket.order.dao.FlashTicketOrderItemMapper;
import com.flashticket.order.dao.FlashTicketOrderMapper;
import com.flashticket.order.entity.MallUserAddress;
import com.flashticket.order.entity.FlashTicketOrder;
import com.flashticket.order.entity.FlashTicketOrderAddress;
import com.flashticket.order.entity.FlashTicketOrderItem;
import com.flashticket.order.service.FlashTicketOrderService;
import com.flashticket.order.service.FlashDistributedLockService;
import com.flashticket.order.mq.OrderMessageProducer;
import dto.NewBeeMallShoppingCartItemDTO;
openfeign.NewBeeCloudShopCartServiceFeign;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

@Service
public class FlashTicketOrderServiceImpl implements FlashTicketOrderService {

    @Autowired
    private FlashTicketOrderMapper newBeeMallOrderMapper;

    @Autowired
    private FlashTicketOrderItemMapper newBeeMallOrderItemMapper;

    @Autowired
    private FlashTicketOrderAddressMapper newBeeMallOrderAddressMapper;

    @Autowired
    private FlashTicketGoodsServiceFeign goodsService;

    @Autowired
    private FlashDistributedLockService distributedLockService;

    @Autowired
    private OrderMessageProducer orderMessageProducer;

    @Autowired


    @Override
    public FlashTicketOrderDetailVO getOrderDetailByOrderId(Long orderId) {
        FlashTicketOrder newBeeMallOrder = newBeeMallOrderMapper.selectByPrimaryKey(orderId);
        if (newBeeMallOrder == null) {
            FlashTicketException.fail(ServiceResultEnum.DATA_NOT_EXIST.getResult());
        }
        List<FlashTicketOrderItem> orderItems = newBeeMallOrderItemMapper.selectByOrderId(newBeeMallOrder.getOrderId());
        //获取订单项数据
        if (!CollectionUtils.isEmpty(orderItems)) {
            List<FlashTicketOrderItemVO> newBeeMallOrderItemVOS = BeanUtil.copyList(orderItems, FlashTicketOrderItemVO.class);
            FlashTicketOrderDetailVO newBeeMallOrderDetailVO = new FlashTicketOrderDetailVO();
            BeanUtil.copyProperties(newBeeMallOrder, newBeeMallOrderDetailVO);
            newBeeMallOrderDetailVO.setOrderStatusString(FlashTicketOrderStatusEnum.getFlashTicketOrderStatusEnumByStatus(newBeeMallOrderDetailVO.getOrderStatus()).getName());
            newBeeMallOrderDetailVO.setPayTypeString(PayTypeEnum.getPayTypeEnumByType(newBeeMallOrderDetailVO.getPayType()).getName());
            newBeeMallOrderDetailVO.setFlashTicketOrderItemVOS(newBeeMallOrderItemVOS);
            return newBeeMallOrderDetailVO;
        } else {
            FlashTicketException.fail(ServiceResultEnum.ORDER_ITEM_NULL_ERROR.getResult());
            return null;
        }
    }

    @Override
    public FlashTicketOrderDetailVO getOrderDetailByOrderNo(String orderNo, Long userId) {
        FlashTicketOrder newBeeMallOrder = newBeeMallOrderMapper.selectByOrderNo(orderNo);
        if (newBeeMallOrder == null) {
            FlashTicketException.fail(ServiceResultEnum.DATA_NOT_EXIST.getResult());
        }
        if (!userId.equals(newBeeMallOrder.getUserId())) {
            FlashTicketException.fail(ServiceResultEnum.REQUEST_FORBIDEN_ERROR.getResult());
        }
        List<FlashTicketOrderItem> orderItems = newBeeMallOrderItemMapper.selectByOrderId(newBeeMallOrder.getOrderId());
        //获取订单项数据
        if (CollectionUtils.isEmpty(orderItems)) {
            FlashTicketException.fail(ServiceResultEnum.ORDER_ITEM_NOT_EXIST_ERROR.getResult());
        }
        List<FlashTicketOrderItemVO> newBeeMallOrderItemVOS = BeanUtil.copyList(orderItems, FlashTicketOrderItemVO.class);
        FlashTicketOrderDetailVO newBeeMallOrderDetailVO = new FlashTicketOrderDetailVO();
        BeanUtil.copyProperties(newBeeMallOrder, newBeeMallOrderDetailVO);
        newBeeMallOrderDetailVO.setOrderStatusString(FlashTicketOrderStatusEnum.getFlashTicketOrderStatusEnumByStatus(newBeeMallOrderDetailVO.getOrderStatus()).getName());
        newBeeMallOrderDetailVO.setPayTypeString(PayTypeEnum.getPayTypeEnumByType(newBeeMallOrderDetailVO.getPayType()).getName());
        newBeeMallOrderDetailVO.setFlashTicketOrderItemVOS(newBeeMallOrderItemVOS);
        return newBeeMallOrderDetailVO;
    }


    @Override
    public PageResult getMyOrders(PageQueryUtil pageUtil) {
        int total = newBeeMallOrderMapper.getTotalFlashTicketOrders(pageUtil);
        List<FlashTicketOrder> newBeeMallOrders = newBeeMallOrderMapper.findFlashTicketOrderList(pageUtil);
        List<FlashTicketOrderListVO> orderListVOS = new ArrayList<>();
        if (total > 0) {
            //数据转换 将实体类转成vo
            orderListVOS = BeanUtil.copyList(newBeeMallOrders, FlashTicketOrderListVO.class);
            //设置订单状态中文显示值
            for (FlashTicketOrderListVO newBeeMallOrderListVO : orderListVOS) {
                newBeeMallOrderListVO.setOrderStatusString(FlashTicketOrderStatusEnum.getFlashTicketOrderStatusEnumByStatus(newBeeMallOrderListVO.getOrderStatus()).getName());
            }
            List<Long> orderIds = newBeeMallOrders.stream().map(FlashTicketOrder::getOrderId).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(orderIds)) {
                List<FlashTicketOrderItem> orderItems = newBeeMallOrderItemMapper.selectByOrderIds(orderIds);
                Map<Long, List<FlashTicketOrderItem>> itemByOrderIdMap = orderItems.stream().collect(groupingBy(FlashTicketOrderItem::getOrderId));
                for (FlashTicketOrderListVO newBeeMallOrderListVO : orderListVOS) {
                    //封装每个订单列表对象的订单项数据
                    if (itemByOrderIdMap.containsKey(newBeeMallOrderListVO.getOrderId())) {
                        List<FlashTicketOrderItem> orderItemListTemp = itemByOrderIdMap.get(newBeeMallOrderListVO.getOrderId());
                        //将FlashTicketOrderItem对象列表转换成FlashTicketOrderItemVO对象列表
                        List<FlashTicketOrderItemVO> newBeeMallOrderItemVOS = BeanUtil.copyList(orderItemListTemp, FlashTicketOrderItemVO.class);
                        newBeeMallOrderListVO.setFlashTicketOrderItemVOS(newBeeMallOrderItemVOS);
                    }
                }
            }
        }
        PageResult pageResult = new PageResult(orderListVOS, total, pageUtil.getLimit(), pageUtil.getPage());
        return pageResult;
    }

    @Override
    public String cancelOrder(String orderNo, Long userId) {
        FlashTicketOrder newBeeMallOrder = newBeeMallOrderMapper.selectByOrderNo(orderNo);
        if (newBeeMallOrder != null) {
            //验证是否是当前userId下的订单，否则报错
            if (!userId.equals(newBeeMallOrder.getUserId())) {
                FlashTicketException.fail(ServiceResultEnum.NO_PERMISSION_ERROR.getResult());
            }
            //订单状态判断
            if (newBeeMallOrder.getOrderStatus().intValue() == FlashTicketOrderStatusEnum.ORDER_SUCCESS.getOrderStatus()
                    || newBeeMallOrder.getOrderStatus().intValue() == FlashTicketOrderStatusEnum.ORDER_CLOSED_BY_MALLUSER.getOrderStatus()
                    || newBeeMallOrder.getOrderStatus().intValue() == FlashTicketOrderStatusEnum.ORDER_CLOSED_BY_EXPIRED.getOrderStatus()
                    || newBeeMallOrder.getOrderStatus().intValue() == FlashTicketOrderStatusEnum.ORDER_CLOSED_BY_JUDGE.getOrderStatus()) {
                return ServiceResultEnum.ORDER_STATUS_ERROR.getResult();
            }
            if (newBeeMallOrderMapper.closeOrder(Collections.singletonList(newBeeMallOrder.getOrderId()), FlashTicketOrderStatusEnum.ORDER_CLOSED_BY_MALLUSER.getOrderStatus()) > 0) {
                return ServiceResultEnum.SUCCESS.getResult();
            } else {
                return ServiceResultEnum.DB_ERROR.getResult();
            }
        }
        return ServiceResultEnum.ORDER_NOT_EXIST_ERROR.getResult();
    }

    @Override
    public String finishOrder(String orderNo, Long userId) {
        FlashTicketOrder newBeeMallOrder = newBeeMallOrderMapper.selectByOrderNo(orderNo);
        if (newBeeMallOrder != null) {
            //验证是否是当前userId下的订单，否则报错
            if (!userId.equals(newBeeMallOrder.getUserId())) {
                return ServiceResultEnum.NO_PERMISSION_ERROR.getResult();
            }
            //订单状态判断 非出库状态下不进行修改操作
            if (newBeeMallOrder.getOrderStatus().intValue() != FlashTicketOrderStatusEnum.ORDER_EXPRESS.getOrderStatus()) {
                return ServiceResultEnum.ORDER_STATUS_ERROR.getResult();
            }
            newBeeMallOrder.setOrderStatus((byte) FlashTicketOrderStatusEnum.ORDER_SUCCESS.getOrderStatus());
            newBeeMallOrder.setUpdateTime(new Date());
            if (newBeeMallOrderMapper.updateByPrimaryKeySelective(newBeeMallOrder) > 0) {
                return ServiceResultEnum.SUCCESS.getResult();
            } else {
                return ServiceResultEnum.DB_ERROR.getResult();
            }
        }
        return ServiceResultEnum.ORDER_NOT_EXIST_ERROR.getResult();
    }

    @Override
    public String paySuccess(String orderNo, int payType) {
        FlashTicketOrder newBeeMallOrder = newBeeMallOrderMapper.selectByOrderNo(orderNo);
        if (newBeeMallOrder != null) {
            //订单状态判断 非待支付状态下不进行修改操作
            if (newBeeMallOrder.getOrderStatus().intValue() != FlashTicketOrderStatusEnum.ORDER_PRE_PAY.getOrderStatus()) {
                return ServiceResultEnum.ORDER_STATUS_ERROR.getResult();
            }
            newBeeMallOrder.setOrderStatus((byte) FlashTicketOrderStatusEnum.ORDER_PAID.getOrderStatus());
            newBeeMallOrder.setPayType((byte) payType);
            newBeeMallOrder.setPayStatus((byte) PayStatusEnum.PAY_SUCCESS.getPayStatus());
            newBeeMallOrder.setPayTime(new Date());
            newBeeMallOrder.setUpdateTime(new Date());
            if (newBeeMallOrderMapper.updateByPrimaryKeySelective(newBeeMallOrder) > 0) {
                return ServiceResultEnum.SUCCESS.getResult();
            } else {
                return ServiceResultEnum.DB_ERROR.getResult();
            }
        }
        return ServiceResultEnum.ORDER_NOT_EXIST_ERROR.getResult();
    }

    @Override
    @Transactional
    @GlobalTransactional
    public String saveOrder(Long mallUserId, MallUserAddress address, List<Long> cartItemIds) {
        // ===== 分布式锁：防止同一用户重复下单 =====
        // 使用 Redisson 锁，粒度是 userId + goodsId
        // 因为秒杀场景下同一用户对同一商品只能下一单
        // 这里取第一个 goodsId 做锁粒度（简化，实际可以遍历全部）
        Long firstGoodsId = null;
        //调用购物车服务feign获取数据

        if (cartItemDTOListResult == null || cartItemDTOListResult.getResultCode() != 200) {
            FlashTicketException.fail("参数异常");
        }

        if (CollectionUtils.isEmpty(itemsForSave)) {
            //无数据
            FlashTicketException.fail("参数异常");
        }

        // 取第一个商品 ID 做锁粒度
        firstGoodsId = itemsForSave.get(0).getGoodsId();

        if (firstGoodsId != null) {
            Boolean locked = distributedLockService.executeWithOrderLock(mallUserId, firstGoodsId, () -> {
                doSaveOrder(mallUserId, address, cartItemIds, itemsForSave, cartItemDTOListResult);
                return true;
            });
            if (locked == null || !locked) {
                throw new RuntimeException("操作太频繁，请稍后重试");
            }
            return "订单处理中";
        }

        // 兜底走原逻辑
        return doSaveOrder(mallUserId, address, cartItemIds, itemsForSave, cartItemDTOListResult);
    }

    /**
     * 实际执行订单创建（被锁保护）
     */
    private String doSaveOrder(Long mallUserId, MallUserAddress address,
                                List<Long> cartItemIds,
                                List<NewBeeMallShoppingCartItemDTO> itemsForSave,
                                Result<List<NewBeeMallShoppingCartItemDTO>> cartItemDTOListResult) {
        List<Long> goodsIds = itemsForSave.stream().map(NewBeeMallShoppingCartItemDTO::getGoodsId).collect(Collectors.toList());
        //调用商品服务feign获取数据
        Result<List<FlashTicketGoodsDTO>> goodsDTOListResult = goodsService.listByGoodsIds(goodsIds);
        if (goodsDTOListResult == null || goodsDTOListResult.getResultCode() != 200) {
            FlashTicketException.fail("参数异常");
        }
        List<FlashTicketGoodsDTO> newBeeMallGoods = goodsDTOListResult.getData();
        //检查是否包含已下架商品
        List<FlashTicketGoodsDTO> goodsListNotSelling = newBeeMallGoods.stream()
                .filter(newBeeMallGoodsTemp -> newBeeMallGoodsTemp.getGoodsSellStatus() != 0)
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(goodsListNotSelling)) {
            //goodsListNotSelling 对象非空则表示有下架商品
            FlashTicketException.fail(goodsListNotSelling.get(0).getGoodsName() + "已下架，无法生成订单");
        }
        Map<Long, FlashTicketGoodsDTO> newBeeMallGoodsMap = newBeeMallGoods.stream().collect(Collectors.toMap(FlashTicketGoodsDTO::getGoodsId, Function.identity(), (entity1, entity2) -> entity1));
        //判断商品库存
        for (NewBeeMallShoppingCartItemDTO cartItemDTO : itemsForSave) {
            //查出的商品中不存在购物车中的这条关联商品数据，直接返回错误提醒
            if (!newBeeMallGoodsMap.containsKey(cartItemDTO.getGoodsId())) {
                FlashTicketException.fail(ServiceResultEnum.SHOPPING_ITEM_ERROR.getResult());
            }
            //存在数量大于库存的情况，直接返回错误提醒
            if (cartItemDTO.getGoodsCount() > newBeeMallGoodsMap.get(cartItemDTO.getGoodsId()).getStockNum()) {
                FlashTicketException.fail(ServiceResultEnum.SHOPPING_ITEM_COUNT_ERROR.getResult());
            }
        }
        //删除购物项
        if (!CollectionUtils.isEmpty(itemIdList) && !CollectionUtils.isEmpty(goodsIds) && !CollectionUtils.isEmpty(newBeeMallGoods)) {

            //调用购物车服务feign删除数据
            Result<Boolean> deleteByCartItemIdsResult = shopCartService.deleteByCartItemIds(itemIdList);
            if (deleteByCartItemIdsResult != null && deleteByCartItemIdsResult.getResultCode() == 200) {


                List<StockNumDTO> stockNumDTOS = BeanUtil.copyList(itemsForSave, StockNumDTO.class);
                UpdateStockNumDTO updateStockNumDTO = new UpdateStockNumDTO();
                updateStockNumDTO.setStockNumDTOS(stockNumDTOS);

                //调用商品服务feign修改库存数据
                Result<Boolean> updateStockResult = goodsService.updateStock(updateStockNumDTO);
                if (updateStockResult == null || updateStockResult.getResultCode() != 200) {
                    FlashTicketException.fail(ServiceResultEnum.PARAM_ERROR.getResult());
                }
                if (!updateStockResult.getData()) {
                    FlashTicketException.fail(ServiceResultEnum.SHOPPING_ITEM_COUNT_ERROR.getResult());
                }
                //生成订单号
                String orderNo = NumberUtil.genOrderNo();
                int priceTotal = 0;
                //保存订单
                FlashTicketOrder newBeeMallOrder = new FlashTicketOrder();
                newBeeMallOrder.setOrderNo(orderNo);
                newBeeMallOrder.setUserId(mallUserId);
                //总价
                for (NewBeeMallShoppingCartItemDTO cartItemDTO : itemsForSave) {
                    priceTotal += cartItemDTO.getGoodsCount() * newBeeMallGoodsMap.get(cartItemDTO.getGoodsId()).getSellingPrice();
                }
                if (priceTotal < 1) {
                    FlashTicketException.fail(ServiceResultEnum.ORDER_PRICE_ERROR.getResult());
                }
                newBeeMallOrder.setTotalPrice(priceTotal);
                String extraInfo = "";
                newBeeMallOrder.setExtraInfo(extraInfo);
                //生成订单项并保存订单项纪录
                if (newBeeMallOrderMapper.insertSelective(newBeeMallOrder) > 0) {
                    //生成订单收货地址快照，并保存至数据库
                    FlashTicketOrderAddress newBeeMallOrderAddress = new FlashTicketOrderAddress();
                    BeanUtil.copyProperties(address, newBeeMallOrderAddress);
                    newBeeMallOrderAddress.setOrderId(newBeeMallOrder.getOrderId());
                    //生成所有的订单项快照，并保存至数据库
                    List<FlashTicketOrderItem> newBeeMallOrderItems = new ArrayList<>();
                    for (NewBeeMallShoppingCartItemDTO cartItemDTO : itemsForSave) {
                        FlashTicketOrderItem newBeeMallOrderItem = new FlashTicketOrderItem();
                        //使用BeanUtil工具类将cartItemDTO中的属性复制到newBeeMallOrderItem对象中
                        BeanUtil.copyProperties(cartItemDTO, newBeeMallOrderItem);
                        newBeeMallOrderItem.setGoodsCoverImg(newBeeMallGoodsMap.get(cartItemDTO.getGoodsId()).getGoodsCoverImg());
                        newBeeMallOrderItem.setGoodsName(newBeeMallGoodsMap.get(cartItemDTO.getGoodsId()).getGoodsName());
                        newBeeMallOrderItem.setSellingPrice(newBeeMallGoodsMap.get(cartItemDTO.getGoodsId()).getSellingPrice());
                        //FlashTicketOrderMapper文件insert()方法中使用了useGeneratedKeys因此orderId可以获取到
                        newBeeMallOrderItem.setOrderId(newBeeMallOrder.getOrderId());
                        newBeeMallOrderItems.add(newBeeMallOrderItem);
                    }
                    //保存至数据库
                    if (newBeeMallOrderItemMapper.insertBatch(newBeeMallOrderItems) > 0 && newBeeMallOrderAddressMapper.insertSelective(newBeeMallOrderAddress) > 0) {
                        // 发送 MQ 延迟消息：15 分钟后检测是否超时未支付
                        try {
                            orderMessageProducer.sendDelayCancelMessage(orderNo);
                        } catch (Exception e) {
                            log.error("发送延迟取消消息失败，不影响主流程: orderNo={}", orderNo, e);
                        }
                        //所有操作成功后，将订单号返回，以供Controller方法跳转到订单详情
                        return orderNo;
                    }
                    FlashTicketException.fail(ServiceResultEnum.ORDER_PRICE_ERROR.getResult());
                }
                FlashTicketException.fail(ServiceResultEnum.DB_ERROR.getResult());
            }
            FlashTicketException.fail(ServiceResultEnum.DB_ERROR.getResult());
        }
        FlashTicketException.fail(ServiceResultEnum.SHOPPING_ITEM_ERROR.getResult());
        return ServiceResultEnum.SHOPPING_ITEM_ERROR.getResult();
    }


    @Override
    public PageResult getFlashTicketOrdersPage(PageQueryUtil pageUtil) {
        List<FlashTicketOrder> newBeeMallOrders = newBeeMallOrderMapper.findFlashTicketOrderList(pageUtil);
        int total = newBeeMallOrderMapper.getTotalFlashTicketOrders(pageUtil);
        PageResult pageResult = new PageResult(newBeeMallOrders, total, pageUtil.getLimit(), pageUtil.getPage());
        return pageResult;
    }

    @Override
    @Transactional
    public String updateOrderInfo(FlashTicketOrder newBeeMallOrder) {
        FlashTicketOrder temp = newBeeMallOrderMapper.selectByPrimaryKey(newBeeMallOrder.getOrderId());
        //不为空且orderStatus>=0且状态为出库之前可以修改部分信息
        if (temp != null && temp.getOrderStatus() >= 0 && temp.getOrderStatus() < 3) {
            temp.setTotalPrice(newBeeMallOrder.getTotalPrice());
            temp.setUpdateTime(new Date());
            if (newBeeMallOrderMapper.updateByPrimaryKeySelective(temp) > 0) {
                return ServiceResultEnum.SUCCESS.getResult();
            }
            return ServiceResultEnum.DB_ERROR.getResult();
        }
        return ServiceResultEnum.DATA_NOT_EXIST.getResult();
    }

    @Override
    @Transactional
    public String checkDone(Long[] ids) {
        //查询所有的订单 判断状态 修改状态和更新时间
        List<FlashTicketOrder> orders = newBeeMallOrderMapper.selectByPrimaryKeys(Arrays.asList(ids));
        String errorOrderNos = "";
        if (!CollectionUtils.isEmpty(orders)) {
            for (FlashTicketOrder newBeeMallOrder : orders) {
                if (newBeeMallOrder.getIsDeleted() == 1) {
                    errorOrderNos += newBeeMallOrder.getOrderNo() + " ";
                    continue;
                }
                if (newBeeMallOrder.getOrderStatus() != 1) {
                    errorOrderNos += newBeeMallOrder.getOrderNo() + " ";
                }
            }
            if (!StringUtils.hasText(errorOrderNos)) {
                //订单状态正常 可以执行配货完成操作 修改订单状态和更新时间
                if (newBeeMallOrderMapper.checkDone(Arrays.asList(ids)) > 0) {
                    return ServiceResultEnum.SUCCESS.getResult();
                } else {
                    return ServiceResultEnum.DB_ERROR.getResult();
                }
            } else {
                //订单此时不可执行出库操作
                if (errorOrderNos.length() > 0 && errorOrderNos.length() < 100) {
                    return errorOrderNos + "订单的状态不是支付成功无法执行出库操作";
                } else {
                    return "你选择了太多状态不是支付成功的订单，无法执行配货完成操作";
                }
            }
        }
        //未查询到数据 返回错误提示
        return ServiceResultEnum.DATA_NOT_EXIST.getResult();
    }

    @Override
    @Transactional
    public String checkOut(Long[] ids) {
        //查询所有的订单 判断状态 修改状态和更新时间
        List<FlashTicketOrder> orders = newBeeMallOrderMapper.selectByPrimaryKeys(Arrays.asList(ids));
        String errorOrderNos = "";
        if (!CollectionUtils.isEmpty(orders)) {
            for (FlashTicketOrder newBeeMallOrder : orders) {
                if (newBeeMallOrder.getIsDeleted() == 1) {
                    errorOrderNos += newBeeMallOrder.getOrderNo() + " ";
                    continue;
                }
                if (newBeeMallOrder.getOrderStatus() != 1 && newBeeMallOrder.getOrderStatus() != 2) {
                    errorOrderNos += newBeeMallOrder.getOrderNo() + " ";
                }
            }
            if (!StringUtils.hasText(errorOrderNos)) {
                //订单状态正常 可以执行出库操作 修改订单状态和更新时间
                if (newBeeMallOrderMapper.checkOut(Arrays.asList(ids)) > 0) {
                    return ServiceResultEnum.SUCCESS.getResult();
                } else {
                    return ServiceResultEnum.DB_ERROR.getResult();
                }
            } else {
                //订单此时不可执行出库操作
                if (errorOrderNos.length() > 0 && errorOrderNos.length() < 100) {
                    return errorOrderNos + "订单的状态不是支付成功或配货完成无法执行出库操作";
                } else {
                    return "你选择了太多状态不是支付成功或配货完成的订单，无法执行出库操作";
                }
            }
        }
        //未查询到数据 返回错误提示
        return ServiceResultEnum.DATA_NOT_EXIST.getResult();
    }

    @Override
    @Transactional
    public String closeOrder(Long[] ids) {
        //查询所有的订单 判断状态 修改状态和更新时间
        List<FlashTicketOrder> orders = newBeeMallOrderMapper.selectByPrimaryKeys(Arrays.asList(ids));
        String errorOrderNos = "";
        if (!CollectionUtils.isEmpty(orders)) {
            for (FlashTicketOrder newBeeMallOrder : orders) {
                // isDeleted=1 一定为已关闭订单
                if (newBeeMallOrder.getIsDeleted() == 1) {
                    errorOrderNos += newBeeMallOrder.getOrderNo() + " ";
                    continue;
                }
                //已关闭或者已完成无法关闭订单
                if (newBeeMallOrder.getOrderStatus() == 4 || newBeeMallOrder.getOrderStatus() < 0) {
                    errorOrderNos += newBeeMallOrder.getOrderNo() + " ";
                }
            }
            if (!StringUtils.hasText(errorOrderNos)) {
                //订单状态正常 可以执行关闭操作 修改订单状态和更新时间
                if (newBeeMallOrderMapper.closeOrder(Arrays.asList(ids), FlashTicketOrderStatusEnum.ORDER_CLOSED_BY_JUDGE.getOrderStatus()) > 0) {
                    return ServiceResultEnum.SUCCESS.getResult();
                } else {
                    return ServiceResultEnum.DB_ERROR.getResult();
                }
            } else {
                //订单此时不可执行关闭操作
                if (errorOrderNos.length() > 0 && errorOrderNos.length() < 100) {
                    return errorOrderNos + "订单不能执行关闭操作";
                } else {
                    return "你选择的订单不能执行关闭操作";
                }
            }
        }
        //未查询到数据 返回错误提示
        return ServiceResultEnum.DATA_NOT_EXIST.getResult();
    }

    @Override
    public List<FlashTicketOrderItemVO> getOrderItems(Long orderId) {
        FlashTicketOrder newBeeMallOrder = newBeeMallOrderMapper.selectByPrimaryKey(orderId);
        if (newBeeMallOrder != null) {
            List<FlashTicketOrderItem> orderItems = newBeeMallOrderItemMapper.selectByOrderId(newBeeMallOrder.getOrderId());
            //获取订单项数据
            if (!CollectionUtils.isEmpty(orderItems)) {
                List<FlashTicketOrderItemVO> newBeeMallOrderItemVOS = BeanUtil.copyList(orderItems, FlashTicketOrderItemVO.class);
                return newBeeMallOrderItemVOS;
            }
        }
        return null;
    }
}
