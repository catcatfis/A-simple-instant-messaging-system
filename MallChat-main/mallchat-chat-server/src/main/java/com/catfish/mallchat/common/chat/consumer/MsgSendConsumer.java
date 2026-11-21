package com.catfish.mallchat.common.chat.consumer;

import com.catfish.mallchat.common.chat.dao.ContactDao;
import com.catfish.mallchat.common.chat.dao.MessageDao;
import com.catfish.mallchat.common.chat.dao.RoomDao;
import com.catfish.mallchat.common.chat.dao.RoomFriendDao;
import com.catfish.mallchat.common.chat.domain.entity.Message;
import com.catfish.mallchat.common.chat.domain.entity.Room;
import com.catfish.mallchat.common.chat.domain.entity.RoomFriend;
import com.catfish.mallchat.common.chat.domain.enums.RoomTypeEnum;
import com.catfish.mallchat.common.chat.domain.vo.response.ChatMessageResp;
import com.catfish.mallchat.common.chat.service.ChatService;
import com.catfish.mallchat.common.chat.service.WeChatMsgOperationService;
import com.catfish.mallchat.common.chat.service.cache.GroupMemberCache;
import com.catfish.mallchat.common.chat.service.cache.HotRoomCache;
import com.catfish.mallchat.common.chat.service.cache.RoomCache;
import com.catfish.mallchat.common.chatai.service.IChatAIService;
import com.catfish.mallchat.common.common.constant.MQConstant;
import com.catfish.mallchat.common.common.domain.dto.MsgSendMessageDTO;
import com.catfish.mallchat.common.user.service.WebSocketService;
import com.catfish.mallchat.common.user.service.adapter.WSAdapter;
import com.catfish.mallchat.common.user.service.cache.UserCache;
import com.catfish.mallchat.common.user.service.impl.PushService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 发消息的 RocketMQ 消费者：真正把新消息“分发”出去。
 *
 * <p>为什么发消息要异步？因为用户点击发送后，接口只需把消息存进数据库就返回，
 * 后面的“刷新房间活跃时间、给房间里每个人推送”这些耗时可长的动作交给 MQ 消费者做，
 * 这样发送接口响应快，也不会因为推送失败而影响消息保存。
 *
 * <p>完整链路（新手建议按顺序阅读）：
 * <ol>
 *     <li>ChatController#sendMsg 接收请求；</li>
 *     <li>ChatServiceImpl#sendMsg 校验 + 存库 + 发布 MessageSendEvent（本地事件）；</li>
 *     <li>MessageSendListener#messageRoute 在事务提交前调用 mqProducer.sendSecureMsg 发到 chat_send_msg；</li>
 *     <li>本类的 onMessage 被触发，从库里查出消息并分发给房间成员。</li>
 * </ol>
 *
 * <p>这里的消费组没有写 messageModel，默认是 CLUSTERING（集群模式）：
 * 同一条消息只会被这个组里的一个实例消费。这是有意的——
 * 消息推送本身不依赖“每个实例都收到”，推送动作会再发一条广播消息到 websocket_push 主题。
 */
@RocketMQMessageListener(consumerGroup = MQConstant.SEND_MSG_GROUP, topic = MQConstant.SEND_MSG_TOPIC)
@Component
public class MsgSendConsumer implements RocketMQListener<MsgSendMessageDTO> {
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
    private PushService pushService;

    /**
     * 消费入口：MQ 收到一条消息就会调用一次。
     *
     * <p>注意：方法抛异常会让 MQ 认为消费失败并重新投递（有最大次数限制），
     * 因此必须保证这里的逻辑尽量幂等，重试也不会产生重复副作用。
     *
     * @param dto 消息体，里面只有消息 id，需要自己回库查询
     */
    @Override
    public void onMessage(MsgSendMessageDTO dto) {
        // 1. 根据 msgId 把完整的消息实体查出来（生产者只传了 id）
        Message message = messageDao.getById(dto.getMsgId());
        // 2. 取出消息所属房间（带 Caffeine/Redis 缓存）
        Room room = roomCache.get(message.getRoomId());
        // 3. 组装成前端需要的展示结构（包含发送人信息、引用消息等）
        ChatMessageResp msgResp = chatService.getMsgResp(message, null);
        //所有房间更新房间最新消息（MySQL：活跃时间 + 最后一条消息 id）
        roomDao.refreshActiveTime(room.getId(), message.getId(), message.getCreateTime());
        // 本地缓存删掉，下次访问时重新加载最新数据
        roomCache.delete(room.getId());
        if (room.isHotRoom()) {//热门群聊推送所有在线的人
            //更新热门群聊时间-redis
            hotRoomCache.refreshActiveTime(room.getId(), message.getCreateTime());
            //推送所有人
            pushService.sendPushMsg(WSAdapter.buildMsgSend(msgResp));
        } else {
            List<Long> memberUidList = new ArrayList<>();
            if (Objects.equals(room.getType(), RoomTypeEnum.GROUP.getType())) {//普通群聊推送所有群成员
                // 从缓存拿群成员 uid 列表（避免每次都查 join 表）
                memberUidList = groupMemberCache.getMemberUidList(room.getId());
            } else if (Objects.equals(room.getType(), RoomTypeEnum.FRIEND.getType())) {//单聊对象
                //对单人推送：单聊房间只存了两个人的 uid
                RoomFriend roomFriend = roomFriendDao.getByRoomId(room.getId());
                memberUidList = Arrays.asList(roomFriend.getUid1(), roomFriend.getUid2());
            }
            //更新所有群成员的会话时间（每个成员的“会话列表”排序靠它）
            contactDao.refreshOrCreateActiveTime(room.getId(), memberUidList, message.getId(), message.getCreateTime());
            //推送房间成员（再次发 MQ 到 websocket_push，由各实例推给本地连接）
            pushService.sendPushMsg(WSAdapter.buildMsgSend(msgResp), memberUidList);
        }
    }


}
