package com.abin.mallchat.common.mall.controller;

import com.abin.mallchat.common.common.domain.vo.response.ApiResult;
import com.abin.mallchat.common.mall.domain.entity.Voucher;
import com.abin.mallchat.common.mall.service.IVoucherService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Description: 优惠券相关接口
 */
@RestController
@RequestMapping("/capi/mall")
@Api(tags = "商城-优惠券")
@RequiredArgsConstructor
public class VoucherController {

    private final IVoucherService voucherService;

    @GetMapping("/public/voucher/of/shop")
    @ApiOperation("查询商户的优惠券列表")
    public ApiResult<List<Voucher>> queryVoucherOfShop(@RequestParam("shopId") Long shopId) {
        return ApiResult.success(voucherService.queryVoucherOfShop(shopId));
    }

    @GetMapping("/public/voucher/seckill/list")
    @ApiOperation("查询所有上架的秒杀券")
    public ApiResult<List<Voucher>> querySeckillVouchers() {
        return ApiResult.success(voucherService.querySeckillVouchers());
    }
}
