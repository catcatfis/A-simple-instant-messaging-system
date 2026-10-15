package com.abin.mallchat.common.mall.constant;

/**
 * Description: 商城模块 RocketMQ 常量。
 * <p>
 * 秒杀下单采用「Redis Lua 预扣减 + MQ 异步落库」：
 * Lua 脚本保证原子性与一人一单，DB 写操作通过 MQ 异步执行，削峰填谷。
 */
public interface MallMQConstant {

    /**
     * 秒杀订单创建 topic。
     * 使用 CLUSTERING 集群模式：同一订单只需被消费一次（区别于聊天消息的 BROADCASTING）
     */
    String SECKILL_ORDER_TOPIC = "mall_seckill_order";

    /**
     * 秒杀订单消费组
     */
    String SECKILL_ORDER_GROUP = "mall_seckill_order_group";
}
