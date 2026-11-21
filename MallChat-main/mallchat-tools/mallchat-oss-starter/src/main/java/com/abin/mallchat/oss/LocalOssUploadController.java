package com.abin.mallchat.oss;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;

/**
 * Description: 本地磁盘存储的上传入口。
 * <p>
 * 前端拿到 uploadUrl 后会直接 PUT 文件到该地址（与 MinIO 预签名 PUT 行为一致），
 * 因此这里只负责把请求体落盘，不做任何业务处理。
 * 路径校验由 {@link LocalOssTemplate#resolve} 负责，防止目录穿越。
 * <p>
 * 鉴权：该端点不在 /capi 下，无法走 TokenInterceptor（前端 PUT 裸二进制时不带 Authorization）。
 * 改由 {@link OssUploadTicket} 票据鉴权——票据由已登录接口签发、绑定路径且带过期时间，
 * 从而避免上传端点被匿名滥用为文件托管。
 * <p>
 * 注意:
 * <ul>
 *     <li>刻意不使用 @ConditionalOnBean，因为自动配置的注册顺序不保证条件成立</li>
 *     <li>必须开启 CORS：前端页面与后端不同源，且 PUT 带 Content-Type 会触发 OPTIONS 预检，
 *         预检失败会导致前端 XHR 永不 onload（表现为上传一直转圈）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping(LocalOssTemplate.UPLOAD_PATH)
@ConditionalOnExpression("${oss.enabled}")
@ConditionalOnProperty(value = "oss.type", havingValue = "local")
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", methods = {RequestMethod.PUT, RequestMethod.OPTIONS})
@RequiredArgsConstructor
public class LocalOssUploadController {

    private final LocalOssTemplate localOssTemplate;

    private final OssUploadTicket ossUploadTicket;

    @PutMapping
    public ResponseEntity<Void> upload(@RequestParam("path") String path, HttpServletRequest request) {
        // 票据校验：签名有效、未过期、且与本次请求路径绑定
        String ticket = request.getHeader(OssUploadTicket.HEADER);
        if (!ossUploadTicket.verify(ticket, path)) {
            log.warn("本地存储拒绝无票据或票据非法的上传请求, path={}", path);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try (InputStream inputStream = request.getInputStream()) {
            localOssTemplate.writeFile(path, inputStream);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("本地存储拒绝非法路径: {}", path);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("本地存储写入失败, path={}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
