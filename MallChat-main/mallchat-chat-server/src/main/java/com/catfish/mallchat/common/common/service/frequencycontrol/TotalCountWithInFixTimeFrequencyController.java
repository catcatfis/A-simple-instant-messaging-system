package com.catfish.mallchat.common.common.service.frequencycontrol;

import com.catfish.mallchat.common.common.domain.dto.FrequencyControlDTO;
import com.abin.mallchat.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.catfish.mallchat.common.common.service.frequencycontrol.FrequencyControlStrategyFactory.TOTAL_COUNT_WITH_IN_FIX_TIME_FREQUENCY_CONTROLLER;

/**
 * 抽象类频控服务 -使用redis实现 固定时间内不超过固定次数的限流类
 */
@Slf4j
@Service
public class TotalCountWithInFixTimeFrequencyController extends AbstractFrequencyControlService<FrequencyControlDTO> {


    /**
     * 是否达到限流阈值 子类实现 每个子类都可以自定义自己的限流逻辑判断
     *
     * @param frequencyControlMap 定义的注解频控 Map中的Key-对应redis的单个频控的Key Map中的Value-对应redis的单个频控的Key限制的Value
     * @return true-方法被限流 false-方法没有被限流
     */
    /**
     * 原子版固定窗口计数：用一段 Lua 脚本把"读计数、比较阈值、自增、设过期"合并为一次调用。
     * <p>
     * 原实现先 mget 读取再在 finally 中 inc，并发窗口内多个请求会同时读到未超限而全部放行，
     * 导致实际的限流阈值形同虚设。
     */
    @Override
    protected boolean reachRateLimit(Map<String, FrequencyControlDTO> frequencyControlMap) {
        //批量获取redis统计的值
        List<String> frequencyKeys = new ArrayList<>(frequencyControlMap.keySet());
        List<Integer> countList = RedisUtils.mget(frequencyKeys, Integer.class);
        for (int i = 0; i < frequencyKeys.size(); i++) {
            String key = frequencyKeys.get(i);
            Integer count = countList.get(i);
            int frequencyControlCount = frequencyControlMap.get(key).getCount();
            if (Objects.nonNull(count) && count >= frequencyControlCount) {
                //频率超过了
                log.warn("frequencyControl limit key:{},count:{}", key, count);
                return true;
            }
        }
        return false;
    }

    /**
     * 原子占位：判断与计数一步完成，供抽象类优先调用
     */
    @Override
    protected Boolean tryAcquireAtomically(Map<String, FrequencyControlDTO> frequencyControlMap) {
        List<String> keys = new ArrayList<>(frequencyControlMap.size());
        List<Integer> maxCounts = new ArrayList<>(frequencyControlMap.size());
        List<Long> ttlSeconds = new ArrayList<>(frequencyControlMap.size());
        frequencyControlMap.forEach((key, dto) -> {
            keys.add(key);
            maxCounts.add(dto.getCount());
            ttlSeconds.add(dto.getUnit().toSeconds(dto.getTime()));
        });
        boolean acquired = RedisUtils.tryAcquire(keys, maxCounts, ttlSeconds);
        if (!acquired) {
            log.warn("frequencyControl limit hit, keys:{}", keys);
        }
        return acquired;
    }

    /**
     * 增加限流统计次数 子类实现 每个子类都可以自定义自己的限流统计信息增加的逻辑
     *
     * @param frequencyControlMap 定义的注解频控 Map中的Key-对应redis的单个频控的Key Map中的Value-对应redis的单个频控的Key限制的Value
     */
    @Override
    protected void addFrequencyControlStatisticsCount(Map<String, FrequencyControlDTO> frequencyControlMap) {
        frequencyControlMap.forEach((k, v) -> RedisUtils.inc(k, v.getTime(), v.getUnit()));
    }

    @Override
    protected String getStrategyName() {
        return TOTAL_COUNT_WITH_IN_FIX_TIME_FREQUENCY_CONTROLLER;
    }
}
