package com.catfish.mallchat.common.common.event;

import com.catfish.mallchat.common.user.domain.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * UserOnlineEvent
 */

@Getter
public class UserOnlineEvent extends ApplicationEvent {
    private final User user;

    public UserOnlineEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
