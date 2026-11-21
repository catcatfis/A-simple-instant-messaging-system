package com.catfish.mallchat.common.common.event;

import com.catfish.mallchat.common.chat.domain.dto.ChatMessageMarkDTO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * MessageMarkEvent
 */

@Getter
public class MessageMarkEvent extends ApplicationEvent {

    private final ChatMessageMarkDTO dto;

    public MessageMarkEvent(Object source, ChatMessageMarkDTO dto) {
        super(source);
        this.dto = dto;
    }

}
