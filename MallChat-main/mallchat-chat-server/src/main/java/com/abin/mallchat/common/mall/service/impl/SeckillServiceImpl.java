package com.abin.mallchat.common.mall.service.impl;

import com.abin.mallchat.common.common.utils.RequestHolder;
import com.abin.mallchat.common.mall.constant.MallMQConstant;
import com.abin.mallchat.common.mall.constant.MallRedisKey;
import com.abin.mallchat.common.mall.domain.dto.SeckillOrderMessageDTO;
import com.abin.mallchat.common.mall.domain.entity.SeckillVoucher;
import com.abin.mallchat.common.mall.mapper.SeckillVoucherMapper;
import com.abin.mallchat.common.mall.service.ISeckillService;
import com.abin.mallchat.common.mall.service.RedisIdWorker;
import com.abin.mallchat.transaction.service.MQProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Description: 秒杀下单实现。
 * <p>
 * 与原版 heimadianping 的差异：
 * 原版用 Redis Stream 作为异步队列，本项目改用已有的 RocketMQ（复用 MQProducer），
 * 好处是无需自己维护消费者组与 pending-list 补偿，且与项目其他异步链路统一。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements ISeckillService {

    /**
     * Lua 脚本：库存判断 + 一人一单判断 + 预扣减，保证原子性
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 脚本返回值：成功
     */
    private static final long RESULT_SUCCESS = 0L;
    /**
     * 脚本返回值：库存不足
     */
    private static final long RESULT_NO_STOCK = 1L;
    /**
     * 脚本返回值：重复下单
     */
    private static final long RESULT_DUPLICATE = 2L;

    /**
     * 库存预热后的有效期，避免长期不用的券占内存
     */
    private static final long STOCK_CACHE_DAYS = 7L;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final MQProducer mqProducer;
    private final SeckillVoucherMapper seckillVoucherMapper;

    @Override
    public Long seckillVoucher(Long voucherId) {
        Long userId = RequestHolder.get().getUid();
        long orderId = redisIdWorker.nextId("order");

        List<String> keys = Arrays.asList(
                MallRedisKey.getKey(MallRedisKey.SECKILL_STOCK_KEY, voucherId),
                MallRedisKey.getKey(MallRedisKey.SECKILL_ORDER_KEY, voucherId)
        );
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, keys,
                voucherId.toString(), userId.toString());
        if (result == null) {
            throw new IllegalStateException("秒杀脚本执行异常");
        }
        long r = result;
        if (r == RESULT_NO_STOCK) {
            throw new IllegalStateException("库存不足");
        }
        if (r == RESULT_DUPLICATE) {
            throw new IllegalStateException("不能重复下单");
        }
        if (r != RESULT_SUCCESS) {
            throw new IllegalStateException("秒杀失败");
        }

        // 预扣减成功，投递 MQ 异步落库（DB 操作不在请求线程中执行）
        mqProducer.sendMsg(MallMQConstant.SECKILL_ORDER_TOPIC,
                new SeckillOrderMessageDTO(orderId, userId, voucherId));
        log.info("秒杀预扣减成功, orderId={}, uid={}, voucherId={}", orderId, userId, voucherId);
        return orderId;
    }

    @Override
    public void warmUpStock(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherMapper.selectById(voucherId);
        if (voucher == null) {
            throw new IllegalStateException("秒杀券不存在: " + voucherId);
        }
        String stockKey = MallRedisKey.getKey(MallRedisKey.SECKILL_STOCK_KEY, voucherId);
        String orderKey = MallRedisKey.getKey(MallRedisKey.SECKILL_ORDER_KEY, voucherId);
        stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()),
                STOCK_CACHE_DAYS, TimeUnit.DAYS);
        // 清空历史下单用户，保证一人一单判断在活动维度上是干净的
        stringRedisTemplate.delete(orderKey);
        log.info("秒杀库存预热完成, voucherId={}, stock={}", voucherId, voucher.getStock());
    }
}
