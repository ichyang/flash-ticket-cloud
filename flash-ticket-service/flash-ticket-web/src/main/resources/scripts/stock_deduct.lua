-- 库存扣减 Lua 脚本（原子操作）
-- KEYS[1]: 库存 key，例如 "ticket:stock:1001"
-- ARGV[1]: 扣减数量
-- ARGV[2]: 扣减前最大允许值（兜底，同乐观锁语义）
--
-- 返回值：
--   1  → 扣减成功
--   0  → 库存不足
--  -1  → key 不存在（未预热或已过期）
--  -2  → 参数异常

local key = KEYS[1]
local delta = tonumber(ARGV[1])
local maxAllowed = tonumber(ARGV[2])

if delta == nil or delta <= 0 then
    return -2
end

local exists = redis.call('EXISTS', key)
if exists == 0 then
    return -1
end

local stock = redis.call('GET', key)
if stock == false then
    return -1
end

stock = tonumber(stock)
if stock == nil then
    return -1
end

if stock >= delta then
    -- 如果传入了兜底阈值，校验一次
    if maxAllowed ~= nil and maxAllowed > 0 and stock > maxAllowed then
        return -2
    end
    redis.call('DECRBY', key, delta)
    return 1
else
    return 0
end
