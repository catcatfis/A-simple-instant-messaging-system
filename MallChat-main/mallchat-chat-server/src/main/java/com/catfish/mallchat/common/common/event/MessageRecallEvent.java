package com.catfish.mallchat.common.common.event;

import com.catfish.mallchat.common.chat.domain.dto.ChatMsgRecallDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * MessageRecallEvent
 */

@Getter
public class MessageRecallEvent extends ApplicationEvent {

    private final ChatMsgRecallDTO recallDTO;

    public MessageRecallEvent(Object source, ChatMsgRecallDTO recallDTO) {
        super(source);
        this.recallDTO = recallDTO;
    }

}
