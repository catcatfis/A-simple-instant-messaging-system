package com.catfish.mallchat.common.user.service.impl;

import com.catfish.mallchat.common.common.utils.AssertUtil;
import com.catfish.mallchat.common.user.domain.enums.OssSceneEnum;
import com.catfish.mallchat.common.user.domain.vo.request.oss.UploadUrlReq;
import com.catfish.mallchat.common.user.service.OssService;
import com.abin.mallchat.oss.OssServiceTemplate;
import com.abin.mallchat.oss.domain.OssReq;
import com.abin.mallchat.oss.domain.OssResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Description: 对象存储服务实现，根据场景生成上传临时 URL
 */
@Service
public class OssServiceImpl implements OssService {
    /**
     * 依赖抽象而非具体实现，因此 oss.type=minio 与 oss.type=local 可自由切换
     */
    @Autowired
    private OssServiceTemplate ossServiceTemplate;

    @Override
    public OssResp getUploadUrl(Long uid, UploadUrlReq req) {
        OssSceneEnum sceneEnum = OssSceneEnum.of(req.getScene());
        AssertUtil.isNotEmpty(sceneEnum, "场景有误");
        OssReq ossReq = OssReq.builder()
                .fileName(req.getFileName())
                .filePath(sceneEnum.getPath())
                .uid(uid)
                .build();
        return ossServiceTemplate.getPreSignedObjectUrl(ossReq);
    }
}
