package com.catfish.mallchat.common.common.config;

import com.catfish.mallchat.common.common.factory.MyThreadFactory;
import com.abin.mallchat.transaction.annotation.SecureInvokeConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置。
 *
 * <p>本项目用到三个线程池：
 * <ul>
 *     <li>{@code mallchatExecutor}：通用线程池，同时充当 @Async 默认线程池
 *         和“可靠消息重试”线程池（因为它实现了 SecureInvokeConfigurer）；</li>
 *     <li>{@code websocketExecutor}：专门用于给 WebSocket 连接写消息，队列大但满了就直接丢弃，
 *         因为推送晚到/丢失比阻塞业务线程更能接受；</li>
 *     <li>{@code aichatExecutor}：AI 回复用，AI 耗时不可控，不能让它们占满通用线程池。</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig implements AsyncConfigurer, SecureInvokeConfigurer {
    /**
     * 项目共用线程池
     */
    public static final String MALLCHAT_EXECUTOR = "mallchatExecutor";
    /**
     * websocket通信线程池
     */
    public static final String WS_EXECUTOR = "websocketExecutor";


    public static final String AICHAT_EXECUTOR = "aichatExecutor";

    /**
     * @Async 注解默认使用的线程池
     */
    @Override
    public Executor getAsyncExecutor() {
        return mallchatExecutor();
    }

    /**
     * 提供给本地消息表模块（SecureInvokeService）的重试线程池。
     * 返回 null 时会用 ForkJoinPool，这里显式复用通用线程池。
     */
    @Override
    public Executor getSecureInvokeExecutor() {
        return mallchatExecutor();
    }

    @Bean(MALLCHAT_EXECUTOR)
    @Primary
    public ThreadPoolTaskExecutor mallchatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mallchat-executor-");
        // 队列满了由调用线程自己执行，绝不丢任务（可靠消息重试不能丢）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());//满了调用线程执行，认为重要任务
        executor.setThreadFactory(new MyThreadFactory(executor));
        executor.initialize();
        return executor;
    }

    @Bean(WS_EXECUTOR)
    public ThreadPoolTaskExecutor websocketExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(16);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(1000);//支持同时推送1000人
        executor.setThreadNamePrefix("websocket-executor-");
        // 队列满了直接丢弃：推送属于可丢弃的实时消息，不能让堆积拖垮服务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());//满了直接丢弃，默认为不重要消息推送
        executor.setThreadFactory(new MyThreadFactory(executor));
        executor.initialize();
        return executor;
    }

    @Bean(AICHAT_EXECUTOR)
    public ThreadPoolTaskExecutor chatAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(15);
        executor.setThreadNamePrefix("aichat-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());//满了直接丢弃，默认为不重要消息推送
        executor.setThreadFactory(new MyThreadFactory(executor));
        return executor;
    }
}
