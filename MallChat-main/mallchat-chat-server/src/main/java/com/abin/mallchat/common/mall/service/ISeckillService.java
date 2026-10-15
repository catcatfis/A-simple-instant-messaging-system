package com.abin.mallchat.common.mall.service;

/**
 * Description: 秒杀服务
 */
public interface ISeckillService {

    /**
     * 秒杀下单。
     * <p>
     * 流程：Redis Lua 原子校验（库存 + 一人一单）并预扣减 → 投递 MQ 异步落库 → 立即返回订单id。
     * 前置条件：库存必须已预热到 Redis，参见 {@link #warmUpStock(Long)}。
     *
     * @param voucherId 优惠券id
     * @return 订单id
     */
    Long seckillVoucher(Long voucherId);

    /**
     * 把秒杀券库存预热到 Redis，并清空该券的下单用户集合。
     * 上线秒杀活动前调用一次。
     */
    void warmUpStock(Long voucherId);
}
