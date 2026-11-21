package com.catfish.mallchat.common.common.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录消息的 MQ 传输对象。
 *
 * <p>为什么登录也要走 MQ？因为 WebSocket 连接可能连在 A 实例上，
 * 而用户扫码（微信回调）可能落在 B 实例上。B 不知道 A 上保存着那个 channel，
 * 所以通过广播 MQ 让所有实例都收到，谁本地缓存了这个 code 对应的连接，谁就去完成登录。
 * （本地缓存见 WebSocketServiceImpl.WAIT_LOGIN_MAP）
 *
 * <p>生产者：WxMsgService；消费者：MsgLoginConsumer -> WebSocketServiceImpl.scanLoginSuccess
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 已登录成功的用户 id */
    private Long uid;
    /** 二维码对应的登录码（用来找到发起登录的那条 WebSocket 连接） */
    private Integer code;
}
