package com.abin.mallchat.common.mall.controller;

import com.abin.mallchat.common.common.domain.vo.response.ApiResult;
import com.abin.mallchat.common.common.exception.BusinessErrorEnum;
import com.abin.mallchat.common.mall.domain.entity.Shop;
import com.abin.mallchat.common.mall.domain.entity.ShopType;
import com.abin.mallchat.common.mall.service.IShopService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Description: 商户相关接口。
 * 路径含 /public/ 的接口免登录（由 TokenInterceptor 放行）。
 */
@RestController
@RequestMapping("/capi/mall")
@Api(tags = "商城-商户")
@RequiredArgsConstructor
public class ShopController {

    private final IShopService shopService;

    @GetMapping("/public/shop/{id}")
    @ApiOperation("商户详情（空值占位防穿透）")
    public ApiResult<Shop> queryShopById(@PathVariable("id") Long id) {
        Shop shop = shopService.queryById(id);
        if (shop == null) {
            return ApiResult.fail(BusinessErrorEnum.BUSINESS_ERROR.getErrorCode(), "商户不存在");
        }
        return ApiResult.success(shop);
    }

    @GetMapping("/public/shop/{id}/mutex")
    @ApiOperation("商户详情（互斥锁防击穿，用于对比演示）")
    public ApiResult<Shop> queryShopByIdWithMutex(@PathVariable("id") Long id) {
        Shop shop = shopService.queryByIdWithMutex(id);
        if (shop == null) {
            return ApiResult.fail(BusinessErrorEnum.BUSINESS_ERROR.getErrorCode(), "商户不存在");
        }
        return ApiResult.success(shop);
    }

    @GetMapping("/public/shop/{id}/logical")
    @ApiOperation("商户详情（逻辑过期防击穿，需先预热缓存）")
    public ApiResult<Shop> queryShopByIdWithLogicalExpire(@PathVariable("id") Long id) {
        Shop shop = shopService.queryByIdWithLogicalExpire(id);
        if (shop == null) {
            return ApiResult.fail(BusinessErrorEnum.BUSINESS_ERROR.getErrorCode(), "商户不存在或缓存未预热");
        }
        return ApiResult.success(shop);
    }

    @PutMapping("/shop")
    @ApiOperation("更新商户并删除缓存")
    public ApiResult<Void> updateShop(@RequestBody Shop shop) {
        shopService.update(shop);
        return ApiResult.success();
    }

    @GetMapping("/public/shop/of/type")
    @ApiOperation("按类型分页查询商户；传经纬度则按距离由近到远排序")
    public ApiResult<List<Shop>> queryShopByType(@RequestParam("typeId") Integer typeId,
                                                 @RequestParam(value = "current", required = false, defaultValue = "1") Integer current,
                                                 @RequestParam(value = "x", required = false) Double x,
                                                 @RequestParam(value = "y", required = false) Double y) {
        return ApiResult.success(shopService.queryByType(typeId, current, x, y));
    }

    @GetMapping("/public/shop-type/list")
    @ApiOperation("查询全部商户类型")
    public ApiResult<List<ShopType>> queryAllTypes() {
        return ApiResult.success(shopService.queryAllTypes());
    }

    @GetMapping("/shop/preheat/{id}")
    @ApiOperation("把商户写入逻辑过期缓存（预热，供逻辑过期方案使用）")
    public ApiResult<Void> preheat(@PathVariable("id") Long id) {
        shopService.saveShop2Redis(id, 20L);
        return ApiResult.success();
    }

    @GetMapping("/shop/geo/init")
    @ApiOperation("把有经纬度的商户按类型导入 Redis GEO（按距离排序的前置步骤）")
    public ApiResult<Integer> initShopGeo() {
        return ApiResult.success(shopService.loadShopGeo());
    }
}
