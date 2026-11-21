package com.catfish.mallchat.common.common.intecepter;

import com.catfish.mallchat.common.common.constant.MDCKey;
import com.catfish.mallchat.common.common.exception.HttpErrorEnum;
import com.catfish.mallchat.common.user.service.LoginService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;
import java.util.Optional;

/**
 * TokenInterceptor
 */

@Order(-2)
@Slf4j
@Component
public class TokenInterceptor implements HandlerInterceptor {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String AUTHORIZATION_SCHEMA = "Bearer ";
    public static final String ATTRIBUTE_UID = "uid";

    @Autowired
    private LoginService loginService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取用户登录token
        String token = getToken(request);
        Long validUid = loginService.getValidUid(token);
        if (Objects.nonNull(validUid)) {//有登录态
            request.setAttribute(ATTRIBUTE_UID, validUid);
        } else {
            boolean isPublicURI = isPublicURI(request.getRequestURI());
            if (!isPublicURI) {//又没有登录态，又不是公开路径，直接401
                HttpErrorEnum.ACCESS_DENIED.sendHttpError(response);
                return false;
            }
        }
        MDC.put(MDCKey.UID, String.valueOf(validUid));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        MDC.remove(MDCKey.UID);
    }

    /**
     * 判断是不是公共方法，可以未登录访问的
     *
     * @param requestURI
     */
    private boolean isPublicURI(String requestURI) {
        // 本地存储的裸 PUT 上传端点：前端 PUT 二进制时不带 token，
        // 因此该端点改由 OssUploadTicket 票据鉴权（签名+过期+路径绑定），而非会话 token。
        // 下载仍走明文 URL（消息图片需要被 <img> 直接引用），但对象名含随机 uuid，不可枚举。
        if (requestURI != null && requestURI.startsWith("/oss/upload/")) {
            return true;
        }
        String[] split = requestURI.split("/");
        return split.length > 3 && "public".equals(split[3]);
    }

    private String getToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        return Optional.ofNullable(header)
                .filter(h -> h.startsWith(AUTHORIZATION_SCHEMA))
                .map(h -> h.substring(AUTHORIZATION_SCHEMA.length()))
                .orElse(null);
    }
}