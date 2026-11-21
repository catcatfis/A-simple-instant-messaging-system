package com.catfish.mallchat.common.chatai.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * ChatGPTMsg
 */

@Getter
@Setter
@ToString
public class ChatGPTMsg implements Serializable {

    private String role;

    private String content;


}
