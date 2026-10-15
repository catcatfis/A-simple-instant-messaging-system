package com.abin.mallchat.common.mall.config;

import com.abin.mallchat.common.mall.service.IShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Description: 启动时把商户经纬度导入 Redis GEO 集合。
 * <p>
 * GEOADD 对同一 member 是覆盖语义，重复执行不会产生脏数据，因此这里无需判重。
 * 导入失败不阻断启动，{@code queryByType} 在 GEO 不可用时会降级为普通分页。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MallGeoInitializer implements ApplicationRunner {

    private final IShopService shopService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int count = shopService.loadShopGeo();
            log.info("启动时导入商户 GEO 数据, 商户数={}", count);
        } catch (Exception e) {
            log.warn("启动时导入商户 GEO 数据失败，按距离排序将降级为普通分页, cause={}", e.getMessage());
        }
    }
}
