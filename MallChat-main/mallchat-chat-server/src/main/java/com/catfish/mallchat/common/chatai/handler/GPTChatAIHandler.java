package com.catfish.mallchat.common.chatai.handler;

import com.catfish.mallchat.common.chat.domain.entity.Message;
import com.catfish.mallchat.common.chat.domain.entity.msg.MessageExtra;
import com.catfish.mallchat.common.chatai.domain.ChatGPTContext;
import com.catfish.mallchat.common.chatai.domain.ChatGPTMsg;
import com.catfish.mallchat.common.chatai.domain.builder.ChatGPTContextBuilder;
import com.catfish.mallchat.common.chatai.domain.builder.ChatGPTMsgBuilder;
import com.catfish.mallchat.common.chatai.enums.ChatGPTRoleEnum;
import com.catfish.mallchat.common.chatai.properties.ChatGPTProperties;
import com.catfish.mallchat.common.common.constant.RedisKey;
import com.catfish.mallchat.common.common.domain.dto.FrequencyControlDTO;
import com.catfish.mallchat.common.common.exception.FrequencyControlException;
import com.catfish.mallchat.common.common.service.frequencycontrol.FrequencyControlUtil;
import com.abin.mallchat.utils.RedisUtils;
import com.catfish.mallchat.common.user.domain.vo.response.user.UserInfoResp;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.catfish.mallchat.common.common.constant.RedisKey.USER_CHAT_CONTEXT;
import static com.catfish.mallchat.common.common.service.frequencycontrol.FrequencyControlStrategyFactory.TOTAL_COUNT_WITH_IN_FIX_TIME_FREQUENCY_CONTROLLER;

/**
 * Description: AI 聊天处理器。
 * <p>
 * 基于 <b>LangChain4j</b> 的 {@link ChatModel} 实现，替代原先手写的 OkHttp + 手工 SSE 解析。
 * 收益：
 * <ul>
 *     <li>请求与响应处理由框架统一负责，无需自己解析流式分片（原先因空 choices 帧抛 NPE）</li>
 *     <li>切换任意 OpenAI 兼容服务只需改配置，业务代码无需改动</li>
 * </ul>
 * 业务侧仍自行维护：上下文裁剪、会话缓存、限流与消息落库。
 */
@Slf4j
@Component
public class GPTChatAIHandler extends AbstractChatAIHandler {
    /**
     * 限流前缀
     */
    private static final String CHAT_FREQUENCY_PREFIX = "GPTChatAIHandler";

    @Autowired
    private ChatGPTProperties chatGPTProperties;

    /**
     * LangChain4j 对话模型，由 ChatModelConfig 构建
     */
    @Autowired
    private ChatModel chatModel;

    private String aiName;

    @Override
    protected void init() {
        // 必须调用父类：父类会把自己注册进 ChatAIHandlerFactory，
        // 否则 ChatAIServiceImpl 通过 getChatAIHandlerById 找不到本 handler，AI 会被静默跳过
        super.init();
        if (!isUse()) {
            return;
        }
        UserInfoResp userInfo = userService.getUserInfo(chatGPTProperties.getAIUserId());
        if (userInfo == null) {
            log.error("根据AIUserId:{} 找不到用户信息", chatGPTProperties.getAIUserId());
            throw new RuntimeException("根据AIUserId: " + chatGPTProperties.getAIUserId() + " 找不到用户信息");
        }
        if (StringUtils.isBlank(userInfo.getName())) {
            log.warn("根据AIUserId:{} 找到的用户信息没有name", chatGPTProperties.getAIUserId());
            throw new RuntimeException("根据AIUserId: " + chatGPTProperties.getAIUserId() + " 找到的用户没有名字");
        }
        aiName = userInfo.getName();
        log.info("AI 聊天已启用, name={}, model={}", aiName, chatGPTProperties.getModelName());
    }

    @Override
    protected boolean isUse() {
        return chatGPTProperties.isUse();
    }

    @Override
    public Long getChatAIUserId() {
        return chatGPTProperties.getAIUserId();
    }

    @Override
    protected String doChat(Message message) {
        Long uid = message.getFromUid();
        try {
            FrequencyControlDTO frequencyControlDTO = new FrequencyControlDTO();
            frequencyControlDTO.setKey(RedisKey.getKey(CHAT_FREQUENCY_PREFIX) + ":" + uid);
            frequencyControlDTO.setUnit(TimeUnit.HOURS);
            frequencyControlDTO.setCount(chatGPTProperties.getLimit());
            frequencyControlDTO.setTime(1);
            return FrequencyControlUtil.executeWithFrequencyControl(TOTAL_COUNT_WITH_IN_FIX_TIME_FREQUENCY_CONTROLLER,
                    frequencyControlDTO,
                    () -> chatWithModel(message));
        } catch (FrequencyControlException e) {
            return "亲爱的,你今天找我聊了" + chatGPTProperties.getLimit() + "次了~人家累了~明天见";
        } catch (Throwable e) {
            log.warn("AI 聊天异常:", e);
            return "系统开小差啦~~";
        }
    }

