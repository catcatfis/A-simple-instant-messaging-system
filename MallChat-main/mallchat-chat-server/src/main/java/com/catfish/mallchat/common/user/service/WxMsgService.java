package com.catfish.mallchat.common.user.service;

import cn.hutool.core.util.RandomUtil;
import com.catfish.mallchat.common.common.constant.MQConstant;
import com.catfish.mallchat.common.common.constant.RedisKey;
import com.catfish.mallchat.common.common.domain.dto.LoginMessageDTO;
import com.catfish.mallchat.common.common.domain.dto.ScanSuccessMessageDTO;
import com.abin.mallchat.utils.RedisUtils;
import com.catfish.mallchat.common.user.dao.UserDao;
import com.catfish.mallchat.common.user.domain.entity.User;
import com.catfish.mallchat.common.user.service.adapter.TextBuilder;
import com.catfish.mallchat.common.user.service.adapter.UserAdapter;
import com.abin.mallchat.transaction.service.MQProducer;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 处理与微信接口的交互逻辑（扫码登录的起点）。
 *
 * <p>扫码登录的三个步骤，也就是本类的三个 MQ 使用点：
 * <ol>
 *     <li>用户扫描二维码 -> {@link #scan}：如果已注册，发<b>登录消息</b>（LOGIN_MSG_TOPIC）；
 *         否则发<b>扫码成功消息</b>（SCAN_MSG_TOPIC），并回复微信一条授权链接；</li>
 *     <li>用户在微信里点授权 -> {@link #authorize}：更新用户信息，再发<b>登录消息</b>（LOGIN_MSG_TOPIC）。</li>
 * </ol>
 * 为什么不直接调用 WebSocketService 推送？因为微信回调可能落在另一台服务器上，
 * 只有广播 MQ 才能找到“持有那条 WebSocket 连接”的实例。
 */
@Service
@Slf4j
public class WxMsgService {
    /**
     * 用户的openId和前端登录场景code的映射关系
     */
    private static final String URL = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_userinfo&state=STATE#wechat_redirect";
    @Value("${wx.mp.callback}")
    private String callback;
    @Autowired
    private UserDao userDao;
    @Autowired
    private UserService userService;
    @Autowired
    private MQProducer mqProducer;

    /**
     * 处理“用户扫描二维码”事件。
     *
     * @param wxMpService     微信 SDK 的服务对象
     * @param wxMpXmlMessage  微信推送的 XML 消息（含 openid、带场景值的 EventKey）
     * @return 需要回复给微信用户的文本消息；已登录时返回 null
     */
    public WxMpXmlOutMessage scan(WxMpService wxMpService, WxMpXmlMessage wxMpXmlMessage) {
        String openid = wxMpXmlMessage.getFromUser();
        // 二维码里的场景值就是前端生成的登录码，用来和 WebSocket 连接绑定
        Integer loginCode = Integer.parseInt(getEventKey(wxMpXmlMessage));
        User user = userDao.getByOpenId(openid);
        //如果已经注册,直接登录成功
        if (Objects.nonNull(user) && StringUtils.isNotEmpty(user.getAvatar())) {
            // 发送登录消息到 MQ，由持有该登录码连接的实例完成登录
            mqProducer.sendMsg(MQConstant.LOGIN_MSG_TOPIC, new LoginMessageDTO(user.getId(), loginCode));
            return null;
        }

        //user为空先注册,手动生成,以保存uid
        if (Objects.isNull(user)) {
            user = User.builder().openId(openid).build();
            userService.register(user);
        }
        //在redis中保存openid和场景code的关系，后续才能通知到前端,旧版数据没有清除,这里设置了过期时间
        RedisUtils.set(RedisKey.getKey(RedisKey.OPEN_ID_STRING, openid), loginCode, 60, TimeUnit.MINUTES);
        //授权流程,给用户发送授权消息，并且异步通知前端扫码成功,等待授权
        // 广播 MQ：让持有该登录码连接的那台服务器给前端推一条“已扫码”
        mqProducer.sendMsg(MQConstant.SCAN_MSG_TOPIC, new ScanSuccessMessageDTO(loginCode));
        String skipUrl = String.format(URL, wxMpService.getWxMpConfigStorage().getAppId(), URLEncoder.encode(callback + "/wx/portal/public/callBack"));
        WxMpXmlOutMessage.TEXT().build();
        return new TextBuilder().build("请点击链接授权：<a href=\"" + skipUrl + "\">登录</a>", wxMpXmlMessage, wxMpService);
    }

    private String getEventKey(WxMpXmlMessage wxMpXmlMessage) {
        //扫码关注的渠道事件有前缀，需要去除
        return wxMpXmlMessage.getEventKey().replace("qrscene_", "");
    }

    /**
     * 用户授权（微信回调），拿到微信用户信息后触发真正的登录。
     *
     * @param userInfo 微信授权返回的用户信息
     */
    public void authorize(WxOAuth2UserInfo userInfo) {
        User user = userDao.getByOpenId(userInfo.getOpenid());
        //更新用户信息
        if (StringUtils.isEmpty(user.getName())) {
            fillUserInfo(user.getId(), userInfo);
        }
        // 从 redis 找回“这次授权对应前端哪个二维码/登录码”
        Integer code = RedisUtils.get(RedisKey.getKey(RedisKey.OPEN_ID_STRING, userInfo.getOpenid()), Integer.class);
        //发送登录成功事件（发 MQ，由持有该连接的实例下发 token）
        mqProducer.sendMsg(MQConstant.LOGIN_MSG_TOPIC, new LoginMessageDTO(user.getId(), code));
    }

    private void fillUserInfo(Long uid, WxOAuth2UserInfo userInfo) {
        User update = UserAdapter.buildAuthorizeUser(uid, userInfo);
        for (int i = 0; i < 5; i++) {
            try {
                userDao.updateById(update);
                return;
            } catch (DuplicateKeyException e) {
                log.info("fill userInfo duplicate uid:{},info:{}", uid, userInfo);
            } catch (Exception e) {
                log.error("fill userInfo fail uid:{},info:{}", uid, userInfo);
            }
            update.setName("名字重置" + RandomUtil.randomInt(100000));
        }
    }
}
