package com.flashticket.ticket.service;

import com.flashticket.ticket.entity.FlashTicketGoods;
import com.flashticket.ticket.entity.StockNumDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Redis 库存管理服务
 * 提供库存预热、Lua 原子扣减、库存回滚等能力
 */
@Service
public class FlashTicketRedisService {

    private static final Logger log = LoggerFactory.getLogger(FlashTicketRedisService.class);

    /** 库存 key 前缀 */
    private static final String STOCK_KEY_PREFIX = "ticket:stock:";

    /** 库存锁 key 前缀（用于分段库存） */
    private static final String STOCK_LOCK_PREFIX = "ticket:lock:";

    /** Lua 脚本：库存扣减 */
    private DefaultRedisScript<Long> stockDeductScript;

    @Autowired
    private RedisTemplate<String, Serializable> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    public void init() {
        // 加载 Lua 脚本
        stockDeductScript = new DefaultRedisScript<>();
        stockDeductScript.setLocation(new ClassPathResource("scripts/stock_deduct.lua"));
        stockDeductScript.setResultType(Long.class);
        log.info("Redis 库存 Lua 脚本加载完成");
    }

    // ==================== 库存预热 ====================

    /**
     * 批量预热库存到 Redis（秒杀开始前调用）
     *
     * @param goodsList 商品列表
     * @param ttlSeconds TTL 秒数，默认 3600
     */
    public void preheatStock(List<FlashTicketGoods> goodsList, long ttlSeconds) {
        if (goodsList == null || goodsList.isEmpty()) {
            return;
        }
        Map<String, String> stockMap = new HashMap<>(goodsList.size());
        for (FlashTicketGoods goods : goodsList) {
            if (goods.getGoodsId() != null && goods.getStockNum() != null) {
                stockMap.put(buildStockKey(goods.getGoodsId()), String.valueOf(goods.getStockNum()));
            }
        }
        stringRedisTemplate.opsForValue().multiSet(stockMap);
        // 逐 key 设置 TTL（multiSet 不支持批量 TTL）
        for (String key : stockMap.keySet()) {
            stringRedisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        log.info("库存预热完成，共 {} 个商品", stockMap.size());
    }

    /**
     * 单个商品库存预热
     */
    public void preheatSingleStock(Long goodsId, Integer stockNum, long ttlSeconds) {
        String key = buildStockKey(goodsId);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(stockNum), ttlSeconds, TimeUnit.SECONDS);
    }

    // ==================== 库存扣减（Lua 原子操作） ====================

    /**
     * 使用 Lua 脚本原子扣减库存
     *
     * @param goodsId 商品 ID
     * @param count   扣减数量
     * @return 1=成功, 0=库存不足, -1=key 不存在, -2=参数错误
     */
    public long deductStock(Long goodsId, int count) {
        String key = buildStockKey(goodsId);
        List<String> keys = Collections.singletonList(key);
        // 从数据库加载最大库存作为兜底校验（防止 Redis 数据异常）
        // 第三个参数 args[2] 为 0 表示不启用兜底校验
        Long result = stringRedisTemplate.execute(stockDeductScript, keys,
                String.valueOf(count), "0");
        return result == null ? -1 : result;
    }

    // ==================== 库存回滚 ====================

    /**
     * 库存回滚（订单超时取消时调用）
     */
    public void rollbackStock(Long goodsId, int count) {
        String key = buildStockKey(goodsId);
        stringRedisTemplate.opsForValue().increment(key, count);
        log.info("库存回滚: goodsId={}, count={}", goodsId, count);
    }

    // ==================== 库存查询 ====================

    /**
     * 查询 Redis 中的实时库存
     */
    public Integer getStock(Long goodsId) {
        String key = buildStockKey(goodsId);
        String value = stringRedisTemplate.opsForValue().get(key);
        return value == null ? null : Integer.parseInt(value);
    }

    /**
     * 批量查询库存
     */
    public Map<Long, Integer> getStockBatch(List<Long> goodsIds) {
        List<String> keys = goodsIds.stream()
                .map(this::buildStockKey)
                .collect(Collectors.toList());
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<Long, Integer> result = new HashMap<>(goodsIds.size());
        if (values != null) {
            for (int i = 0; i < goodsIds.size(); i++) {
                String v = values.get(i);
                if (v != null) {
                    result.put(goodsIds.get(i), Integer.parseInt(v));
                }
            }
        }
        return result;
    }

    // ==================== 库存 Key 管理 ====================

    public String buildStockKey(Long goodsId) {
        return STOCK_KEY_PREFIX + goodsId;
    }

    /**
     * 删除库存缓存（商品下架或秒杀结束后清理）
     */
    public void deleteStock(Long goodsId) {
        stringRedisTemplate.delete(buildStockKey(goodsId));
    }

    public void deleteStockBatch(List<Long> goodsIds) {
        List<String> keys = goodsIds.stream()
                .map(this::buildStockKey)
                .collect(Collectors.toList());
        stringRedisTemplate.delete(keys);
    }
}
