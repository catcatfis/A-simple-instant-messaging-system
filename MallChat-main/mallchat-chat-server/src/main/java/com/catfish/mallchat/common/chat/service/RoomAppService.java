package com.catfish.mallchat.common.chat.service;

import com.catfish.mallchat.common.chat.domain.vo.request.ChatMessageMemberReq;
import com.catfish.mallchat.common.chat.domain.vo.request.GroupAddReq;
import com.catfish.mallchat.common.chat.domain.vo.request.member.MemberAddReq;
import com.catfish.mallchat.common.chat.domain.vo.request.member.MemberDelReq;
import com.catfish.mallchat.common.chat.domain.vo.request.member.MemberReq;
import com.catfish.mallchat.common.chat.domain.vo.response.ChatMemberListResp;
import com.catfish.mallchat.common.chat.domain.vo.response.ChatRoomResp;
import com.catfish.mallchat.common.chat.domain.vo.response.MemberResp;
import com.catfish.mallchat.common.common.domain.vo.request.CursorPageBaseReq;
import com.catfish.mallchat.common.common.domain.vo.response.CursorPageBaseResp;
import com.catfish.mallchat.common.user.domain.vo.response.ws.ChatMemberResp;

import java.util.List;

/**
 * Description: 房间应用层服务接口，封装会话列表、群管理等业务
 */
public interface RoomAppService {
    /**
     * 获取会话列表--支持未登录态
     */
    CursorPageBaseResp<ChatRoomResp> getContactPage(CursorPageBaseReq request, Long uid);

    /**
     * 获取群组信息
     */
    MemberResp getGroupDetail(Long uid, long roomId);

    CursorPageBaseResp<ChatMemberResp> getMemberPage(MemberReq request);

    List<ChatMemberListResp> getMemberList(ChatMessageMemberReq request);

    void delMember(Long uid, MemberDelReq request);

    void addMember(Long uid, MemberAddReq request);

    Long addGroup(Long uid, GroupAddReq request);

    ChatRoomResp getContactDetail(Long uid, Long roomId);

    ChatRoomResp getContactDetailByFriend(Long uid, Long friendUid);
}
