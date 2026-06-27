/**
 * 严肃声明：
 * 开源版本请务必保留此注释头信息，若删除我方将保留所有法律责任追究！
 * 本软件已申请软件著作权，受国家版权局知识产权以及国家计算机软件著作权保护！
 * 可正常分享和学习源码，不得用于违法犯罪活动，违者必究！
 * Copyright (c) 2022 程序员十三 all rights reserved.
 * 版权所有，侵权必究！
 */
package com.flashticket.ticket.dao;

import com.flashticket.common.dto.PageQueryUtil;
import com.flashticket.ticket.entity.FlashTicketGoods;
import com.flashticket.ticket.entity.StockNumDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FlashTicketGoodsMapper {
    int deleteByPrimaryKey(Long goodsId);

    int insert(FlashTicketGoods record);

    int insertSelective(FlashTicketGoods record);

    FlashTicketGoods selectByPrimaryKey(Long goodsId);

    FlashTicketGoods selectByCategoryIdAndName(@Param("goodsName") String goodsName, @Param("goodsCategoryId") Long goodsCategoryId);

    int updateByPrimaryKeySelective(FlashTicketGoods record);

    int updateByPrimaryKeyWithBLOBs(FlashTicketGoods record);

    int updateByPrimaryKey(FlashTicketGoods record);

    List<FlashTicketGoods> findFlashTicketGoodsList(PageQueryUtil pageUtil);

    int getTotalFlashTicketGoods(PageQueryUtil pageUtil);

    List<FlashTicketGoods> selectByPrimaryKeys(List<Long> goodsIds);

    List<FlashTicketGoods> findFlashTicketGoodsListBySearch(PageQueryUtil pageUtil);

    int getTotalFlashTicketGoodsBySearch(PageQueryUtil pageUtil);

    int batchInsert(@Param("newBeeMallGoodsList") List<FlashTicketGoods> newBeeMallGoodsList);

    int updateStockNum(@Param("stockNumDTOS") List<StockNumDTO> stockNumDTOS);

    int batchUpdateSellStatus(@Param("orderIds")Long[] orderIds,@Param("sellStatus") int sellStatus);

}