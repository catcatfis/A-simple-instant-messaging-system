package com.catfish.mallchat.common.common.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 扫码成功消息的 MQ 传输对象。
 *
 * <p>用户刚扫完码、还没点击授权时，需要先让前端页面提示“已扫码，等待确认”。
 * 同样因为连接可能不在当前实例上，所以通过广播 MQ 通知所有实例。
 *
 * <p>生产者：WxMsgService；消费者：ScanSuccessConsumer -> WebSocketServiceImpl.scanSuccess
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScanSuccessMessageDTO implements Serializable {
    /**
     * 二维码对应的登录码（用于找到发起登录的那条 WebSocket 连接）
     */
    private Integer code;

}
