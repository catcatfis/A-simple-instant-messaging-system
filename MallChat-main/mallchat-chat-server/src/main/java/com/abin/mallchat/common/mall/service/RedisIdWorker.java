package com.abin.mallchat.common.mall.service;

import com.abin.mallchat.common.mall.constant.MallRedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Description: 基于 Redis 自增的全局唯一ID生成器。
 * <p>
 * 结构：符号位(1) + 时间戳(31) + 序列号(32)，共 64 位，整体递增。
 * 优势：全局唯一、趋势递增（对 MySQL 索引友好）、不依赖数据库自增。
 * 参考 heimadianping 的 RedisIdWorker，key 前缀改为 MallChat 命名空间。
 */
@Component
@RequiredArgsConstructor
public class RedisIdWorker {

    /**
     * 起始时间戳，2022-01-01 00:00:00 UTC
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 序列号占用位数
     */
    private static final int COUNT_BITS = 32;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd");

    private final StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        // 按天分 key，避免单个 key 无限增长，也便于统计每日单量
        String date = now.format(DATE_FORMATTER);
        Long count = stringRedisTemplate.opsForValue()
                .increment(MallRedisKey.getKey(MallRedisKey.ID_WORKER_KEY, keyPrefix + ":" + date));
        if (count == null) {
            throw new IllegalStateException("生成全局唯一ID失败, prefix=" + keyPrefix);
        }
        return timestamp << COUNT_BITS | count;
    }
}
