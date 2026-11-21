package com.catfish.mallchat.common.user.service;

/**
 * Ip 服务接口
 */

public interface IpService {
    /**
     * 异步更新用户ip详情
     *
     * @param uid
     */
    void refreshIpDetailAsync(Long uid);
}
