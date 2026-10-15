package com.abin.mallchat.common.mall.service.impl;

import cn.hutool.core.util.StrUtil;
import com.abin.mallchat.common.mall.constant.MallRedisKey;
import com.abin.mallchat.common.mall.domain.entity.Shop;
import com.abin.mallchat.common.mall.domain.entity.ShopType;
import com.abin.mallchat.common.mall.mapper.ShopMapper;
import com.abin.mallchat.common.mall.mapper.ShopTypeMapper;
import com.abin.mallchat.common.mall.service.IShopService;
import com.abin.mallchat.common.mall.service.cache.MallCacheClient;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Description: 商户查询实现。
 * <p>
 * 三种缓存方案都在此暴露入口，便于对比验证：
 * <ul>
 *     <li>默认 {@link #queryById} 用空值占位防穿透</li>
 *     <li>{@link #queryByIdWithMutex} 用互斥锁防击穿</li>
 *     <li>{@link #queryByIdWithLogicalExpire} 用逻辑过期防击穿（高可用，允许短暂脏读）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements IShopService {

    private static final long SHOP_CACHE_MINUTES = 30L;
    private static final long SHOP_LOGICAL_EXPIRE_SECONDS = 20L;
    private static final int DEFAULT_PAGE_SIZE = 5;
    /**
     * 按距离查询的半径（米）
     */
    private static final double GEO_SEARCH_RADIUS = 5000D;

    private final ShopMapper shopMapper;
    private final ShopTypeMapper shopTypeMapper;
    private final MallCacheClient cacheClient;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Shop queryById(Long id) {
        // CACHE_SHOP_PREFIX 不带占位符，由 MallCacheClient 内部拼接 id
        return cacheClient.queryWithPassThrough(MallRedisKey.getKey(MallRedisKey.CACHE_SHOP_PREFIX),
                id, Shop.class, shopMapper::selectById, SHOP_CACHE_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public Shop queryByIdWithMutex(Long id) {
        return cacheClient.queryWithMutex(MallRedisKey.getKey(MallRedisKey.CACHE_SHOP_PREFIX),
                id, Shop.class, shopMapper::selectById, SHOP_CACHE_MINUTES, TimeUnit.MINUTES);
    }

    @Override
    public Shop queryByIdWithLogicalExpire(Long id) {
        return cacheClient.queryWithLogicalExpire(MallRedisKey.getKey(MallRedisKey.CACHE_SHOP_PREFIX),
                id, Shop.class, shopMapper::selectById, SHOP_LOGICAL_EXPIRE_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void update(Shop shop) {
        if (shop == null || shop.getId() == null) {
            throw new IllegalArgumentException("店铺id不能为空");
        }
        // Cache Aside：先更新数据库，再删除缓存
        shopMapper.updateById(shop);
        stringRedisTemplate.delete(MallRedisKey.getKey(MallRedisKey.CACHE_SHOP_KEY, shop.getId()));
    }

    @Override
    public List<Shop> queryByType(Integer typeId, Integer current, Double x, Double y) {
        if (typeId == null) {
            throw new IllegalArgumentException("商户类型不能为空");
        }
        int page = current == null || current < 1 ? 1 : current;
        // 无坐标：直接分页查库
        if (x == null || y == null) {
            return queryPageByType(typeId, page);
        }

        // 有坐标：走 Redis GEO，按距离由近到远取
        int from = (page - 1) * DEFAULT_PAGE_SIZE;
        int end = page * DEFAULT_PAGE_SIZE;
        String geoKey = MallRedisKey.getKey(MallRedisKey.SHOP_GEO_KEY, typeId);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results;
        try {
            // 注意：这里必须用 geoRadius，不能用 Spring Data 的 GEOSEARCH(search)：
            // 本项目 Redis 客户端是 Redisson 3.17.1，redisson-spring-data-26 只实现了 geoRadius，
            // 未实现 geoSearch，而 DefaultedRedisConnection.geoSearch 在缺实现时会递归调用自己，
            // 最终抛 StackOverflowError（Error 而非 Exception，无法用 catch(Exception) 兜住）。
            results = stringRedisTemplate.opsForGeo().radius(
                    geoKey,
                    new Circle(new Point(x, y), new Distance(GEO_SEARCH_RADIUS)),
                    RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end));
        } catch (Exception e) {
            // GEO 集合尚未初始化（key 不存在）等情况降级为普通分页，保证接口可用
            log.warn("GEO 查询失败，降级为普通分页, typeId={}, geoKey={}, cause={}", typeId, geoKey, e.getMessage());
            return queryPageByType(typeId, page);
        }
        if (results == null) {
            return queryPageByType(typeId, page);
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = new ArrayList<>(results.getContent());
        if (list.isEmpty()) {
            // 该类型下没有任何 GEO 数据（例如全部商户都没填经纬度），同样降级，避免前端拿到空列表
            return queryPageByType(typeId, page);
        }
        if (list.size() <= from) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(r -> {
            String shopIdStr = r.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            distanceMap.put(shopIdStr, r.getDistance());
        });
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 保持 GEO 返回的距离顺序（DB 的 in 查询不保证顺序，用 ORDER BY FIELD 还原）
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .in(Shop::getId, ids)
                .last("ORDER BY FIELD(id," + idStr + ")"));
        shops.forEach(s -> {
            Distance d = distanceMap.get(s.getId().toString());
            if (d != null) {
                s.setDistance(d.getValue());
            }
        });
        return shops;
    }

    @Override
    public List<ShopType> queryAllTypes() {
        return shopTypeMapper.selectList(new LambdaQueryWrapper<ShopType>().orderByAsc(ShopType::getSort));
    }

    /**
     * 普通分页查询，GEO 不可用时作为降级路径
     */
    private List<Shop> queryPageByType(Integer typeId, int page) {
        Page<Shop> p = shopMapper.selectPage(new Page<>(page, DEFAULT_PAGE_SIZE),
                new LambdaQueryWrapper<Shop>().eq(Shop::getTypeId, typeId));
        return p.getRecords();
    }

    @Override
    public int loadShopGeo() {
        List<Shop> shops = shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .isNotNull(Shop::getX)
                .isNotNull(Shop::getY));
        if (shops.isEmpty()) {
            return 0;
        }
        // 按类型分组，一次 GEOADD 批量写入，减少往返
        Map<Long, List<RedisGeoCommands.GeoLocation<String>>> grouped = new HashMap<>();
        for (Shop shop : shops) {
            grouped.computeIfAbsent(shop.getTypeId(), k -> new ArrayList<>())
                    .add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                            new Point(shop.getX(), shop.getY())));
        }
        grouped.forEach((typeId, locations) -> {
            String geoKey = MallRedisKey.getKey(MallRedisKey.SHOP_GEO_KEY, typeId);
            stringRedisTemplate.opsForGeo().add(geoKey, locations);
        });
        log.info("商户 GEO 数据写入完成, 商户数={}, 类型数={}", shops.size(), grouped.size());
        return shops.size();
    }

    @Override
    public void saveShop2Redis(Long id, Long expireSeconds) {
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            log.warn("商户不存在，无法预热缓存, id={}", id);
            return;
        }
        cacheClient.setWithLogicalExpire(MallRedisKey.getKey(MallRedisKey.CACHE_SHOP_KEY, id),
                shop, expireSeconds, TimeUnit.SECONDS);
    }
}
