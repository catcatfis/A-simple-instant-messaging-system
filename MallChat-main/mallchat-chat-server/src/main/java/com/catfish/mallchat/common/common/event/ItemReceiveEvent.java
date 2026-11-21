package com.catfish.mallchat.common.common.event;

import com.catfish.mallchat.common.user.domain.entity.UserBackpack;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * ItemReceiveEvent
 */

@Getter
public class ItemReceiveEvent extends ApplicationEvent {
    private UserBackpack userBackpack;

    public ItemReceiveEvent(Object source, UserBackpack userBackpack) {
        super(source);
        this.userBackpack = userBackpack;
    }

}
