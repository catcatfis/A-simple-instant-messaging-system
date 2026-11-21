package com.abin.mallchat.oss;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.file.FileNameUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.abin.mallchat.oss.domain.OssReq;
import com.abin.mallchat.oss.domain.OssResp;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Optional;

/**
 * Description: 本地磁盘存储实现。
 * <p>
 * 与 {@link MinIOTemplate} 保持相同的方法语义，使 OssServiceImpl 无需改动即可切换。
 * 前端只依赖「先取 uploadUrl → PUT 上传 → 用 downloadUrl 展示」，因此本地实现同样返回一对 URL：
 * <ul>
 *     <li>uploadUrl   = {endpoint}/capi/oss/upload/local?path={objectPath}</li>
 *     <li>downloadUrl = {endpoint}{objectPath}</li>
 * </ul>
 * endpoint 需配置为客户端可访问的后端地址（如 http://localhost:8080）。
 */
@AllArgsConstructor
public class LocalOssTemplate implements OssServiceTemplate {

    /**
     * 上传接口路径，与 LocalOssUploadController 保持一致。
     * 刻意不放在 /capi 下：前端 PUT 裸二进制时不带 Authorization 头，
     * 放在 /capi 会被 TokenInterceptor 拦截返回 401。
     * 因此该端点靠 {@link OssUploadTicket} 票据鉴权，而不是会话 token。
     */
    public static final String UPLOAD_PATH = "/oss/upload/local";

    OssProperties ossProperties;

    /**
     * 上传票据签发器，用于保护裸 PUT 上传端点
     */
    OssUploadTicket ossUploadTicket;

    /**
     * 返回本地上传/下载地址，替代 MinIO 的预签名 URL
     */
    @Override
    public OssResp getPreSignedObjectUrl(OssReq req) {
        String absolutePath = req.isAutoPath() ? generateAutoPath(req) : req.getFilePath() + StrUtil.SLASH + req.getFileName();
        String encodedPath = java.net.URLEncoder.encode(absolutePath, java.nio.charset.StandardCharsets.UTF_8);
        return OssResp.builder()
                .uploadUrl(ossProperties.getEndpoint() + UPLOAD_PATH + "?path=" + encodedPath)
                .downloadUrl(getDownloadUrl(absolutePath))
                // 前端 PUT 时需回传该票据（请求头 X-Upload-Ticket），否则上传会被拒绝
                .uploadTicket(ossUploadTicket.sign(absolutePath))
                .build();
    }

    private String getDownloadUrl(String pathFile) {
        return ossProperties.getEndpoint() + pathFile;
    }

    /**
     * 与 MinIOTemplate 完全一致的路径规则：{filePath}/{yyyyMM}/{uid}/{uuid}.{suffix}
     */
    @Override
    public String generateAutoPath(OssReq req) {
        String uid = Optional.ofNullable(req.getUid()).map(String::valueOf).orElse("000000");
        UUID uuid = UUID.fastUUID();
        String suffix = FileNameUtil.getSuffix(req.getFileName());
        String yearAndMonth = DateUtil.format(new Date(), DatePattern.NORM_MONTH_PATTERN);
        return req.getFilePath() + StrUtil.SLASH + yearAndMonth + StrUtil.SLASH + uid + StrUtil.SLASH + uuid + StrUtil.DOT + suffix;
    }

    /**
     * 存储根目录，未配置时退化为临时目录，避免启动即失败
     */
    public String getRootDir() {
        String root = ossProperties.getLocalRoot();
        if (StrUtil.isBlank(root)) {
            root = System.getProperty("java.io.tmpdir") + File.separator + "mallchat-oss";
        }
        return root;
    }

    /**
     * 把 objectPath 解析成磁盘真实路径，并做目录穿越防护。
     * 拒绝 ".." 等越权访问，确保文件只能落在 root 之内。
     */
    public Path resolve(String objectPath) {
        String relative = StrUtil.removePrefix(objectPath, StrUtil.SLASH);
        Path root = Paths.get(getRootDir()).toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的文件路径: " + objectPath);
        }
        return target;
    }

    /**
     * 保存上传的文件
     */
    @SneakyThrows
    public void writeFile(String objectPath, InputStream inputStream) {
        Path target = resolve(objectPath);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
}
