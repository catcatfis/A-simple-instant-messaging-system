-- ============================================================
-- 秒杀下单原子脚本
-- 在 Redis 单线程中一次性完成「库存判断 + 一人一单判断 + 扣减 + 记录用户」，
-- 避免并发超卖与重复下单。
--
-- KEYS[1] 库存 key   mallchat:mall:seckill:stock:{voucherId}
-- KEYS[2] 下单用户集合 key  mallchat:mall:seckill:order:{voucherId}
-- ARGV[1] voucherId
-- ARGV[2] userId
--
-- 返回：0 成功；1 库存不足；2 重复下单
-- 说明：库存 key 不存在视为售罄（而非无限库存），避免缓存未预热时被刷
-- ============================================================
local stockKey = KEYS[1]
local orderKey = KEYS[2]
local voucherId = ARGV[1]
local userId = ARGV[2]

local stock = redis.call('get', stockKey)
if (not stock or tonumber(stock) <= 0) then
    return 1
end

if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)
return 0
