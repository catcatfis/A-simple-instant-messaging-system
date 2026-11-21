package com.catfish.mallchat.common.user.consumer;

import com.catfish.mallchat.common.common.constant.MQConstant;
import com.catfish.mallchat.common.common.domain.dto.LoginMessageDTO;
import com.catfish.mallchat.common.user.service.WebSocketService;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 登录消息的 RocketMQ 消费者：在本地服务上找寻对应 channel，完成该用户的登录，并触发上线事件。
 *
 * <p>为什么用广播模式：
 * 扫二维码建立 WebSocket 连接的是 A 实例，用户在微信里点授权可能打到 B 实例。
 * B 实例处理完只能发一条广播 MQ，所有实例都收到，只有真正持有这个 code 连接的 A 实例
 * 能从本地缓存 WAIT_LOGIN_MAP 里找到 channel 并下发 token，其余实例找不到就自然忽略。
 *
 * <p>如果所有实例都找不到（比如用户关闭了页面/服务重启），这次登录就自然失败，
 * 前端会表现为二维码过期，需要重新扫码。
 */
@RocketMQMessageListener(consumerGroup = MQConstant.LOGIN_MSG_GROUP, topic = MQConstant.LOGIN_MSG_TOPIC, messageModel = MessageModel.BROADCASTING)
@Component
public class MsgLoginConsumer implements RocketMQListener<LoginMessageDTO> {
    @Autowired
    private WebSocketService webSocketService;

    @Override
    public void onMessage(LoginMessageDTO loginMessageDTO) {
        //尝试登录登录（本机没有对应 channel 时会直接返回 false，不影响其他实例）
        webSocketService.scanLoginSuccess(loginMessageDTO.getCode(), loginMessageDTO.getUid());
    }

}
