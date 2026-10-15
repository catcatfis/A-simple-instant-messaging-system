package com.abin.mallchat.common.mall.consumer;

import com.abin.mallchat.common.mall.constant.MallMQConstant;
import com.abin.mallchat.common.mall.domain.dto.SeckillOrderMessageDTO;
import com.abin.mallchat.common.mall.domain.entity.SeckillVoucher;
import com.abin.mallchat.common.mall.domain.entity.VoucherOrder;
import com.abin.mallchat.common.mall.mapper.SeckillVoucherMapper;
import com.abin.mallchat.common.mall.mapper.VoucherOrderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Description: 秒杀订单异步落库消费者。
 * <p>
 * 三层防重复下单，从快到慢：
 * <ol>
 *     <li>Redis Lua 脚本的 SISMEMBER 判断（前置拦截，挡住绝大多数重复请求）</li>
 *     <li>Redisson 按 userId 加锁 + 查库判断（防同一用户并发消息）</li>
 *     <li>tb_voucher_order 的 uniq_user_voucher 唯一索引（数据库最终兜底，
 *         即使前两层被绕过也只会插入失败而非产生脏数据）</li>
 * </ol>
 * 库存扣减使用 <code>stock = stock - 1 where stock &gt; 0</code> 的乐观更新，
 * 依赖数据库行锁保证不会超卖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MallMQConstant.SECKILL_ORDER_TOPIC,
        consumerGroup = MallMQConstant.SECKILL_ORDER_GROUP
)
public class SeckillOrderConsumer implements RocketMQListener<SeckillOrderMessageDTO> {

    private static final String ORDER_LOCK_KEY = "mallchat:mall:lock:order:";

    private final SeckillVoucherMapper seckillVoucherMapper;
    private final VoucherOrderMapper voucherOrderMapper;
    private final RedissonClient redissonClient;

    @Override
    public void onMessage(SeckillOrderMessageDTO message) {
        Long userId = message.getUserId();
        Long voucherId = message.getVoucherId();
        Long orderId = message.getOrderId();

        RLock lock = redissonClient.getLock(ORDER_LOCK_KEY + userId);
        boolean locked = lock.tryLock();
        if (!locked) {
            // 同一用户已有订单在处理中，直接丢弃（Lua 已保证该用户只成功一次）
            log.warn("获取下单锁失败，忽略重复消息, uid={}, voucherId={}", userId, voucherId);
            return;
        }
        try {
            createOrder(orderId, userId, voucherId);
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void createOrder(Long orderId, Long userId, Long voucherId) {
        // 一人一单：先查库，避免无谓的扣库存
        // MyBatis-Plus 3.4.0 的 selectCount 返回 Integer
        Integer existCount = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, userId)
                .eq(VoucherOrder::getVoucherId, voucherId));
        if (existCount != null && existCount > 0) {
            log.warn("用户已下过单，忽略, uid={}, voucherId={}", userId, voucherId);
            return;
        }

        // 乐观扣减库存：where stock > 0 由数据库行锁保证不超卖
        int updated = seckillVoucherMapper.update(null,
                new UpdateWrapper<SeckillVoucher>()
                        .setSql("stock = stock - 1")
                        .eq("voucher_id", voucherId)
                        .gt("stock", 0));
        if (updated <= 0) {
            log.warn("库存不足，下单失败, uid={}, voucherId={}", userId, voucherId);
            return;
        }

        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setPayType(1);
        // 1 未支付
        order.setStatus(1);
        try {
            voucherOrderMapper.insert(order);
            log.info("秒杀订单创建成功, orderId={}, uid={}, voucherId={}", orderId, userId, voucherId);
        } catch (DuplicateKeyException e) {
            // 唯一索引兜底：说明该用户已存在订单，此行不会被插入。
            // 需要把刚才扣掉的库存补回去，保持数据一致。
            log.warn("唯一索引拦截重复下单，回补库存, uid={}, voucherId={}", userId, voucherId);
            seckillVoucherMapper.update(null,
                    new UpdateWrapper<SeckillVoucher>()
                            .setSql("stock = stock + 1")
                            .eq("voucher_id", voucherId));
        }
    }
}
