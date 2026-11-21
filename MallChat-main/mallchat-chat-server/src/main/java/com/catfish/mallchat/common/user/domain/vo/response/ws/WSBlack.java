package com.catfish.mallchat.common.user.domain.vo.response.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Description: WebSocket 拉黑通知推送体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WSBlack {
    private Long uid;
}
