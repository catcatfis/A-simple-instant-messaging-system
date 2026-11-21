package com.abin.mallchat.transaction.service;

import java.util.Objects;

/**
 * 可靠调用上下文持有者：用一个 ThreadLocal 标记“当前线程是不是正在重放任务”。
 *
 * <p>为什么需要它：重放时会再次调用带 @SecureInvoke 的方法，如果不加标记，
 * 切面又会把它写进表里，导致同一条任务反复入库、无限循环。
 * <p>ThreadLocal 存的是变量副本，所以每个线程互不影响；线程池复用线程，
 * 因此用完必须在 finally 里 remove（见 SecureInvokeService.doInvoke）。
 */

public class SecureInvokeHolder {
    private static final ThreadLocal<Boolean> INVOKE_THREAD_LOCAL = new ThreadLocal<>();

    /** 当前线程是否正在执行重放逻辑 */
    public static boolean isInvoking() {
        return Objects.nonNull(INVOKE_THREAD_LOCAL.get());
    }

    /** 标记开始重放 */
    public static void setInvoking() {
        INVOKE_THREAD_LOCAL.set(Boolean.TRUE);
    }

    /** 清除标记（务必在 finally 中调用） */
    public static void invoked() {
        INVOKE_THREAD_LOCAL.remove();
    }
}
