package com.abin.mallchat.common.mall.service.cache;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.abin.mallchat.common.mall.constant.MallRedisKey;
import com.abin.mallchat.common.mall.domain.dto.RedisData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Description: 缓存工具类，覆盖缓存的三大经典问题。
 * <p>
 * <ul>
 *     <li>{@link #queryWithPassThrough} 缓存穿透：数据库也不存在时写入空串占位，避免请求反复打到 DB</li>
 *     <li>{@link #queryWithMutex} 缓存击穿：重建时加互斥锁，只放一个请求进 DB，其余短暂等待后重试</li>
 *     <li>{@link #queryWithLogicalExpire} 缓存击穿（逻辑过期）：不设物理 TTL，靠数据内的过期时间判断，
 *         过期后异步重建，重建期间仍返回旧数据，保证高可用</li>
 * </ul>
 * 参考 heimadianping 的 CacheClient 实现，key 前缀改为 MallChat 的 mallchat:mall: 命名空间。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MallCacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 逻辑过期方案下用于异步重建缓存的线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 空值占位的过期时间（分钟）
     */
    private static final long NULL_TTL_MINUTES = 2L;

    /**
     * 互斥锁的过期时间（秒）
     */
    private static final long LOCK_TTL_SECONDS = 10L;

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 写入带逻辑过期时间的数据（不设置 Redis 物理过期）
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // 使用 epoch 毫秒，避免 LocalDateTime 在 Hutool 反序列化时失败
        redisData.setExpireTime(System.currentTimeMillis() + unit.toMillis(time));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存穿透防护
     *
     * @param keyPrefix 缓存 key 前缀
     * @param id        业务 id
     * @param type      返回类型
     * @param dbFallback 回源查询
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                         Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 命中真实数据
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 命中空值占位，直接返回 null，不再打 DB
        if (json != null) {
            return null;
        }
        // 回源
        R r = dbFallback.apply(id);
        if (r == null) {
            // 数据库也没有：写空串占位，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", NULL_TTL_MINUTES, TimeUnit.MINUTES);
            return null;
        }
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 缓存击穿防护（互斥锁方案）
     * 优点：实现简单、能保证一致性；缺点：未拿到锁的请求需要等待重试
     */
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type,
                                   Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        String lockKey = MallRedisKey.getKey(MallRedisKey.LOCK_SHOP_KEY, id);
        R r;
        try {
            if (!tryLock(lockKey)) {
                // 没抢到锁：短暂休眠后重试，避免同时打 DB
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 双重检查：等锁期间可能已被其他线程重建
            String again = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(again)) {
                return JSONUtil.toBean(again, type);
            }
            r = dbFallback.apply(id);
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", NULL_TTL_MINUTES, TimeUnit.MINUTES);
                return null;
            }
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return r;
    }

    /**
     * 缓存击穿防护（逻辑过期方案）
     * 优点：永不阻塞、可用性高；缺点：会短暂返回旧数据，牺牲强一致
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                           Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            // 逻辑过期方案要求缓存必须预热，未命中直接返回空
            return null;
        }
        RedisData redisData;
        R r;
        try {
            redisData = JSONUtil.toBean(json, RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        } catch (Exception e) {
            log.error("逻辑过期缓存反序列化失败, key={}", key, e);
            return null;
        }
        if (!redisData.isExpired()) {
            return r;
        }
        // 已逻辑过期：尝试异步重建，同时立刻返回旧数据
        String lockKey = MallRedisKey.getKey(MallRedisKey.LOCK_SHOP_KEY, id);
        if (tryLock(lockKey)) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R newR = dbFallback.apply(id);
                    if (newR != null) {
                        this.setWithLogicalExpire(key, newR, time, unit);
                    }
                } catch (Exception e) {
                    log.error("缓存重建失败, key={}", key, e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_TTL_SECONDS, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
