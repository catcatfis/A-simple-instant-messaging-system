package com.abin.mallchat.oss;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Oss 配置属性
 */

@Data
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    /**
     * 是否开启
     */
    Boolean enabled;

    /**
     * 存储对象服务器类型
     */
    OssType type;

    /**
     * OSS 访问端点，集群时需提供统一入口
     */
    String endpoint;

    /**
     * 用户名
     */
    String accessKey;

    /**
     * 密码
     */
    String secretKey;

    /**
     * 存储桶
     */
    String bucketName;

    /**
     * 本地磁盘存储的根目录，仅 oss.type=local 时生效。
     * 为空时退化为 java.io.tmpdir/mallchat-oss
     */
    String localRoot;

    /**
     * 本地上传票据的签名密钥，仅 oss.type=local 时生效。
     * 未配置时使用 secretKey，仍为空则退化为进程级随机密钥（多实例部署时需显式配置）。
     */
    String uploadTicketSecret;
}
