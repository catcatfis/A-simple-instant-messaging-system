package com.catfish.mallchat.common.chat.domain.vo.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description: 消息已读未读数量信息响应体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageReadInfoResp {
    @ApiModelProperty("消息id")
    private Long msgId;

    @ApiModelProperty("已读数")
    private Integer readCount;

    @ApiModelProperty("未读数")
    private Integer unReadCount;

}
