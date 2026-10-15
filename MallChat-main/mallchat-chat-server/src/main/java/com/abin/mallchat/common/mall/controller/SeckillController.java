package com.abin.mallchat.common.mall.controller;

import com.abin.mallchat.common.common.domain.vo.response.ApiResult;
import com.abin.mallchat.common.mall.domain.entity.VoucherOrder;
import com.abin.mallchat.common.mall.mapper.VoucherOrderMapper;
import com.abin.mallchat.common.mall.service.ISeckillService;
import com.abin.mallchat.common.common.utils.RequestHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Description: 秒杀与优惠券订单接口。
 * 下单接口需要登录（不在 /public/ 路径下）。
 */
@RestController
@RequestMapping("/capi/mall")
@Api(tags = "商城-秒杀")
@RequiredArgsConstructor
public class SeckillController {

    private final ISeckillService seckillService;
    private final VoucherOrderMapper voucherOrderMapper;

    @PostMapping("/seckill/{voucherId}")
    @ApiOperation("秒杀下单，成功返回订单id")
    public ApiResult<Long> seckill(@PathVariable("voucherId") Long voucherId) {
        return ApiResult.success(seckillService.seckillVoucher(voucherId));
    }

    @PostMapping("/seckill/warmup/{voucherId}")
    @ApiOperation("预热秒杀库存到 Redis（上线活动前调用）")
    public ApiResult<Void> warmUp(@PathVariable("voucherId") Long voucherId) {
        seckillService.warmUpStock(voucherId);
        return ApiResult.success();
    }

    @GetMapping("/order/my")
    @ApiOperation("查询我的优惠券订单")
    public ApiResult<List<VoucherOrder>> myOrders() {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(voucherOrderMapper.selectList(new LambdaQueryWrapper<VoucherOrder>()
                .eq(VoucherOrder::getUserId, uid)
                .orderByDesc(VoucherOrder::getCreateTime)));
    }
}
