package com.catfish.mallchat.common.user.consumer;

import com.catfish.mallchat.common.common.constant.MQConstant;
import com.catfish.mallchat.common.common.domain.dto.ScanSuccessMessageDTO;
import com.catfish.mallchat.common.user.service.WebSocketService;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 扫码成功消息的 RocketMQ 消费者：把“已扫码”的状态发给持有该连接的用户，等待其授权。
 *
 * <p>同样是广播模式，谁本地有这个登录码对应的 channel，谁就给前端推一条“已扫码”消息。
 * 前端收到后会把二维码页面从“等待扫码”切换成“已扫码，请在手机上确认”。
 */
@RocketMQMessageListener(consumerGroup = MQConstant.SCAN_MSG_GROUP, topic = MQConstant.SCAN_MSG_TOPIC, messageModel = MessageModel.BROADCASTING)
@Component
public class ScanSuccessConsumer implements RocketMQListener<ScanSuccessMessageDTO> {
    @Autowired
    private WebSocketService webSocketService;

    @Override
    public void onMessage(ScanSuccessMessageDTO scanSuccessMessageDTO) {
        webSocketService.scanSuccess(scanSuccessMessageDTO.getCode());
    }

}
