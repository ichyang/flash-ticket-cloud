package com.flashticket.order.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式锁配置
 *
 * 用于秒杀场景下的防重复下单：
 * - 同一用户对同一商品在锁有效期内只能下一单
 * - Watchdog 机制自动续期，防止业务未完成锁就过期
 */
@Configuration
public class RedissonConfig {

    private static final Logger log = LoggerFactory.getLogger(RedissonConfig.class);

    @Value("${spring.redis.host:127.0.0.1}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:13}")
    private int database;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 单节点模式
        String address = "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setDatabase(database)
                .setConnectionPoolSize(16)
                .setConnectionMinimumIdleSize(4)
                // Watchdog 超时，默认 30 秒
                .setTimeout(5000)
                .setRetryAttempts(3);

        RedissonClient client = Redisson.create(config);
        log.info("Redisson 客户端初始化完成: address={}, database={}", address, database);
        return client;
    }

    // ==================== 锁 Key 常量 ====================

    /**
     * 下单锁前缀
     * 粒度：每个用户 + 每个商品
     * 防止同一用户对同一商品重复下单
     */
    public static final String ORDER_LOCK_PREFIX = "flash:lock:order:";

    /**
     * 库存扣减锁前缀
     * 粒度：每个商品
     * 注意：Redis Lua 已经保证原子性，这个锁是额外兜底
     */
    public static final String STOCK_LOCK_PREFIX = "flash:lock:stock:";

    /**
     * 构建下单锁 Key
     */
    public static String buildOrderLockKey(Long userId, Long goodsId) {
        return ORDER_LOCK_PREFIX + userId + ":" + goodsId;
    }

    /**
     * 构建库存锁 Key
     */
    public static String buildStockLockKey(Long goodsId) {
        return STOCK_LOCK_PREFIX + goodsId;
    }
}
