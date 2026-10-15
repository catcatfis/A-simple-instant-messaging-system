package com.abin.mallchat.common.mall.service.impl;

import com.abin.mallchat.common.mall.domain.entity.SeckillVoucher;
import com.abin.mallchat.common.mall.domain.entity.Voucher;
import com.abin.mallchat.common.mall.mapper.SeckillVoucherMapper;
import com.abin.mallchat.common.mall.mapper.VoucherMapper;
import com.abin.mallchat.common.mall.service.IVoucherService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Description: 优惠券查询实现。
 * 秒杀券的库存与时间窗存放在 tb_seckill_voucher，查询时批量回填，避免 N+1。
 */
@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements IVoucherService {

    /**
     * 优惠券类型：秒杀券
     */
    private static final int TYPE_SECKILL = 1;
    /**
     * 状态：上架
     */
    private static final int STATUS_ON_SALE = 1;

    private final VoucherMapper voucherMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;

    @Override
    public List<Voucher> queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getShopId, shopId)
                .eq(Voucher::getStatus, STATUS_ON_SALE));
        fillSeckillInfo(vouchers);
        return vouchers;
    }

    @Override
    public List<Voucher> querySeckillVouchers() {
        List<Voucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getType, TYPE_SECKILL)
                .eq(Voucher::getStatus, STATUS_ON_SALE));
        fillSeckillInfo(vouchers);
        return vouchers;
    }

    /**
     * 批量回填秒杀券的库存与时间窗
     */
    private void fillSeckillInfo(List<Voucher> vouchers) {
        if (vouchers == null || vouchers.isEmpty()) {
            return;
        }
        List<Long> seckillIds = vouchers.stream()
                .filter(v -> v.getType() != null && v.getType() == TYPE_SECKILL)
                .map(Voucher::getId)
                .collect(Collectors.toList());
        if (seckillIds.isEmpty()) {
            return;
        }
        List<SeckillVoucher> seckillVouchers = seckillVoucherMapper.selectBatchIds(seckillIds);
        Map<Long, SeckillVoucher> map = seckillVouchers == null ? Collections.emptyMap()
                : seckillVouchers.stream().collect(Collectors.toMap(SeckillVoucher::getVoucherId, Function.identity()));
        vouchers.forEach(v -> {
            SeckillVoucher sv = map.get(v.getId());
            if (sv != null) {
                v.setStock(sv.getStock());
                v.setBeginTime(sv.getBeginTime());
                v.setEndTime(sv.getEndTime());
            }
        });
    }
}
