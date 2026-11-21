package com.catfish.mallchat.common.user.service.impl;

import com.catfish.mallchat.common.common.constant.MQConstant;
import com.catfish.mallchat.common.common.domain.dto.PushMessageDTO;
import com.catfish.mallchat.common.user.domain.enums.WSBaseResp;
import com.abin.mallchat.transaction.service.MQProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * WebSocket 消息推送服务。
 *
 * <p>注意：它并不直接写 channel，而是把“推送任务”丢进 MQ 的 websocket_push 主题，
 * 之后再由图上的 PushConsumer 去各实例的本地连接表里找对应的 WebSocket channel。
 *
 * <p>为什么要多这一层？因为服务器可能是集群部署：处理业务请求的实例，
 * 未必就是用户 WebSocket 长连接所在的那个实例。通过广播 MQ 来“找人”。
 *
 * <p>使用普通 sendMsg（不保证不丢）而不是 sendSecureMsg：推送丢失的影响很小，
 * 用户刷新后仍能看到历史消息。
 */
@Service
public class PushService {
    @Autowired
    private MQProducer mqProducer;

    /**
     * 推送给指定的一批用户
     */
    public void sendPushMsg(WSBaseResp<?> msg, List<Long> uidList) {
        mqProducer.sendMsg(MQConstant.PUSH_TOPIC, new PushMessageDTO(uidList, msg));
    }

    /**
     * 推送给单个用户
     */
    public void sendPushMsg(WSBaseResp<?> msg, Long uid) {
        mqProducer.sendMsg(MQConstant.PUSH_TOPIC, new PushMessageDTO(uid, msg));
    }

    /**
     * 推送给所有在线用户（热门群聊场景）
     */
    public void sendPushMsg(WSBaseResp<?> msg) {
        mqProducer.sendMsg(MQConstant.PUSH_TOPIC, new PushMessageDTO(msg));
    }
}
