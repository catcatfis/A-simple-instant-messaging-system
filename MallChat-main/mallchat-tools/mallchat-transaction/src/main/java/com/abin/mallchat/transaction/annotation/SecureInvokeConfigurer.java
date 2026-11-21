/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.abin.mallchat.transaction.annotation;

import org.springframework.lang.Nullable;

import java.util.concurrent.Executor;

/**
 * 自定义可靠调用执行线程池的接口。
 *
 * <p>容器里只能有一个实现（由 TransactionAutoConfiguration 校验）。
 * 本项目里 ThreadPoolConfig 实现了它，返回专门用于执行重试任务的线程池；
 * 如果不实现，则默认使用 ForkJoinPool.commonPool()。
 */

public interface SecureInvokeConfigurer {

    /**
     * 返回执行可靠调用（重试）任务的线程池
     */
    @Nullable
    default Executor getSecureInvokeExecutor() {
        return null;
    }

}
