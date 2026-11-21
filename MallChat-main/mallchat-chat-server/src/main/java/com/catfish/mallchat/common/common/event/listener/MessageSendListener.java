package com.catfish.mallchat.common.common.event.listener;

import com.catfish.mallchat.common.chat.dao.ContactDao;
import com.catfish.mallchat.common.chat.dao.MessageDao;
import com.catfish.mallchat.common.chat.dao.RoomDao;
import com.catfish.mallchat.common.chat.dao.RoomFriendDao;
import com.catfish.mallchat.common.chat.domain.entity.Message;
import com.catfish.mallchat.common.chat.domain.entity.Room;
import com.catfish.mallchat.common.chat.domain.enums.HotFlagEnum;
import com.catfish.mallchat.common.chat.service.ChatService;
import com.catfish.mallchat.common.chat.service.WeChatMsgOperationService;
import com.catfish.mallchat.common.chat.service.cache.GroupMemberCache;
import com.catfish.mallchat.common.chat.service.cache.HotRoomCache;
import com.catfish.mallchat.common.chat.service.cache.RoomCache;
import com.catfish.mallchat.common.chatai.service.IChatAIService;
import com.catfish.mallchat.common.common.constant.MQConstant;
import com.catfish.mallchat.common.common.domain.dto.MsgSendMessageDTO;
import com.catfish.mallchat.common.common.event.MessageSendEvent;
import com.catfish.mallchat.common.user.service.WebSocketService;
import com.catfish.mallchat.common.user.service.cache.UserCache;
import com.abin.mallchat.transaction.service.MQProducer;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * 消息发送监听器：监听 Spring 本地事件 MessageSendEvent，负责后续的异步处理。
 *
 * <p>这里最容易新手看不懂的是<br>
 * {@code @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)}：
 * <ul>
 *     <li>普通 @EventListener 会立即执行，此时消息还没真正落库；</li>
 *     <li>@TransactionalEventListener 默认 AFTER_COMMIT，即事务提交后才执行；</li>
 *     <li>但本地消息表方案必须用 BEFORE_COMMIT：因为“登记可靠消息”这一步需要在业务事务里写库，
 *         如果等到 AFTER_COMMIT，事务已经结束，切面检测不到活动事务，会直接执行而
 *         不会把记录写入 secure_invoke_record，可靠投递就失效了。</li>
 * </ul>
 *
 * <p>其他两个监听器用的是默认的 AFTER_COMMIT（事务提交后才做），
 * 因为 AI 回复、微信通知都属于“事情已经成功后再做也不影响主流程”的动作。
 */
@Slf4j
@Component
public class MessageSendListener {
    @Autowired
    private WebSocketService webSocketService;
    @Autowired
    private ChatService chatService;
    @Autowired
    private MessageDao messageDao;
    @Autowired
    private IChatAIService openAIService;
    @Autowired
    WeChatMsgOperationService weChatMsgOperationService;
    @Autowired
    private RoomCache roomCache;
    @Autowired
    private RoomDao roomDao;
    @Autowired
    private GroupMemberCache groupMemberCache;
    @Autowired
    private UserCache userCache;
    @Autowired
    private RoomFriendDao roomFriendDao;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private ContactDao contactDao;
    @Autowired
    private HotRoomCache hotRoomCache;
    @Autowired
    private MQProducer mqProducer;

    /**
     * 消息路由：把“新消息 id”投递到 MQ，后续由 MsgSendConsumer 异步分发。
     *
     * <p>为什么用 sendSecureMsg 而不是 sendMsg？
     * 因为发消息是核心业务，不能接受“库里有这条消息、但没人收到推送”的不一致；
     * 而 sendSecureMsg 会先把发送动作落库，事务提交后保证发出，失败还能重试。
     *
     * <p>phase = BEFORE_COMMIT 的原因见类注释；fallbackExecution = true
     * 表示即使没有事务也执行（否则没有事务时该方法不会被调用）。
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, classes = MessageSendEvent.class, fallbackExecution = true)
    public void messageRoute(MessageSendEvent event) {
        Long msgId = event.getMsgId();
        mqProducer.sendSecureMsg(MQConstant.SEND_MSG_TOPIC, new MsgSendMessageDTO(msgId), msgId);
    }

    /**
     * 热门群聊里触发 AI 机器人回复（事务提交后执行，避免读到未提交数据）
     */
    @TransactionalEventListener(classes = MessageSendEvent.class, fallbackExecution = true)
    public void handlerMsg(@NotNull MessageSendEvent event) {
        Message message = messageDao.getById(event.getMsgId());
        Room room = roomCache.get(message.getRoomId());
        // 只有大群（热门群）才让 AI 接话，避免私聊/小群被打扰
        if (isHotRoom(room)) {
            openAIService.chat(message);
        }
    }

    /**
     * 判断是否为热门群（热房间的消息会推给全部在线用户）
     */
    public boolean isHotRoom(Room room) {
        return Objects.equals(HotFlagEnum.YES.getType(), room.getHotFlag());
    }

    /**
     * 给用户微信推送艾特好友的消息通知
     * （这个没开启，微信不让推）
     */
    @TransactionalEventListener(classes = MessageSendEvent.class, fallbackExecution = true)
    public void publishChatToWechat(@NotNull MessageSendEvent event) {
        Message message = messageDao.getById(event.getMsgId());
        if (Objects.nonNull(message.getExtra().getAtUidList())) {
            weChatMsgOperationService.publishChatMsgToWeChatUser(message.getFromUid(), message.getExtra().getAtUidList(),
                    message.getContent());
        }
    }
}
