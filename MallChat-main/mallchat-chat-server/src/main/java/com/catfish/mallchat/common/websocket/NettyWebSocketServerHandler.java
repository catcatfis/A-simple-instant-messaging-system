package com.catfish.mallchat.common.websocket;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import cn.hutool.json.JSONUtil;
import com.catfish.mallchat.common.user.domain.enums.WSReqTypeEnum;
import com.catfish.mallchat.common.user.domain.vo.request.ws.WSAuthorize;
import com.catfish.mallchat.common.user.domain.vo.request.ws.WSBaseReq;
import com.catfish.mallchat.common.user.service.WebSocketService;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * NettyWebSocketServer 处理器
 */

@Slf4j
@Sharable
public class NettyWebSocketServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private WebSocketService webSocketService;

    // 当web客户端连接后，触发该方法
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.webSocketService = getService();
    }

    // 客户端离线
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        userOffLine(ctx);
    }

    /**
     * 取消绑定
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 可能出现业务判断离线后再次触发 channelInactive
        log.warn("触发 channelInactive 掉线![{}]", ctx.channel().id());
        userOffLine(ctx);
    }

    private void userOffLine(ChannelHandlerContext ctx) {
        this.webSocketService.removed(ctx.channel());
        ctx.channel().close();
    }

    /**
     * 心跳检查
     *
     * @param ctx
     * @param evt
     * @throws Exception
     */
    /**
     * 心跳与协议事件处理
     * <p>
     * 读空闲：长时间没收到任何数据（包括 Pong 帧），说明对端已失联，主动关闭连接；
     * 写空闲：长时间没有数据下发，主动发一个 Ping 帧探活。客户端（浏览器）会自动回 Pong，
     * Pong 属于入站数据，会重置 IdleStateHandler 的读计时，因此无需额外维护等待标记。
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                // 读空闲：对端已失联（含未回 Pong），关闭连接并触发下线
                log.warn("读空闲超时，关闭连接 [{}]", ctx.channel().id());
                userOffLine(ctx);
            } else if (idleStateEvent.state() == IdleState.WRITER_IDLE) {
                // 写空闲：发 Ping 探活，避免长连接被中间设备静默断开
                log.debug("写空闲，发送 Ping 帧 [{}]", ctx.channel().id());
                ctx.writeAndFlush(new PingWebSocketFrame());
            }
        } else if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            this.webSocketService.connect(ctx.channel());
            String token = NettyUtil.getAttr(ctx.channel(), NettyUtil.TOKEN);
            if (StrUtil.isNotBlank(token)) {
                this.webSocketService.authorize(ctx.channel(), new WSAuthorize(token));
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    // 处理异常
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn("异常发生，异常消息 ={}", cause);
        ctx.channel().close();
    }

    /**
     * Pong 帧必须在这里消费掉，否则会继续传递到 channelRead0，
     * 导致 SimpleChannelInboundHandler&lt;TextWebSocketFrame&gt; 强转失败。
     * 这里只记录探活成功，读计时的重置由 IdleStateHandler 自动完成。
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof PongWebSocketFrame) {
            log.debug("收到 Pong 帧，连接存活 [{}]", ctx.channel().id());
            return;
        }
        super.channelRead(ctx, msg);
    }

    private WebSocketService getService() {
        return SpringUtil.getBean(WebSocketService.class);
    }

    // 读取客户端发送的请求报文
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) throws Exception {
        WSBaseReq wsBaseReq;
        try {
            wsBaseReq = JSONUtil.toBean(msg.text(), WSBaseReq.class);
        } catch (Exception e) {
            // 单条畸形报文不应导致整条连接被关闭
            log.warn("非法 WS 报文，已忽略: {}", msg.text(), e);
            return;
        }
        WSReqTypeEnum wsReqTypeEnum = WSReqTypeEnum.of(wsBaseReq.getType());
        if (wsReqTypeEnum == null) {
            log.info("未知 WS 消息类型: {}", wsBaseReq.getType());
            return;
        }
        switch (wsReqTypeEnum) {
            case LOGIN:
                this.webSocketService.handleLoginReq(ctx.channel());
                log.info("请求二维码 = " + msg.text());
                break;
            case AUTHORIZE:
                // 首条消息鉴权：避免把 token 拼在 URL 上（会进入网关/代理访问日志）
                this.webSocketService.authorize(ctx.channel(), buildAuthorize(wsBaseReq.getData()));
                break;
            case HEARTBEAT:
                break;
            default:
                log.info("未知类型");
        }
    }

    /**
     * 解析鉴权报文体。前端发送形态为 {"type":3,"data":{"token":"xxx"}}，
     * 兼容直接传字符串 token 的写法。
     */
    private WSAuthorize buildAuthorize(String data) {
        if (StrUtil.isBlank(data)) {
            return new WSAuthorize(null);
        }
        String trimmed = data.trim();
        if (trimmed.startsWith("{")) {
            return JSONUtil.toBean(trimmed, WSAuthorize.class);
        }
        return new WSAuthorize(trimmed);
    }
}
