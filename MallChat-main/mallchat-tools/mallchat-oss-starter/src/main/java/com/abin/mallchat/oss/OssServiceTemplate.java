package com.abin.mallchat.oss;

import com.abin.mallchat.oss.domain.OssReq;
import com.abin.mallchat.oss.domain.OssResp;

/**
 * Description: 对象存储的统一抽象。
 * <p>
 * MinIO 与本地磁盘两种实现都实现该接口，业务层只依赖抽象，
 * 因此切换 oss.type 不需要改动任何业务代码。
 */
public interface OssServiceTemplate {

    /**
     * 返回上传地址与下载地址。
     * MinIO 返回预签名 PUT URL；本地存储返回后端的上传/下载端点。
     */
    OssResp getPreSignedObjectUrl(OssReq req);

    /**
     * 生成随机对象路径，防止重名覆盖
     */
    String generateAutoPath(OssReq req);
}
