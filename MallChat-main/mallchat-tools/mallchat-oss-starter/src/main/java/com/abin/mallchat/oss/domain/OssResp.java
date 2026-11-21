package com.abin.mallchat.oss.domain;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对象存储上传 URL 响应体
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OssResp {

    @ApiModelProperty(value = "上传的临时url")
    private String uploadUrl;

    @ApiModelProperty(value = "成功后能够下载的url")
    private String downloadUrl;

    @ApiModelProperty(value = "本地上传票据，PUT 上传时需放入请求头 X-Upload-Ticket；MinIO 预签名模式下为 null")
    private String uploadTicket;
}
