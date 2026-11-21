package com.catfish.mallchat.common.common.constant;

/**
 * MQConstant：本项目所有 RocketMQ 主题（Topic）和消费组（ConsumerGroup）的统一定义。
 *
 * <p>新手提示：
 * <ul>
 *     <li>Topic 相当于“频道”，生产者往某个 topic 发，消费者监听这个 topic 收；</li>
 *     <li>Group 是消费组。同一个 group 内的多个实例在集群模式下分摊消息，
 *         不同 group 之间互不影响（各自都能收到一份）。</li>
 *     <li>本项目的 4 个消费组都用 BROADCASTING（广播）模式，因为 WebSocket 连接在不同实例上，
 *         每个实例都需要收到消息，才能推给连在自己身上的用户。</li>
 * </ul>
 *
 * <p>四组主题对应的业务：登录、扫码、发消息、WebSocket 推送，
 * 正好对应本项目 4 个消费者类：MsgLoginConsumer、ScanSuccessConsumer、MsgSendConsumer、PushConsumer。
 */

public interface MQConstant {

    /**
     * 发消息主题：用户发出消息、写入数据库后，通过它通知消费者去做“房间活跃时间刷新 + 推送给房间成员”
     * 生产者：MessageSendListener；消费者：MsgSendConsumer
     */
    String SEND_MSG_TOPIC = "chat_send_msg";
    String SEND_MSG_GROUP = "chat_send_msg_group";

    /**
     * 推送主题：所有需要“主动推送给前端 WebSocket”的消息都走这里。
     * 这样业务代码不用关心用户到底连在哪个实例上，由消费者在本地查找 channel 推送。
     * 生产者：PushService；消费者：PushConsumer
     */
    String PUSH_TOPIC = "websocket_push";
    String PUSH_GROUP = "websocket_push_group";

    /**
     * (授权完成后)登录信息主题：告诉持有二维码连接的那台实例“用户已登录成功，可以下发 token 了”。
     * 生产者：WxMsgService；消费者：MsgLoginConsumer
     */
    String LOGIN_MSG_TOPIC = "user_login_send_msg";
    String LOGIN_MSG_GROUP = "user_login_send_msg_group";

    /**
     * 扫码成功主题：用户扫了二维码但还没授权，先通知前端“已扫码，请等待确认”。
     * 生产者：WxMsgService；消费者：ScanSuccessConsumer
     */
    String SCAN_MSG_TOPIC = "user_scan_send_msg";
    String SCAN_MSG_GROUP = "user_scan_send_msg_group";
}
