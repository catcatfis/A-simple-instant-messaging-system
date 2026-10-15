package com.abin.mallchat.oss;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description: 本地磁盘存储配置，oss.type=local 时启用。
 * 与 MinIOConfiguration 互斥，切换只需改 oss.type。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnExpression("${oss.enabled}")
@ConditionalOnProperty(value = "oss.type", havingValue = "local")
public class LocalOssConfiguration {

    @Bean
    @ConditionalOnMissingBean(LocalOssTemplate.class)
    public LocalOssTemplate localOssTemplate(OssProperties ossProperties) {
        return new LocalOssTemplate(ossProperties);
    }
}
