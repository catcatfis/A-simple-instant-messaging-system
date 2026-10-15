package com.abin.mallchat.common.mall.constant;

/**
 * Description: 商城模块 Redis key 常量。
 * 统一使用 mallchat:mall: 前缀，与聊天域 key 隔离。
 * <p>
 * 约定：需要拼接 id 的常量必须带 %s 占位符（与 MallChat 原生的 RedisKey 约定一致），
 * 否则 String.format 会忽略参数，导致 key 缺少 id 而发生缓存读写错位。
 */
public class MallRedisKey {
    private static final String BASE_KEY = "mallchat:mall:";

    /**
     * 商户缓存前缀。用于交给 MallCacheClient 自行拼接 id 的场景，
     * 注意它不带占位符，不能传给 getKey（否则 String.format 会因缺少参数而报错）
     */
    public static final String CACHE_SHOP_PREFIX = "cache:shop:";

    /**
     * 商户缓存完整 key，形如 mallchat:mall:cache:shop:1
     */
    public static final String CACHE_SHOP_KEY = "cache:shop:%s";

    /**
     * 商户缓存重建用的互斥锁，形如 mallchat:mall:lock:shop:1
     */
    public static final String LOCK_SHOP_KEY = "lock:shop:%s";

    /**
     * 秒杀库存，形如 mallchat:mall:seckill:stock:5
     */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:%s";

    /**
     * 秒杀下单用户集合，形如 mallchat:mall:seckill:order:5，用于一人一单
     */
    public static final String SECKILL_ORDER_KEY = "seckill:order:%s";

    /**
     * 按类型存放的商户 GEO 集合，形如 mallchat:mall:shop:geo:1
     */
    public static final String SHOP_GEO_KEY = "shop:geo:%s";

    /**
     * 全局唯一ID的自增序列，形如 mallchat:mall:icr:order:2026:09:20
     */
    public static final String ID_WORKER_KEY = "icr:%s";

    public static String getKey(String key, Object... objects) {
        return BASE_KEY + String.format(key, objects);
    }
}
