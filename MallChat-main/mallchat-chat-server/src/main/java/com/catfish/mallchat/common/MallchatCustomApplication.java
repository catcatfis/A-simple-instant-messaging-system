package com.catfish.mallchat.common;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * MallChat 服务启动入口。
 * <p>
 * 说明：业务代码位于 {@code com.catfish.mallchat}，工具组件（starter）位于 {@code com.abin.mallchat}。
 * scanBasePackages 必须同时包含两者，否则业务 Bean / 组件内的 Mapper 不会被扫描到。
 * 工具组件的自动配置通过 META-INF/spring.factories 注册。
 *
 * <p>新手看代码的顺序建议：
 * <ol>
 *     <li>本类：应用从哪里启动，扫描了哪些包；</li>
 *     <li>MallChat-代码审查报告 / 改造实施记录 等文档：了解整体结构；</li>
 *     <li>sendMsg 链路：ChatController -> ChatServiceImpl -> MessageSendListener
 *         -> MQProducer -> MsgSendConsumer -> PushService -> PushConsumer -> WebSocket；</li>
 *     <li>扫码登录链路：WxMsgService ->（MQ）-> MsgLoginConsumer/ScanSuccessConsumer
 *         -> WebSocketServiceImpl。</li>
 * </ol>
 */

@SpringBootApplication(scanBasePackages = {"com.catfish.mallchat", "com.abin.mallchat"})
// 第二个包是本地消息表（可靠发送 MQ）所在的包，千万别删，否则 SecureInvokeRecordMapper 注入失败
@MapperScan({"com.catfish.mallchat.common.**.mapper", "com.abin.mallchat.transaction.mapper"})
@ServletComponentScan
public class MallchatCustomApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallchatCustomApplication.class,args);
    }

}