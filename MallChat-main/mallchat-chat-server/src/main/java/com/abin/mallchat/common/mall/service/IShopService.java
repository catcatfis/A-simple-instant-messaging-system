package com.abin.mallchat.common.mall.service;

import com.abin.mallchat.common.mall.domain.entity.Shop;
import com.abin.mallchat.common.mall.domain.entity.ShopType;

import java.util.List;

/**
 * Description: 商户与商户类型查询服务
 */
public interface IShopService {

    /**
     * 按 id 查询商户（缓存穿透防护：数据库不存在时写空值占位）
     */
    Shop queryById(Long id);

    /**
     * 按 id 查询商户（缓存击穿防护：互斥锁方案）
     */
    Shop queryByIdWithMutex(Long id);

    /**
     * 按 id 查询商户（缓存击穿防护：逻辑过期方案，需先预热）
     */
    Shop queryByIdWithLogicalExpire(Long id);

    /**
     * 更新商户并删除缓存（Cache Aside 模式）
     */
    void update(Shop shop);

    /**
     * 按类型分页查询商户；传入经纬度时按距离由近到远排序
     *
     * @param typeId  商户类型
     * @param current 页码，从 1 开始
     * @param x       经度，可为 null
     * @param y       纬度，可为 null
     */
    List<Shop> queryByType(Integer typeId, Integer current, Double x, Double y);

    /**
     * 查询全部商户类型，按 sort 升序
     */
    List<ShopType> queryAllTypes();

    /**
     * 把数据库中有经纬度的商户按类型写入 Redis GEO 集合，
     * 使 {@link #queryByType} 的按距离排序可用。
     *
     * @return 写入的商户数量
     */
    int loadShopGeo();

    /**
     * 把商户写入逻辑过期缓存（逻辑过期方案的前置预热）
     *
     * @param id            商户id
     * @param expireSeconds 逻辑过期秒数
     */
    void saveShop2Redis(Long id, Long expireSeconds);
}
