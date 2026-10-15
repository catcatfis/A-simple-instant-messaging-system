package com.abin.mallchat.oss;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Description: 本地磁盘存储的下载入口（只读）。
 * <p>
 * downloadUrl 形如 {endpoint}/chat/202609/20000/xxx.png，这里把 HTTP 路径直接映射到存储根目录下的文件。
 * 目录穿越由 {@link LocalOssTemplate#resolve} 拦截。
 */
@Slf4j
@RestController
@ConditionalOnExpression("${oss.enabled}")
@ConditionalOnProperty(value = "oss.type", havingValue = "local")
@CrossOrigin(originPatterns = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.OPTIONS})
@RequiredArgsConstructor
public class LocalOssDownloadController {

    private final LocalOssTemplate localOssTemplate;

    @GetMapping("/**")
    public ResponseEntity<InputStreamResource> download(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (attribute == null) {
            return ResponseEntity.notFound().build();
        }
        String path = attribute.toString();

        Path file;
        try {
            file = localOssTemplate.resolve(path);
        } catch (IllegalArgumentException e) {
            log.warn("本地存储拒绝非法下载路径: {}", path);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        try {
            String contentType = Files.probeContentType(file);
            MediaType mediaType = contentType != null
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
            InputStream inputStream = Files.newInputStream(file);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(Files.size(file))
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                    .body(new InputStreamResource(inputStream));
        } catch (Exception e) {
            log.error("本地存储读取失败, path={}", path, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
