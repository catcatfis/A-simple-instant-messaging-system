package com.catfish.mallchat.common.user.service.impl;

import cn.hutool.core.util.StrUtil;
import com.catfish.mallchat.common.common.constant.RedisKey;
import com.catfish.mallchat.common.common.exception.BusinessException;
import com.catfish.mallchat.common.common.service.LockService;
import com.catfish.mallchat.common.common.utils.JwtUtils;
import com.abin.mallchat.utils.RedisUtils;
import com.catfish.mallchat.common.user.service.LoginService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Description: 登录相关处理类
 */
@Service
@Slf4j
public class LoginServiceImpl implements LoginService {

    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private LockService lockService;
    //token过期时间
    private static final Integer TOKEN_EXPIRE_DAYS = 5;
    //token续期时间
    private static final Integer TOKEN_RENEWAL_DAYS = 2;
    //token签发分布式锁的key前缀，按uid隔离
    private static final String TOKEN_LOCK_KEY = "mallchat:login:token:lock:";
    //等待锁的时间，超时说明并发签发正在进行，走降级复用逻辑
    private static final int LOCK_WAIT_MILLIS = 3000;

    /**
     * 校验token是不是有效
     *
     * @param token
     * @return
     */
    @Override
    public boolean verify(String token) {
        Long uid = jwtUtils.getUidOrNull(token);
        if (Objects.isNull(uid)) {
            return false;
        }
        String key = RedisKey.getKey(RedisKey.USER_TOKEN_STRING, uid);
        String realToken = RedisUtils.getStr(key);
        return Objects.equals(token, realToken);//有可能token失效了，需要校验是不是和最新token一致
    }

    @Async
    @Override
    public void renewalTokenIfNecessary(String token) {
        Long uid = jwtUtils.getUidOrNull(token);
        if (Objects.isNull(uid)) {
            return;
        }
        String key = RedisKey.getKey(RedisKey.USER_TOKEN_STRING, uid);
        long expireDays = RedisUtils.getExpire(key, TimeUnit.DAYS);
        if (expireDays == -2) {//不存在的key
            return;
        }
        if (expireDays < TOKEN_RENEWAL_DAYS) {//剩余有效期不足续期阈值时续期
            RedisUtils.expire(key, TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        }
    }

    @Override
    public String login(Long uid) {
        String key = RedisKey.getKey(RedisKey.USER_TOKEN_STRING, uid);
        //快路径：已有token直接复用，避免无谓加锁
        String token = RedisUtils.getStr(key);
        if (StrUtil.isNotBlank(token)) {
            return token;
        }
        try {
            //慢路径：分布式锁保证同一用户串行签发token，避免并发下各签一个、后写覆盖先写，
            //导致先返回给客户端的token在verify时与redis中的最新token不一致而失效
            return lockService.executeWithLock(TOKEN_LOCK_KEY + uid, LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS,
                    () -> issueTokenIfAbsent(key, uid));
        } catch (BusinessException e) {
            //获取锁失败，说明已有线程正在签发，直接复用其结果即可
            log.info("获取token签发锁失败,降级复用已有token uid:{}", uid);
            String concurrentToken = RedisUtils.getStr(key);
            if (StrUtil.isNotBlank(concurrentToken)) {
                return concurrentToken;
            }
            throw e;
        }
    }

    /**
     * 签发token，使用setIfAbsent保证只有第一个写入者生效（双重检查）
     */
    private String issueTokenIfAbsent(String key, Long uid) {
        //双重检查：等锁期间可能已被其他线程写入
        String existToken = RedisUtils.getStr(key);
        if (StrUtil.isNotBlank(existToken)) {
            return existToken;
        }
        String newToken = jwtUtils.createToken(uid);
        boolean success = RedisUtils.setIfAbsent(key, newToken, TOKEN_EXPIRE_DAYS, TimeUnit.DAYS);
        if (!success) {
            //极端情况下setIfAbsent被其他节点抢先写入，以已存在的token为准
            String concurrentToken = RedisUtils.getStr(key);
            if (StrUtil.isNotBlank(concurrentToken)) {
                return concurrentToken;
            }
        }
        return newToken;
    }

    @Override
    public Long getValidUid(String token) {
        boolean verify = verify(token);
        return verify ? jwtUtils.getUidOrNull(token) : null;
    }
}
