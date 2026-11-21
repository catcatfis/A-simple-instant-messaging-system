package com.catfish.mallchat.common.common.config;

import cn.hutool.core.util.StrUtil;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description: Redisson 客户端配置
 */
@Configuration
public class RedissonConfig {
    @Autowired
    private RedisProperties redisProperties;

    /**
     * 连接超时（毫秒）：建立 TCP 连接的最长等待
     */
    private static final int CONNECT_TIMEOUT_MS = 5000;
    /**
     * 命令超时（毫秒）：单条 Redis 命令的最长等待。
     * 注意：本项目所有 Redis 操作都经由 Redisson（RedissonConnectionFactory），
     * 因此 spring.redis.timeout 是不生效的，超时必须在这里配置。
     */
    private static final int COMMAND_TIMEOUT_MS = 3000;
    /**
     * 命令失败重试次数，避免网络抖动直接抛错
     */
    private static final int RETRY_ATTEMPTS = 3;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase())
                .setConnectTimeout(CONNECT_TIMEOUT_MS)
                .setTimeout(COMMAND_TIMEOUT_MS)
                .setRetryAttempts(RETRY_ATTEMPTS);
        if (StrUtil.isNotBlank(redisProperties.getPassword())) {
            config.useSingleServer().setPassword(redisProperties.getPassword());
        }
        return Redisson.create(config);
    }
}
