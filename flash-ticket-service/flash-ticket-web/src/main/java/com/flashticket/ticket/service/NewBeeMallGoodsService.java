/**
 * 严肃声明：
 * 开源版本请务必保留此注释头信息，若删除我方将保留所有法律责任追究！
 * 本软件已申请软件著作权，受国家版权局知识产权以及国家计算机软件著作权保护！
 * 可正常分享和学习源码，不得用于违法犯罪活动，违者必究！
 * Copyright (c) 2022 程序员十三 all rights reserved.
 * 版权所有，侵权必究！
 */
package com.flashticket.ticket.service;

import com.flashticket.common.dto.PageQueryUtil;
import com.flashticket.common.dto.PageResult;
import com.flashticket.ticket.entity.FlashTicketGoods;
import com.flashticket.ticket.entity.StockNumDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FlashTicketTicketService {
    /**
     * 后台分页
     *
     * @param pageUtil
     * @return
     */
    PageResult getFlashTicketGoodsPage(PageQueryUtil pageUtil);

    /**
     * 添加商品
     *
     * @param goods
     * @return
     */
    String saveFlashTicketGoods(FlashTicketGoods goods);

    /**
     * 批量新增商品数据
     *
     * @param newBeeMallGoodsList
     * @return
     */
    void batchSaveFlashTicketGoods(List<FlashTicketGoods> newBeeMallGoodsList);

    /**
     * 修改商品信息
     *
     * @param goods
     * @return
     */
    String updateFlashTicketGoods(FlashTicketGoods goods);

    /**
     * 批量修改销售状态(上架下架)
     *
     * @param ids
     * @return
     */
    Boolean batchUpdateSellStatus(Long[] ids, int sellStatus);

    /**
     * 获取商品详情
     *
     * @param id
     * @return
     */
    FlashTicketGoods getFlashTicketGoodsById(Long id);

    /**
     * 获取商品数据
     *
     * @param goodsIds
     * @return
     */
    List<FlashTicketGoods> getFlashTicketGoodsByIds(List<Long> goodsIds);

    /**
     * 商品搜索
     *
     * @param pageUtil
     * @return
     */
    PageResult searchFlashTicketGoods(PageQueryUtil pageUtil);

    Boolean updateStockNum(List<StockNumDTO> stockNumDTOS);

    /**
     * 秒杀前预热库存到 Redis
     */
    void preheatForFlashSale(List<Long> goodsIds);
}
