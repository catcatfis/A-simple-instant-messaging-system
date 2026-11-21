package com.abin.mallchat.oss;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * Description: 本地磁盘存储配置，oss.type=local 时启用。
 * 与 MinIOConfiguration 互斥，切换只需改 oss.type。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnExpression("${oss.enabled}")
@ConditionalOnProperty(value = "oss.type", havingValue = "local")
public class LocalOssConfiguration {

    @Bean
    @ConditionalOnMissingBean(LocalOssTemplate.class)
    public LocalOssTemplate localOssTemplate(OssProperties ossProperties, OssUploadTicket ossUploadTicket) {
        return new LocalOssTemplate(ossProperties, ossUploadTicket);
    }

    /**
     * 上传票据签发器：保证裸 PUT 上传端点不会被匿名滥用。
     * 多实例部署必须配置 oss.uploadTicketSecret，否则各节点密钥不同会导致票据校验失败。
     */
    @Bean
    @ConditionalOnMissingBean(OssUploadTicket.class)
    public OssUploadTicket ossUploadTicket(OssProperties ossProperties) {
        String secret = StrUtil.blankToDefault(ossProperties.getUploadTicketSecret(),
                ossProperties.getSecretKey());
        if (StrUtil.isBlank(secret)) {
            log.warn("未配置 oss.uploadTicketSecret/oss.secretKey，本地上传票据将使用随机密钥，多实例部署下票据无法跨节点校验");
            secret = UUID.fastUUID().toString(true);
        }
        return new OssUploadTicket(secret.getBytes(StandardCharsets.UTF_8));
    }
}
