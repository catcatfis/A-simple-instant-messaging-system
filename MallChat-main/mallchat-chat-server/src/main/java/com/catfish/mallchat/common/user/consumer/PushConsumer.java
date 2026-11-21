package com.catfish.mallchat.common.user.consumer;

import com.catfish.mallchat.common.common.constant.MQConstant;
import com.catfish.mallchat.common.common.domain.dto.PushMessageDTO;
import com.catfish.mallchat.common.user.domain.enums.WSPushTypeEnum;
import com.catfish.mallchat.common.user.service.WebSocketService;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 推送消息的 RocketMQ 消费者：把 MQ 里的“推送任务”真正写给本机上的 WebSocket 连接。
 *
 * <p>为什么要用广播模式（BROADCASTING）？
 * 假设用户 A 连在服务器 1，用户 B 连在服务器 2。一条群消息需要推给两个人，
 * 如果消息只被其中一台机器消费，另一台机器上的用户就收不到。
 * 广播模式下每个实例都会收到这条消息，各自去自己的连接表里找人。
 *
 * <p>注意：消费者方法里的逻辑应该尽量幂等（重复执行也没问题），
 * 因为 MQ 默认可能重复投递（至少一次语义）。
 */
@RocketMQMessageListener(topic = MQConstant.PUSH_TOPIC, consumerGroup = MQConstant.PUSH_GROUP, messageModel = MessageModel.BROADCASTING)
@Component
public class PushConsumer implements RocketMQListener<PushMessageDTO> {
    @Autowired
    private WebSocketService webSocketService;

    /**
     * 收到推送消息后，根据推送类型决定推给谁：
     * <ul>
     *     <li>USER：只推给指定的一个/一批 uid；</li>
     *     <li>ALL：推给本机所有在线用户（比如热门群聊新消息）。</li>
     * </ul>
     * 注意“推给 uid”时也是在本机连接表里找，找不到说明该用户不在本机，直接跳过。
     */
    @Override
    public void onMessage(PushMessageDTO message) {
        WSPushTypeEnum wsPushTypeEnum = WSPushTypeEnum.of(message.getPushType());
        switch (wsPushTypeEnum) {
            case USER:
                message.getUidList().forEach(uid -> {
                    webSocketService.sendToUid(message.getWsBaseMsg(), uid);
                });
                break;
            case ALL:
                webSocketService.sendToAllOnline(message.getWsBaseMsg(), null);
                break;
        }
    }
}
