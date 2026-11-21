package com.abin.mallchat.transaction.config;

import com.abin.mallchat.transaction.annotation.SecureInvokeConfigurer;
import com.abin.mallchat.transaction.aspect.SecureInvokeAspect;
import com.abin.mallchat.transaction.dao.SecureInvokeRecordDao;
import com.abin.mallchat.transaction.mapper.SecureInvokeRecordMapper;
import com.abin.mallchat.transaction.service.MQProducer;
import com.abin.mallchat.transaction.service.SecureInvokeService;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.CollectionUtils;
import org.springframework.util.function.SingletonSupplier;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 本地事务可靠调用（本地消息表）模块的自动配置类。
 *
 * <p>这个模块不是通过 @ComponentScan 被扫描到的，而是被引入到主启动类后，
 * 由本类把需要的 Bean 一个个注册进 Spring 容器（类似 Spring Boot Starter 的写法）：
 * <ul>
 *     <li>@EnableScheduling：开启 @Scheduled，让 SecureInvokeService.retry() 定时任务生效；</li>
 *     <li>@MapperScan：扫描 secure_invoke_record 表的 Mapper；</li>
 *     <li>@Import：把切面和数据访问层引入进来；</li>
 *     <li>@Bean：手动 new 出 SecureInvokeService 和 MQProducer。
 * </ul>
 *
 * <p>新手提示：MQProducer 并不是 @Component，而是在这里被 @Bean 注册的，
 * 所以你在业务代码里能直接 @Autowired 注入它。
 */

@Configuration
@EnableScheduling
@MapperScan(basePackageClasses = SecureInvokeRecordMapper.class)
@Import({SecureInvokeAspect.class, SecureInvokeRecordDao.class})
public class TransactionAutoConfiguration {

    @Nullable
    protected Executor executor;

    /**
     * 收集容器里的 {@link SecureInvokeConfigurer}，从中拿到重试任务用的线程池。
     * 如果没有任何自定义配置，则使用 ForkJoinPool 公共线程池。
     *
     * <p>本项目里由 ThreadPoolConfig 实现该接口，把重试任务放到专用线程池，
     * 避免和相关业务线程互相影响。
     */
    @Autowired
    void setConfigurers(ObjectProvider<SecureInvokeConfigurer> configurers) {
        Supplier<SecureInvokeConfigurer> configurer = SingletonSupplier.of(() -> {
            List<SecureInvokeConfigurer> candidates = configurers.stream().collect(Collectors.toList());
            if (CollectionUtils.isEmpty(candidates)) {
                return null;
            }
            if (candidates.size() > 1) {
                throw new IllegalStateException("Only one SecureInvokeConfigurer may exist");
            }
            return candidates.get(0);
        });
        executor = Optional.ofNullable(configurer.get()).map(SecureInvokeConfigurer::getSecureInvokeExecutor).orElse(ForkJoinPool.commonPool());
    }

    /**
     * 注册可靠调用执行器（写库 + 事务提交后执行 + 定时重试）
     */
    @Bean
    public SecureInvokeService getSecureInvokeService(SecureInvokeRecordDao dao, RedissonClient redissonClient) {
        return new SecureInvokeService(dao, executor, redissonClient);
    }

    /**
     * 注册 MQ 消息发送工具类，业务代码注入它就能发消息
     */
    @Bean
    public MQProducer getMQProducer() {
        return new MQProducer();
    }
}
