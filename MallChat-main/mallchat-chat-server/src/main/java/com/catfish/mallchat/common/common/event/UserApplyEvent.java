package com.catfish.mallchat.common.common.event;

import com.catfish.mallchat.common.user.domain.entity.UserApply;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * UserApplyEvent
 */

@Getter
public class UserApplyEvent extends ApplicationEvent {
    private UserApply userApply;

    public UserApplyEvent(Object source, UserApply userApply) {
        super(source);
        this.userApply = userApply;
    }

}
