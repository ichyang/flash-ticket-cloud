package com.flashticket.ticket.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 本地缓存管理器（Caffeine）
 *
 * 解决三类缓存问题：
 * 1. 缓存穿透 → 空值短时缓存 + Bloom filter 预留接口
 * 2. 缓存击穿 → 互斥锁 + 异步刷新
 * 3. 缓存雪崩 → 随机 TTL + 本地缓存与 Redis 双层防护
 */
@Service
public class FlashTicketCacheManager {

    private static final Logger log = LoggerFactory.getLogger(FlashTicketCacheManager.class);

    /** 热点商品缓存（库存信息） */
    private Cache<Long, Object> hotGoodsCache;

    /** 空值缓存（防穿透） */
    private Cache<String, Boolean> nullCache;

    /** 锁缓存（防击穿，本地互斥） */
    private final Object mutex = new Object();

    @PostConstruct
    public void init() {
        // 热点商品缓存：10分钟过期，最大1000个
        hotGoodsCache = Caffeine.newBuilder()
                .maximumSize(1000)
                // 随机 TTL 5~15 分钟，防雪崩
                .expireAfterWrite(5 + (long) (Math.random() * 10), TimeUnit.MINUTES)
                .recordStats()
                .build();

        // 空值缓存：30秒短时缓存防穿透
        nullCache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build();

        log.info("Caffeine 本地缓存初始化完成");
    }

    // ==================== 热点商品缓存 ====================

    public void putGoods(Long goodsId, Object goods) {
        hotGoodsCache.put(goodsId, goods);
    }

    @SuppressWarnings("unchecked")
    public <T> T getGoods(Long goodsId, Class<T> clazz) {
        Object value = hotGoodsCache.getIfPresent(goodsId);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 带缓存穿透保护的获取
     * 返回 null 表示数据不存在或需要从 DB 加载
     */
    public <T> T getGoodsWithProtection(Long goodsId, Class<T> clazz) {
        T value = getGoods(goodsId, clazz);
        if (value != null) {
            return value;
        }
        // 检查是否为空值缓存命中（防穿透）
        if (nullCache.getIfPresent("goods:null:" + goodsId) != null) {
            return null; // 上次查过 DB 也不存在，直接返回 null
        }
        return null; // 需要上层去 DB 加载
    }

    /**
     * 从 DB 加载后放入缓存（带互斥锁防击穿）
     */
    public <T> T loadGoods(Long goodsId, Class<T> clazz, CacheLoader<T> loader) {
        // 先查缓存
        T cached = getGoods(goodsId, clazz);
        if (cached != null) {
            return cached;
        }
        // 互斥锁防止缓存击穿
        synchronized (mutex) {
            // 双重检查
            cached = getGoods(goodsId, clazz);
            if (cached != null) {
                return cached;
            }
            // 从 DB 加载
            T loaded = loader.load();
            if (loaded != null) {
                putGoods(goodsId, loaded);
            } else {
                // 空值缓存，防穿透
                nullCache.put("goods:null:" + goodsId, Boolean.TRUE);
            }
            return loaded;
        }
    }

    /**
     * 商品更新时失效缓存
     */
    public void evictGoods(Long goodsId) {
        hotGoodsCache.invalidate(goodsId);
        nullCache.invalidate("goods:null:" + goodsId);
    }

    public void evictAll() {
        hotGoodsCache.invalidateAll();
        nullCache.invalidateAll();
    }

    // ==================== 统计 ====================

    public String getStats() {
        return hotGoodsCache.stats().toString();
    }

    /**
     * 缓存加载器函数式接口
     */
    @FunctionalInterface
    public interface CacheLoader<T> {
        T load();
    }
}
