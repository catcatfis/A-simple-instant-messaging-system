package com.catfish.mallchat.common.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 消息发送事件（Spring 本地事件，不是 MQ 消息，别搞混）。
 *
 * <p>作用：把“消息已经存库”这件事告诉同一个应用里的其他监听器，好处是解耦：
 * ChatServiceImpl 只负责发布事件，不关心后续是发 MQ、调 AI、还是发微信通知。
 *
 * <p>本地事件靠 ApplicationEventPublisher 发布，
 * 由 MessageSendListener 用 @TransactionalEventListener 接收，
 * 接收方可以根据事务阶段决定“事务提交前”还是“事务提交后”执行。
 */

@Getter
public class MessageSendEvent extends ApplicationEvent {
    /** 新插入的那条消息 id */
    private Long msgId;

    public MessageSendEvent(Object source, Long msgId) {
        super(source);
        this.msgId = msgId;
    }
}