    /**
     * 调用 LangChain4j 完成一轮对话，并把助手回复追加进上下文
     */
    private String chatWithModel(Message message) {
        ChatGPTContext context = buildContext(message);
        context = tailorContext(context);
        try {
            List<ChatMessage> messages = toLangChainMessages(context.getMsg());
            ChatResponse response = chatModel.chat(messages);
            String text = extractText(response);
            if (StringUtils.isBlank(text)) {
                log.warn("AI 返回内容为空, tokenUsage={}", response == null ? null : response.tokenUsage());
                return "我没想好怎么回答，换个问题试试？";
            }
            context.addMsg(ChatGPTMsgBuilder.assistantMsg(text));
            saveContext(context);
            return text;
        } catch (Exception e) {
            log.warn("调用大模型失败:", e);
            return "我累了，明天再聊吧";
        }
    }

    /**
     * 提取模型回复文本；推理模型可能只有 thinking 而无 content，此处统一兜底
     */
    private String extractText(ChatResponse response) {
        if (response == null || response.aiMessage() == null) {
            return null;
        }
        AiMessage aiMessage = response.aiMessage();
        if (StringUtils.isNotBlank(aiMessage.text())) {
            return aiMessage.text();
        }
        return aiMessage.thinking();
    }

    /**
     * 把内部上下文转换为 LangChain4j 的消息模型
     */
    private List<ChatMessage> toLangChainMessages(List<ChatGPTMsg> msgList) {
        List<ChatMessage> messages = new ArrayList<>(msgList.size());
        for (ChatGPTMsg msg : msgList) {
            if (msg == null || StringUtils.isBlank(msg.getContent())) {
                continue;
            }
            String role = msg.getRole();
            if (ChatGPTRoleEnum.SYSTEM.getRole().equals(role)) {
                messages.add(new SystemMessage(msg.getContent()));
            } else if (ChatGPTRoleEnum.ASSISTANT.getRole().equals(role)) {
                messages.add(new AiMessage(msg.getContent()));
            } else {
                messages.add(UserMessage.from(msg.getContent()));
            }
        }
        return messages;
    }

    /**
     * 上下文裁剪：按字符数粗略估算历史预算，超出则从最早的历史消息开始删除。
     * 说明：langchain4j 未暴露独立 tokenizer，这里用字符数估算（中文约 1 字 1 token 量级），
     * 取 maxTokens 的一半作为历史预算，为当前提问与模型回复留出空间。
     */
    private ChatGPTContext tailorContext(ChatGPTContext context) {
        List<ChatGPTMsg> msg = context.getMsg();
        int maxTokens = chatGPTProperties.getMaxTokens() == null ? 2048 : chatGPTProperties.getMaxTokens();
        int budget = Math.max(maxTokens / 2, 512);
        // 保留 index 0 的系统提示词，其余从最早的历史开始裁剪
        while (msg.size() > 2 && estimateLength(msg) > budget) {
            msg.remove(1);
        }
        return context;
    }

    private int estimateLength(List<ChatGPTMsg> msg) {
        int total = 0;
        for (ChatGPTMsg m : msg) {
            total += m.getContent() == null ? 0 : m.getContent().length();
        }
        return total;
    }

    private ChatGPTContext buildContext(Message message) {
        String prompt = message.getContent().replace("@" + aiName, "").trim();
        Long uid = message.getFromUid();
        Long roomId = message.getRoomId();
        ChatGPTContext chatGPTContext = RedisUtils.get(RedisKey.getKey(USER_CHAT_CONTEXT, uid, roomId), ChatGPTContext.class);
        if (chatGPTContext == null) {
            chatGPTContext = ChatGPTContextBuilder.initContext(uid, roomId);
        }
        saveContext(chatGPTContext);
        chatGPTContext.addMsg(ChatGPTMsgBuilder.userMsg(prompt));
        return chatGPTContext;
    }

    private void saveContext(ChatGPTContext chatGPTContext) {
        RedisUtils.set(RedisKey.getKey(USER_CHAT_CONTEXT, chatGPTContext.getUid(), chatGPTContext.getRoomId()),
                chatGPTContext, 5L, TimeUnit.MINUTES);
    }

    @Override
    protected boolean supports(Message message) {
        if (!chatGPTProperties.isUse()) {
            return false;
        }
        if (StringUtils.isBlank(aiName)) {
            return false;
        }
        MessageExtra extra = message.getExtra();
        if (extra == null) {
            return false;
        }
        if (CollectionUtils.isEmpty(extra.getAtUidList())) {
            return false;
        }
        if (!extra.getAtUidList().contains(chatGPTProperties.getAIUserId())) {
            return false;
        }
        if (StringUtils.isBlank(message.getContent())) {
            return false;
        }
        return StringUtils.contains(message.getContent(), "@" + aiName)
                && StringUtils.isNotBlank(message.getContent().replace(aiName, "").trim());
    }
}
