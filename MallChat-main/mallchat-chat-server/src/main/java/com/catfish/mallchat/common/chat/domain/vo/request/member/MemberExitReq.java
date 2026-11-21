package com.catfish.mallchat.common.chat.domain.vo.request.member;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * MemberExitReq 请求体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberExitReq {
    @NotNull
    @ApiModelProperty("会话id")
    private Long roomId;
}
