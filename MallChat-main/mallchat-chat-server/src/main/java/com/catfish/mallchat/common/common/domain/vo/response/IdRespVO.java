package com.catfish.mallchat.common.common.domain.vo.response;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用 ID 响应体
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdRespVO {
    @ApiModelProperty("id")
    private long id;

    public static IdRespVO id(Long id) {
        IdRespVO idRespVO = new IdRespVO();
        idRespVO.setId(id);
        return idRespVO;
    }
}
