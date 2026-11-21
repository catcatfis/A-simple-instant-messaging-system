package com.catfish.mallchat.common.common.event;

import com.catfish.mallchat.common.user.domain.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * UserBlackEvent
 */

@Getter
public class UserBlackEvent extends ApplicationEvent {
    private final User user;

    public UserBlackEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
