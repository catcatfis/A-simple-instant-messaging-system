package com.catfish.mallchat.common.chatai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GPTRequestDTO 数据传输对象
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GPTRequestDTO {
    /**
     * 聊天内容
     */
    private String content;
    /**
     * 用户Id
     */
    private Long uid;
}
