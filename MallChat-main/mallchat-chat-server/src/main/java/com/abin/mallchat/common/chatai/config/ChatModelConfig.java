package com.abin.mallchat.common.chatai.config;

import com.abin.mallchat.common.chatai.properties.ChatGPTProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Description: AI 聊天模型配置。
 * <p>
 * 使用 LangChain4j 作为统一接入层，替代原先手写的 OkHttp + 手工 SSE 解析，
 * 因此任何 OpenAI 兼容服务（DeepSeek / 通义 / 智谱 / OpenAI 等）都只需改配置。
 * <p>
 * 注意 baseUrl 与完整接口地址的区别：
 * LangChain4j 的 baseUrl 是「根地址」，SDK 内部会自行拼接 /chat/completions，
 * 而本项目的 mallchat.chatgpt.proxyUrl 历史上填的是「完整地址」，
 * 因此这里做了兼容转换，避免线上配置写全路径时出现 /chat/completions/chat/completions。
 */
@Slf4j
@Configuration
public class ChatModelConfig {

    /**
     * OpenAI 兼容接口的路径后缀，若配置里带了需要剥离
     */
    private static final String COMPLETIONS_SUFFIX = "/chat/completions";

    @Bean
    public ChatModel chatModel(ChatGPTProperties properties) {
        String baseUrl = normalizeBaseUrl(properties.getProxyUrl());
        Duration timeout = Duration.ofMillis(properties.getTimeout() == null ? 60000 : properties.getTimeout());
        log.info("初始化 LangChain4j ChatModel: baseUrl={}, model={}, timeout={}ms, maxTokens={}",
                baseUrl, properties.getModelName(), timeout.toMillis(), properties.getMaxTokens());
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(properties.getKey())
                .modelName(properties.getModelName())
                .timeout(timeout)
                // reasoning 模型（如 deepseek 系列）的思考过程同样计入 maxTokens，
                // 取值过小会导致 content 为空，因此这里保证一个下限
                .maxTokens(Math.max(properties.getMaxTokens() == null ? 0 : properties.getMaxTokens(), 2048))
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * 把「完整接口地址」归一化为「根地址」，同时兼容两种写法
     */
    static String normalizeBaseUrl(String proxyUrl) {
        if (StringUtils.isBlank(proxyUrl)) {
            return "https://api.openai.com/v1";
        }
        String url = proxyUrl.trim();
        // 去掉结尾斜杠
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith(COMPLETIONS_SUFFIX)) {
            url = url.substring(0, url.length() - COMPLETIONS_SUFFIX.length());
        }
        return url;
    }
}
