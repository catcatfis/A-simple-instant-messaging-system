package com.abin.mallchat.oss;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Description: 本地存储上传票据的签发与校验。
 * <p>
 * 本地存储没有 MinIO 那样的服务端预签名能力，若上传端点完全开放，
 * 任何人都能不携带登录态向服务器写入任意文件（可被当作免费网盘或恶意文件托管）。
 * <p>
 * 这里采用无状态 HMAC 票据解决：
 * <ul>
 *     <li>后端在 {@code /capi/oss/upload/url}（已被 TokenInterceptor 保护）拿到 uid 后签发票据</li>
 *     <li>前端 PUT 时把票据放在 {@code X-Upload-Ticket} 头</li>
 *     <li>上传端点校验票据签名与过期时间，并确保票据绑定的路径与请求路径一致</li>
 * </ul>
 * 票据不落库，因此多实例部署时各节点使用同一 {@code oss.uploadTicketSecret} 即可校验通过。
 */
@AllArgsConstructor
public class OssUploadTicket {

    /**
     * 票据默认有效期（毫秒）
     */
    public static final long DEFAULT_TTL_MILLIS = 10 * 60 * 1000L;

    /**
     * 票据携带的请求头名称
     */
    public static final String HEADER = "X-Upload-Ticket";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;

    /**
     * 签发票据：payload 为 {@code 过期时间戳:规范化路径}
     */
    public String sign(String objectPath) {
        long expireAt = System.currentTimeMillis() + DEFAULT_TTL_MILLIS;
        String payload = expireAt + ":" + normalize(objectPath);
        return expireAt + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(payload));
    }

    /**
     * 校验票据：签名正确、未过期、且与请求路径绑定
     */
    public boolean verify(String ticket, String objectPath) {
        if (StrUtil.isBlank(ticket) || StrUtil.isBlank(objectPath)) {
            return false;
        }
        int dot = ticket.indexOf('.');
        if (dot <= 0 || dot == ticket.length() - 1) {
            return false;
        }
        long expireAt;
        try {
            expireAt = Long.parseLong(ticket.substring(0, dot));
        } catch (NumberFormatException e) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            return false;
        }
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(expireAt + ":" + normalize(objectPath)));
        // 定长时间比较，避免通过响应时间差推算签名
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                ticket.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 统一路径形式，避免 {@code /a/b} 与 {@code a/b} 被视为不同路径
     */
    private static String normalize(String objectPath) {
        return StrUtil.removePrefix(objectPath, StrUtil.SLASH);
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("上传票据签名失败", e);
        }
    }
}
