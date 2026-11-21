package com.catfish.mallchat.common.common.domain.vo.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * 通用 ID 请求体
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdReqVO {
    @ApiModelProperty("id")
    @NotNull
    private long id;
}
