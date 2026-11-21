package com.catfish.mallchat.common.common.service.frequencycontrol;

import com.catfish.mallchat.common.common.domain.dto.FrequencyControlDTO;
import com.catfish.mallchat.common.common.exception.CommonErrorEnum;
import com.catfish.mallchat.common.common.exception.FrequencyControlException;
import com.catfish.mallchat.common.common.utils.AssertUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 抽象类频控服务 其他类如果要实现限流服务 直接注入使用通用限流类
 * 后期会通过继承此类实现令牌桶等算法
 *
 * @see TotalCountWithInFixTimeFrequencyController 通用限流类
 */
@Slf4j
public abstract class AbstractFrequencyControlService<K extends FrequencyControlDTO> {

    @PostConstruct
    protected void registerMyselfToFactory() {
        FrequencyControlStrategyFactory.registerFrequencyController(getStrategyName(), this);
    }

    /**
     * @param frequencyControlMap 定义的注解频控 Map中的Key-对应redis的单个频控的Key Map中的Value-对应redis的单个频控的Key限制的Value
     * @param supplier            函数式入参-代表每个频控方法执行的不同的业务逻辑
     * @return 业务方法执行的返回值
     * @throws Throwable
     */
    private <T> T executeWithFrequencyControlMap(Map<String, K> frequencyControlMap, SupplierThrowWithoutParam<T> supplier) throws Throwable {
        // 优先走原子占位：判断与计数合并为一次 Redis 往返，避免"先查后记"在并发下突破阈值。
        // 返回 null 表示该实现不支持原子占位，回退到传统的判断+计数流程。
        Boolean acquired = tryAcquireAtomically(frequencyControlMap);
        if (acquired != null) {
            if (!acquired) {
                throw new FrequencyControlException(CommonErrorEnum.FREQUENCY_LIMIT);
            }
            return supplier.get();
        }
        if (reachRateLimit(frequencyControlMap)) {
            throw new FrequencyControlException(CommonErrorEnum.FREQUENCY_LIMIT);
        }
        try {
            return supplier.get();
        } finally {
            //不管成功还是失败，都增加次数
            addFrequencyControlStatisticsCount(frequencyControlMap);
        }
    }

    /**
     * 原子地"判断是否超限并计数"。
     * <p>
     * 默认返回 null 表示不支持，走"判断-执行-计数"三步流程；
     * 支持原子化的策略应重写此方法，把判断与计数合并在一次 Redis 脚本中完成。
     *
     * @param frequencyControlMap 限流定义
     * @return true-放行并已计数；false-已超限；null-不支持原子占位
     */
    protected Boolean tryAcquireAtomically(Map<String, K> frequencyControlMap) {
        return null;
    }


    /**
     * 多限流策略的编程式调用方法 无参的调用方法
     *
     * @param frequencyControlList 频控列表 包含每一个频率控制的定义以及顺序
     * @param supplier             函数式入参-代表每个频控方法执行的不同的业务逻辑
     * @return 业务方法执行的返回值
     * @throws Throwable 被限流或者限流策略定义错误
     */
    @SuppressWarnings("unchecked")
    public <T> T executeWithFrequencyControlList(List<K> frequencyControlList, SupplierThrowWithoutParam<T> supplier) throws Throwable {
        boolean existsFrequencyControlHasNullKey = frequencyControlList.stream().anyMatch(frequencyControl -> ObjectUtils.isEmpty(frequencyControl.getKey()));
        AssertUtil.isFalse(existsFrequencyControlHasNullKey, "限流策略的Key字段不允许出现空值");
        Map<String, FrequencyControlDTO> frequencyControlDTOMap = frequencyControlList.stream().collect(Collectors.groupingBy(FrequencyControlDTO::getKey, Collectors.collectingAndThen(Collectors.toList(), list -> list.get(0))));
        return executeWithFrequencyControlMap((Map<String, K>) frequencyControlDTOMap, supplier);
    }

    /**
     * 单限流策略的调用方法-编程式调用
     *
     * @param frequencyControl 单个频控对象
     * @param supplier         服务提供着
     * @return 业务方法执行结果
     * @throws Throwable
     */
    public <T> T executeWithFrequencyControl(K frequencyControl, SupplierThrowWithoutParam<T> supplier) throws Throwable {
        return executeWithFrequencyControlList(Collections.singletonList(frequencyControl), supplier);
    }


    @FunctionalInterface
    public interface SupplierThrowWithoutParam<T> {

        /**
         * Gets a result.
         *
         * @return a result
         */
        T get() throws Throwable;
    }

    @FunctionalInterface
    public interface Executor {

        /**
         * Gets a result.
         *
         * @return a result
         */
        void execute() throws Throwable;
    }

    /**
     * 是否达到限流阈值 子类实现 每个子类都可以自定义自己的限流逻辑判断
     *
     * @param frequencyControlMap 定义的注解频控 Map中的Key-对应redis的单个频控的Key Map中的Value-对应redis的单个频控的Key限制的Value
     * @return true-方法被限流 false-方法没有被限流
     */
    protected abstract boolean reachRateLimit(Map<String, K> frequencyControlMap);

    /**
     * 增加限流统计次数 子类实现 每个子类都可以自定义自己的限流统计信息增加的逻辑
     *
     * @param frequencyControlMap 定义的注解频控 Map中的Key-对应redis的单个频控的Key Map中的Value-对应redis的单个频控的Key限制的Value
     */
    protected abstract void addFrequencyControlStatisticsCount(Map<String, K> frequencyControlMap);

    /**
     * 获取策略名称
     *
     * @return 策略名称
     */
    protected abstract String getStrategyName();

}
