package com.catfish.mallchat.common.chat.controller;


import com.catfish.mallchat.common.chat.domain.dto.MsgReadInfoDTO;
import com.catfish.mallchat.common.chat.domain.vo.request.*;
import com.catfish.mallchat.common.chat.domain.vo.response.ChatMessageReadResp;
import com.catfish.mallchat.common.chat.domain.vo.response.ChatMessageResp;
import com.catfish.mallchat.common.chat.service.ChatService;
import com.catfish.mallchat.common.common.annotation.FrequencyControl;
import com.catfish.mallchat.common.common.domain.vo.response.ApiResult;
import com.catfish.mallchat.common.common.domain.vo.response.CursorPageBaseResp;
import com.catfish.mallchat.common.common.utils.RequestHolder;
import com.catfish.mallchat.common.user.domain.enums.BlackTypeEnum;
import com.catfish.mallchat.common.user.service.cache.UserCache;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 聊天消息相关的 HTTP 接口（群聊相关接口）。
 *
 * <p>新手建议从 {@link #sendMsg} 开始看，它是整个 MQ 链路的第一环：
 * 控制器 -> ChatService 存库 -> 发本地事件 -> 发 MQ -> 消费者推送。
 */
@RestController
@RequestMapping("/capi/chat")
@Api(tags = "聊天室相关接口")
@Slf4j
public class ChatController {
    @Autowired
    private ChatService chatService;
    @Autowired
    private UserCache userCache;

    /**
     * 拉黑名单（缓存）：当前用户拉黑的人发的消息不展示
     */
    private Set<String> getBlackUidSet() {
        return userCache.getBlackMap().getOrDefault(BlackTypeEnum.UID.getType(), new HashSet<>());
    }

    /**
     * 消息列表（游标分页，滚动加载历史消息）
     */
    @GetMapping("/public/msg/page")
    @ApiOperation("消息列表")
//    @FrequencyControl(time = 120, count = 20, target = FrequencyControl.Target.IP)
    public ApiResult<CursorPageBaseResp<ChatMessageResp>> getMsgPage(@Valid ChatMessagePageReq request) {
        CursorPageBaseResp<ChatMessageResp> msgPage = chatService.getMsgPage(request, RequestHolder.get().getUid());
        filterBlackMsg(msgPage);
        return ApiResult.success(msgPage);
    }

    /**
     * 过滤掉被拉黑用户发送的消息
     */
    private void filterBlackMsg(CursorPageBaseResp<ChatMessageResp> memberPage) {
        Set<String> blackMembers = getBlackUidSet();
        memberPage.getList().removeIf(a -> blackMembers.contains(a.getFromUser().getUid().toString()));
    }

    /**
     * 发送消息（MQ 链路入口）。
     *
     * <p>三个 @FrequencyControl 是限流：5 秒内最多 3 条、30 秒内最多 5 条、60 秒内最多 10 条，
     * 防止刷屏。
     *
     * <p>注意：chatService.sendMsg 只负责“校验 + 存库 + 发布事件”，
     * 真正的推送是异步的（由 MQ 消费者完成），所以这里返回的消息结构只是给发送方自己回显用的。
     */
    @PostMapping("/msg")
    @ApiOperation("发送消息")
    @FrequencyControl(time = 5, count = 3, target = FrequencyControl.Target.UID)
    @FrequencyControl(time = 30, count = 5, target = FrequencyControl.Target.UID)
    @FrequencyControl(time = 60, count = 10, target = FrequencyControl.Target.UID)
    public ApiResult<ChatMessageResp> sendMsg(@Valid @RequestBody ChatMessageReq request) {
        Long msgId = chatService.sendMsg(request, RequestHolder.get().getUid());
        //返回完整消息格式，方便前端展示
        return ApiResult.success(chatService.getMsgResp(msgId, RequestHolder.get().getUid()));
    }

    @PutMapping("/msg/mark")
    @ApiOperation("消息标记")
    @FrequencyControl(time = 10, count = 5, target = FrequencyControl.Target.UID)
    public ApiResult<Void> setMsgMark(@Valid @RequestBody ChatMessageMarkReq request) {
        chatService.setMsgMark(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @PutMapping("/msg/recall")
    @ApiOperation("撤回消息")
    @FrequencyControl(time = 20, count = 3, target = FrequencyControl.Target.UID)
    public ApiResult<Void> recallMsg(@Valid @RequestBody ChatMessageBaseReq request) {
        chatService.recallMsg(RequestHolder.get().getUid(), request);
        return ApiResult.success();
    }

    @GetMapping("/msg/read/page")
    @ApiOperation("消息的已读未读列表")
    public ApiResult<CursorPageBaseResp<ChatMessageReadResp>> getReadPage(@Valid ChatMessageReadReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(chatService.getReadPage(uid, request));
    }

    @GetMapping("/msg/read")
    @ApiOperation("获取消息的已读未读总数")
    public ApiResult<Collection<MsgReadInfoDTO>> getReadInfo(@Valid ChatMessageReadInfoReq request) {
        Long uid = RequestHolder.get().getUid();
        return ApiResult.success(chatService.getMsgReadInfo(uid, request));
    }

    @PutMapping("/msg/read")
    @ApiOperation("消息阅读上报")
    public ApiResult<Void> msgRead(@Valid @RequestBody ChatMessageMemberReq request) {
        Long uid = RequestHolder.get().getUid();
        chatService.msgRead(uid, request);
        return ApiResult.success();
    }
}

