package com.catfish.mallchat.common.common.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息发送的 MQ 传输对象（消息体）。
 *
 * <p>注意：这里只传了 msgId，而不是把整条消息都塞进 MQ。
 * 这样做的好处是：
 * <ul>
 *     <li>消息体小，传输快；</li>
 *     <li>消费时再从数据库查最新数据，不会拿到“旧快照”；</li>
 *     <li>MQ 里不存敏感的业务内容。</li>
 * </ul>
 *
 * <p>链路：ChatServiceImpl.sendMsg -> 发布 MessageSendEvent -> MessageSendListener 发消息到 MQ
 * -> MsgSendConsumer 根据 msgId 查库并推送。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MsgSendMessageDTO implements Serializable {
    /** 数据库 message 表的主键 id */
    private Long msgId;
}
