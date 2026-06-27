package com.flashticket.order.service;

import com.flashticket.order.config.RedissonConfig;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务
 *
 * 基于 Redisson 实现，用于秒杀场景：
 * 1. 防止同一用户重复下单（幂等）
 * 2. 防止缓存击穿（热点 key 重建互斥）
 * 3. Watchdog 自动续期
 */
@Service
public class FlashDistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(FlashDistributedLockService.class);

    /** 默认等待时间（获取锁的超时） */
    private static final long DEFAULT_WAIT_TIME = 3;

    /** 默认锁持有时间（0 = 启用 Watchdog 自动续期） */
    private static final long DEFAULT_LEASE_TIME = 30;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 尝试获取下单锁（防重复下单）
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return 是否成功获取锁
     */
    public boolean tryOrderLock(Long userId, Long goodsId) {
        String lockKey = RedissonConfig.buildOrderLockKey(userId, goodsId);
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // waitTime=3秒, leaseTime=30秒(Watchdog自动续期)
            boolean acquired = lock.tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
            if (acquired) {
                log.debug("获取下单锁成功: userId={}, goodsId={}", userId, goodsId);
            } else {
                log.warn("获取下单锁失败，可能重复下单: userId={}, goodsId={}", userId, goodsId);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取下单锁被中断: userId={}, goodsId={}", userId, goodsId, e);
            return false;
        }
    }

    /**
     * 释放下单锁
     */
    public void unlockOrderLock(Long userId, Long goodsId) {
        String lockKey = RedissonConfig.buildOrderLockKey(userId, goodsId);
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放下单锁: userId={}, goodsId={}", userId, goodsId);
            }
        } catch (Exception e) {
            log.error("释放下单锁异常: userId={}, goodsId={}", userId, goodsId, e);
        }
    }

    /**
     * 带回调的锁执行（推荐用法）
     *
     * @param userId   用户ID
     * @param goodsId  商品ID
     * @param callback 回调函数
     * @param <T>      返回类型
     * @return 回调结果，获取锁失败返回 null
     */
    public <T> T executeWithOrderLock(Long userId, Long goodsId, LockCallback<T> callback) {
        String lockKey = RedissonConfig.buildOrderLockKey(userId, goodsId);
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean acquired = lock.tryLock(DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("获取锁失败，请勿重复下单: userId={}, goodsId={}", userId, goodsId);
                return null;
            }
            log.debug("获取锁成功: key={}", lockKey);
            return callback.execute();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("锁执行被中断", e);
            return null;
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            } catch (Exception e) {
                log.error("释放锁异常", e);
            }
        }
    }

    /**
     * 锁回调接口
     */
    @FunctionalInterface
    public interface LockCallback<T> {
        T execute();
    }
}
