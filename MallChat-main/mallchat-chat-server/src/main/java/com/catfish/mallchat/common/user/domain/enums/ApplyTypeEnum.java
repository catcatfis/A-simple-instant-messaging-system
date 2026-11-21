package com.catfish.mallchat.common.user.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ApplyType 枚举
 */
@Getter
@AllArgsConstructor
public enum ApplyTypeEnum {

    ADD_FRIEND(1, "加好友");


    private final Integer code;

    private final String desc;
}
