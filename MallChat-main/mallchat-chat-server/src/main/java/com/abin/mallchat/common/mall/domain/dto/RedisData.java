package com.abin.mallchat.common.mall.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Description: 带逻辑过期时间的数据包装。
 * 逻辑过期方案不依赖 Redis 物理 TTL，而是把过期时间写进 value，
 * 由应用判断是否过期，过期后异步重建，重建期间仍可返回旧数据。
 * <p>
 * 注意：过期时间使用 epoch 毫秒而非 LocalDateTime。
 * 缓存读写走 Hutool 的 JSONUtil，而 JSONUtil.toBean 不支持 LocalDateTime 反序列化，
 * 用 long 可避免重建缓存时抛异常。
 */
@Data
public class RedisData implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 真正的业务数据
     */
    private Object data;

    /**
     * 逻辑过期时间，epoch 毫秒
     */
    private Long expireTime;

    /**
     * 是否已逻辑过期
     */
    public boolean isExpired() {
        return expireTime == null || expireTime <= System.currentTimeMillis();
    }
}
