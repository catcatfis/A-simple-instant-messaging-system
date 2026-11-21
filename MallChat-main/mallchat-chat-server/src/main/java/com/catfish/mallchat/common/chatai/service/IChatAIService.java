package com.catfish.mallchat.common.chatai.service;

import com.catfish.mallchat.common.chat.domain.entity.Message;

/**
 * IChatAI 服务接口
 */

public interface IChatAIService {

    void chat(Message message);
}
