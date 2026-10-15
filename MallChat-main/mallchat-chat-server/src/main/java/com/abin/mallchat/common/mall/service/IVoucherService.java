package com.abin.mallchat.common.mall.service;

import com.abin.mallchat.common.mall.domain.entity.Voucher;

import java.util.List;

/**
 * Description: 优惠券查询服务
 */
public interface IVoucherService {

    /**
     * 查询某商户的优惠券列表（含秒杀券的库存与时间窗）
     */
    List<Voucher> queryVoucherOfShop(Long shopId);

    /**
     * 查询所有上架的秒杀券
     */
    List<Voucher> querySeckillVouchers();
}
