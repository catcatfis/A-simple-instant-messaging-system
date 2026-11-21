package com.abin.mallchat.transaction.dao;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.abin.mallchat.transaction.domain.entity.SecureInvokeRecord;
import com.abin.mallchat.transaction.mapper.SecureInvokeRecordMapper;
import com.abin.mallchat.transaction.service.SecureInvokeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 可靠调用记录的数据访问层（MyBatis-Plus 的 ServiceImpl，自带 save/getById/removeById 等方法）。
 */

@Component
public class SecureInvokeRecordDao extends ServiceImpl<SecureInvokeRecordMapper, SecureInvokeRecord> {

    /**
     * 查出所有“该重试了”的记录。
     *
     * <p>过滤条件三个都要满足：
     * <ol>
     *     <li>status = 1（待执行）；</li>
     *     <li>next_retry_time < 当前时间（到了重试时间）；</li>
     *     <li>create_time 早于 2 分钟前（避开刚入库、可能马上就会执行的记录）。</li>
     * </ol>
     */
    public List<SecureInvokeRecord> getWaitRetryRecords() {
        Date now = new Date();
        //查2分钟前的失败数据。避免刚入库的数据被查出来
        DateTime afterTime = DateUtil.offsetMinute(now, -(int) SecureInvokeService.RETRY_INTERVAL_MINUTES);
        return lambdaQuery()
                .eq(SecureInvokeRecord::getStatus, SecureInvokeRecord.STATUS_WAIT)
                .lt(SecureInvokeRecord::getNextRetryTime, new Date())
                .lt(SecureInvokeRecord::getCreateTime, afterTime)
                .list();
    }
}
