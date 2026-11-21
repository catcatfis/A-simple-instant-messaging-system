package com.catfish.mallchat.common.chatai.enums;

/**
 * ChatGPTRole 枚举
 */

public enum ChatGPTRoleEnum {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant");

    private final String role;

    ChatGPTRoleEnum(String role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }
}