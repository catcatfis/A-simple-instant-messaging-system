package com.catfish.mallchat.common.chat.service.strategy.msg;

import com.catfish.mallchat.common.common.exception.CommonErrorEnum;
import com.catfish.mallchat.common.common.utils.AssertUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Description: 消息类型处理器工厂，按消息类型路由到对应 Handler
 */
public class MsgHandlerFactory {
    private static final Map<Integer, AbstractMsgHandler> STRATEGY_MAP = new HashMap<>();

    public static void register(Integer code, AbstractMsgHandler strategy) {
        STRATEGY_MAP.put(code, strategy);
    }

    public static AbstractMsgHandler getStrategyNoNull(Integer code) {
        AbstractMsgHandler strategy = STRATEGY_MAP.get(code);
        AssertUtil.isNotEmpty(strategy, CommonErrorEnum.PARAM_VALID);
        return strategy;
    }
}
