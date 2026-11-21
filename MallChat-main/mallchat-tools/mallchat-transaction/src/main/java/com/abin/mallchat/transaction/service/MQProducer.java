package com.abin.mallchat.transaction.service;

import com.abin.mallchat.transaction.annotation.SecureInvoke;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 发送 MQ 消息的工具类（本项目所有发消息的地方最终都会走到这里）。
 *
 * <p>先建立几个 MQ 基础概念（新手必读）：
 * <ul>
 *     <li><b>Topic（主题）</b>：消息的分类频道，例如本项目里“聊天消息”走 chat_send_msg，
 *      “给 WebSocket 推送”走 websocket_push。生产者按 topic 发，消费者按 topic 收。</li>
 *     <li><b>Producer（生产者）</b>：发消息的一方，也就是这个类。</li>
 *     <li><b>Consumer（消费者）</b>：收消息的一方，形如 MsgSendConsumer、PushConsumer 这种类。</li>
 *     <li><b>ConsumerGroup（消费组）</b>：同一组内的消费者共同分摊消息（集群模式），
 *     不同组各收一份（广播模式）。本项目消费者基本都用 BROADCASTING 广播模式。</li>
 * </ul>
 *
 * <p>MQ 解决的核心问题：<b>解耦 + 异步 + 削峰</b>。
 * 以发消息为例，A 发出一条消息后，接口不需要等“给房间每个人推送”这些耗时操作做完就可以返回，
 * 把后续处理丢给 MQ，由消费者慢慢处理。
 *
 * <p>注意：这个类不在 mallchat-chat-server 模块里，而在 mallchat-tools/mallchat-transaction 模块里，
 * 由 TransactionAutoConfiguration 通过 @Bean 注册成 Spring 容器里的对象。
 */
public class MQProducer {

    /**
     * RocketMQ 的模板对象，用法和 RedisTemplate 类似，是官方 starter 提供的“发送消息入口”。
     * 它需要的 name-server 地址在配置文件里配置：rocketmq.name-server=127.0.0.1:9876
     */
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 普通发送：调用后立刻把消息丢给 MQ，不保证成功、不保证不丢。
     *
     * <p>适用场景：丢了也无所谓的消息，例如“给在线用户推送一条 WebSocket 通知”——
     * 用户这次没收到，下次刷新页面还能看到，所以没必要做强保证。
     *
     * @param topic 主题，取值见 MQConstant 里的常量
     * @param body  消息体，会被序列化成 JSON 存到 MQ 的 Broker 上
     */
    public void sendMsg(String topic, Object body) {
        Message<Object> build = MessageBuilder.withPayload(body).build();
        rocketMQTemplate.send(topic, build);
    }

    /**
     * 发送<b>可靠</b>消息：保证“数据库事务提交了，消息一定发出去”。
     *
     * <p>为什么要单独做这一套？因为“写数据库”和“发 MQ”是两个不同的系统，中间可能失败：
     * <pre>
     *   写库成功 -> 发 MQ 失败（网络抖动/重启）-> 消息永久丢失
     * </pre>
     * 这里的解法是业界常说的<b>本地消息表（Transactional Outbox）</b>：
     * <ol>
     *     <li>方法被 @SecureInvoke 注解后，会被 SecureInvokeAspect 切面拦截；</li>
     *     <li>切面把“要调用的类名、方法名、参数”序列化后写入 secure_invoke_record 表（和业务同事务）；</li>
     *     <li>事务提交后（afterCommit）再真正调用本方法发 MQ；</li>
     *     <li>如果发送失败，记录会在表里保留，由 SecureInvokeService 的定时任务每 5 秒扫描并重试。</li>
     * </ol>
     * 这样“业务写库”和“消息落库”在同一个本地事务里，只要事务成功，消息就不会丢。
     *
     * <p>另外注意：因为真正执行被挪到了事务提交之后，方法本身必须返回 void。
     *
     * @param topic 主题
     * @param body  消息体
     * @param key   业务键（例如消息 id）。RocketMQ 用它做消息索引，方便在控制台按 key 查消息、
     *              也便于排查重复消息
     */
    @SecureInvoke
    public void sendSecureMsg(String topic, Object body, Object key) {
        Message<Object> build = MessageBuilder
                .withPayload(body)
                // KEYS 是 RocketMQ 约定的消息头，用于按业务 key 检索消息
                .setHeader("KEYS", key)
                .build();
        rocketMQTemplate.send(topic, build);
    }
}
