/**
 * 严肃声明：
 * 开源版本请务必保留此注释头信息，若删除我方将保留所有法律责任追究！
 * 本软件已申请软件著作权，受国家版权局知识产权以及国家计算机软件著作权保护！
 * 可正常分享和学习源码，不得用于违法犯罪活动，违者必究！
 * Copyright (c) 2022 程序员十三 all rights reserved.
 * 版权所有，侵权必究！
 */
package com.flashticket.ticket.service.impl;

import com.flashticket.common.enums.NewBeeMallCategoryLevelEnum;
import com.flashticket.common.enums.ServiceResultEnum;
import com.flashticket.common.dto.PageQueryUtil;
import com.flashticket.common.dto.PageResult;
import com.flashticket.common.exception.FlashTicketException;
import com.flashticket.common.util.BeanUtil;
import com.flashticket.ticket.controller.vo.FlashTicketSearchGoodsVO;
import com.flashticket.ticket.dao.FlashTicketCategoryMapper;
import com.flashticket.ticket.dao.FlashTicketGoodsMapper;
import com.flashticket.ticket.entity.GoodsCategory;
import com.flashticket.ticket.entity.FlashTicketGoods;
import com.flashticket.ticket.entity.StockNumDTO;
import com.flashticket.ticket.service.FlashTicketCacheManager;
import com.flashticket.ticket.service.FlashTicketRedisService;
import com.flashticket.ticket.service.FlashTicketTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class FlashTicketTicketServiceImpl implements FlashTicketTicketService {

    private static final Logger log = LoggerFactory.getLogger(FlashTicketTicketServiceImpl.class);

    @Autowired
    private FlashTicketGoodsMapper goodsMapper;
    @Autowired
    private FlashTicketCategoryMapper goodsCategoryMapper;
    @Autowired
    private FlashTicketRedisService redisService;
    @Autowired
    private FlashTicketCacheManager cacheManager;

    @Override
    public PageResult getFlashTicketGoodsPage(PageQueryUtil pageUtil) {
        List<FlashTicketGoods> goodsList = goodsMapper.findFlashTicketGoodsList(pageUtil);
        int total = goodsMapper.getTotalFlashTicketGoods(pageUtil);
        PageResult pageResult = new PageResult(goodsList, total, pageUtil.getLimit(), pageUtil.getPage());
        return pageResult;
    }

    @Override
    public String saveFlashTicketGoods(FlashTicketGoods goods) {
        GoodsCategory goodsCategory = goodsCategoryMapper.selectByPrimaryKey(goods.getGoodsCategoryId());
        // 分类不存在或者不是三级分类，则该参数字段异常
        if (goodsCategory == null || goodsCategory.getCategoryLevel().intValue() != NewBeeMallCategoryLevelEnum.LEVEL_THREE.getLevel()) {
            return ServiceResultEnum.GOODS_CATEGORY_ERROR.getResult();
        }
        if (goodsMapper.selectByCategoryIdAndName(goods.getGoodsName(), goods.getGoodsCategoryId()) != null) {
            return ServiceResultEnum.SAME_GOODS_EXIST.getResult();
        }
        if (goodsMapper.insertSelective(goods) > 0) {
            return ServiceResultEnum.SUCCESS.getResult();
        }
        return ServiceResultEnum.DB_ERROR.getResult();
    }

    @Override
    public void batchSaveFlashTicketGoods(List<FlashTicketGoods> newBeeMallGoodsList) {
        if (!CollectionUtils.isEmpty(newBeeMallGoodsList)) {
            goodsMapper.batchInsert(newBeeMallGoodsList);
        }
    }

    @Override
    public String updateFlashTicketGoods(FlashTicketGoods goods) {
        GoodsCategory goodsCategory = goodsCategoryMapper.selectByPrimaryKey(goods.getGoodsCategoryId());
        // 分类不存在或者不是三级分类，则该参数字段异常
        if (goodsCategory == null || goodsCategory.getCategoryLevel().intValue() != NewBeeMallCategoryLevelEnum.LEVEL_THREE.getLevel()) {
            return ServiceResultEnum.GOODS_CATEGORY_ERROR.getResult();
        }
        FlashTicketGoods temp = goodsMapper.selectByPrimaryKey(goods.getGoodsId());
        if (temp == null) {
            return ServiceResultEnum.DATA_NOT_EXIST.getResult();
        }
        FlashTicketGoods temp2 = goodsMapper.selectByCategoryIdAndName(goods.getGoodsName(), goods.getGoodsCategoryId());
        if (temp2 != null && !temp2.getGoodsId().equals(goods.getGoodsId())) {
            //name和分类id相同且不同id 不能继续修改
            return ServiceResultEnum.SAME_GOODS_EXIST.getResult();
        }
        goods.setUpdateTime(new Date());
        if (goodsMapper.updateByPrimaryKeySelective(goods) > 0) {
            return ServiceResultEnum.SUCCESS.getResult();
        }
        return ServiceResultEnum.DB_ERROR.getResult();
    }

    @Override
    public FlashTicketGoods getFlashTicketGoodsById(Long id) {
        FlashTicketGoods newBeeMallGoods = goodsMapper.selectByPrimaryKey(id);
        if (newBeeMallGoods == null) {
            FlashTicketException.fail(ServiceResultEnum.GOODS_NOT_EXIST.getResult());
        }
        return newBeeMallGoods;
    }

    @Override
    public List<FlashTicketGoods> getFlashTicketGoodsByIds(List<Long> goodsIds) {
        return goodsMapper.selectByPrimaryKeys(goodsIds);
    }

    @Override
    public Boolean batchUpdateSellStatus(Long[] ids, int sellStatus) {
        return goodsMapper.batchUpdateSellStatus(ids, sellStatus) > 0;
    }


    @Override
    public PageResult searchFlashTicketGoods(PageQueryUtil pageUtil) {
        List<FlashTicketGoods> goodsList = goodsMapper.findFlashTicketGoodsListBySearch(pageUtil);
        int total = goodsMapper.getTotalFlashTicketGoodsBySearch(pageUtil);
        List<FlashTicketSearchGoodsVO> newBeeMallSearchGoodsVOS = new ArrayList<>();
        if (!CollectionUtils.isEmpty(goodsList)) {
            newBeeMallSearchGoodsVOS = BeanUtil.copyList(goodsList, FlashTicketSearchGoodsVO.class);
            for (FlashTicketSearchGoodsVO newBeeMallSearchGoodsVO : newBeeMallSearchGoodsVOS) {
                String goodsName = newBeeMallSearchGoodsVO.getGoodsName();
                String goodsIntro = newBeeMallSearchGoodsVO.getGoodsIntro();
                // 字符串过长导致文字超出的问题
                if (goodsName.length() > 28) {
                    goodsName = goodsName.substring(0, 28) + "...";
                    newBeeMallSearchGoodsVO.setGoodsName(goodsName);
                }
                if (goodsIntro.length() > 30) {
                    goodsIntro = goodsIntro.substring(0, 30) + "...";
                    newBeeMallSearchGoodsVO.setGoodsIntro(goodsIntro);
                }
            }
        }
        PageResult pageResult = new PageResult(newBeeMallSearchGoodsVOS, total, pageUtil.getLimit(), pageUtil.getPage());
        return pageResult;
    }

    @Override
    public Boolean updateStockNum(List<StockNumDTO> stockNumDTOS) {
        if (stockNumDTOS == null || stockNumDTOS.isEmpty()) {
            return false;
        }

        // ===== 第 1 步：Redis Lua 前置扣减 =====
        // 先尝试 Redis 扣减，如果 Redis 有缓存则用 Lua 原子操作
        // 如果 Redis 没有缓存（key 不存在），跳过 Redis 直接走 DB
        boolean redisAllSuccess = true;
        for (StockNumDTO dto : stockNumDTOS) {
            long result = redisService.deductStock(dto.getGoodsId(), dto.getGoodsCount());
            if (result == 1) {
                // Redis 扣减成功
                log.debug("Redis 库存扣减成功: goodsId={}, count={}", dto.getGoodsId(), dto.getGoodsCount());
            } else if (result == 0) {
                // Redis 库存不足
                log.warn("Redis 库存不足: goodsId={}", dto.getGoodsId());
                redisAllSuccess = false;
                break;
            } else if (result == -1) {
                // Redis key 不存在（未预热），跳过 Redis 直接走 DB
                log.info("Redis 库存未预热，走 DB 扣减: goodsId={}", dto.getGoodsId());
                redisAllSuccess = false;
                break;
            } else {
                // 参数错误
                return false;
            }
        }

        // ===== 第 2 步：DB 乐观锁兜底 =====
        // 无论 Redis 是否成功，DB 乐观锁作为最终一致性的保障
        int affectedRows = goodsMapper.updateStockNum(stockNumDTOS);
        boolean dbSuccess = affectedRows == stockNumDTOS.size();

        if (dbSuccess) {
            // DB 扣减成功，更新本地缓存
            for (StockNumDTO dto : stockNumDTOS) {
                cacheManager.evictGoods(dto.getGoodsId());
            }
            log.info("库存扣减成功（DB 确认）: count={}", stockNumDTOS.size());
            return true;
        } else {
            // DB 扣减失败（库存不足或商品下架），需要回滚 Redis
            log.error("DB 库存扣减失败，回滚 Redis: affectedRows={}, expected={}", affectedRows, stockNumDTOS.size());
            if (redisAllSuccess) {
                // 回滚 Redis 中已扣减的库存
                for (StockNumDTO dto : stockNumDTOS) {
                    redisService.rollbackStock(dto.getGoodsId(), dto.getGoodsCount());
                }
            }
            return false;
        }
    }

    /**
     * 秒杀前调用：预热库存到 Redis
     */
    public void preheatForFlashSale(List<Long> goodsIds) {
        List<FlashTicketGoods> goodsList = goodsMapper.selectByPrimaryKeys(goodsIds);
        if (goodsList != null && !goodsList.isEmpty()) {
            redisService.preheatStock(goodsList, 7200); // 2小时 TTL
            // 预热到本地缓存
            for (FlashTicketGoods goods : goodsList) {
                cacheManager.putGoods(goods.getGoodsId(), goods);
            }
            log.info("秒杀库存预热完成，共 {} 个商品", goodsList.size());
        }
    }
}
