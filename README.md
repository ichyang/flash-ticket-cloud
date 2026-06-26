# 线上演出票务抢购微服务平台

基于 Spring Cloud Alibaba 的微服务架构，实现演出门票限时抢购业务。

## 项目介绍

拆分为用户、票务资源、抢购订单、消息任务四大微服务。针对开票瞬间高并发流量做架构优化，解决票源超售、数据库压力过大、服务雪崩等问题。

### 技术栈

Spring Boot / Spring Cloud Alibaba / Nacos / Sentinel / Redis / RabbitMQ / MyBatis-Plus / MySQL / Redisson / XXL-JOB

### 微服务模块

| 模块 | 说明 |
|------|------|
| flash-ticket-service | 票务资源服务（场次管理、库存管理、票价方案） |
| flash-order-service | 抢购订单服务（秒杀下单、订单流转、超时回收） |
| user-service | 用户服务（注册登录、实名认证、购票记录） |
| gateway-mall | 抢购网关（限流、鉴权、路由转发） |
| gateway-admin | 管理后台网关 |
| common | 公共模块（工具类、通用配置） |

### 核心能力

- 提前把票量缓存到 Redis，Lua 脚本保证库存扣减原子性
- Redis 分布式锁防止一票多抢
- RabbitMQ 异步生成订单，流量削峰
- Sentinel 限流保护核心抢票接口
- 定时任务自动回收超时未付款门票
- 缓存穿透、击穿、雪崩解决方案
- 订单状态机管理全生命周期
