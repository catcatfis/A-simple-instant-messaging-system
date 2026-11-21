package com.catfish.mallchat.common.common.event;

import com.catfish.mallchat.common.user.domain.entity.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * UserOfflineEvent
 */

@Getter
public class UserOfflineEvent extends ApplicationEvent {
    private final User user;

    public UserOfflineEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
