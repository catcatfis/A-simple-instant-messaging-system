package com.catfish.mallchat.common.common.domain.dto;

import com.catfish.mallchat.common.user.domain.enums.WSBaseResp;
import com.catfish.mallchat.common.user.domain.enums.WSPushTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 推送给用户的消息对象（走 websocket_push 主题）。
 *
 * <p>为什么要绕一圈用 MQ 推送，而不是直接调用 WebSocketService 发？
 * 因为用户可能连在任意一台服务器上，生产者根本不知道用户在哪个实例。
 * 所以把“要推给谁、推什么”发到广播 MQ，每台服务器都收到，
 * 再由各实例的 PushConsumer 在本地连接表里找，找到就发（找不到说明用户不在我这）。
 *
 * <p>生产者：PushService；消费者：PushConsumer
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PushMessageDTO implements Serializable {
    /**
     * 推送的ws消息（具体推什么内容，类型见 WSRespTypeEnum）
     */
    private WSBaseResp<?> wsBaseMsg;
    /**
     * 推送的uid（推给哪些用户）；全员推送时为空
     */
    private List<Long> uidList;

    /**
     * 推送类型 1个人 2全员
     *
     * @see WSPushTypeEnum
     */
    private Integer pushType;

    /**
     * 推给单个用户
     */
    public PushMessageDTO(Long uid, WSBaseResp<?> wsBaseMsg) {
        this.uidList = Collections.singletonList(uid);
        this.wsBaseMsg = wsBaseMsg;
        this.pushType = WSPushTypeEnum.USER.getType();
    }

    /**
     * 推给一批用户（例如一个群的所有成员）
     */
    public PushMessageDTO(List<Long> uidList, WSBaseResp<?> wsBaseMsg) {
        this.uidList = uidList;
        this.wsBaseMsg = wsBaseMsg;
        this.pushType = WSPushTypeEnum.USER.getType();
    }

    /**
     * 推给所有在线用户（例如热门群聊的新消息）
     */
    public PushMessageDTO(WSBaseResp<?> wsBaseMsg) {
        this.wsBaseMsg = wsBaseMsg;
        this.pushType = WSPushTypeEnum.ALL.getType();
    }
}
