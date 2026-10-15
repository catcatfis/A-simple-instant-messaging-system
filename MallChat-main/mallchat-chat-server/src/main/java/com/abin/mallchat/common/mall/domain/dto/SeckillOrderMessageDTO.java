package com.abin.mallchat.common.mall.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Description: 秒杀订单消息体。
 * Lua 脚本预扣减成功后，把下单信息投递到 MQ，由消费者异步创建订单。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 订单id，由 RedisIdWorker 生成
     */
    private Long orderId;

    /**
     * 下单用户id
     */
    private Long userId;

    /**
     * 优惠券id
     */
    private Long voucherId;
}
