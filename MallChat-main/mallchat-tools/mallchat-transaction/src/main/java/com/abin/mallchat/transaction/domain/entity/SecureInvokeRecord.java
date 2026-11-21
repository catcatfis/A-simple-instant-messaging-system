package com.abin.mallchat.transaction.domain.entity;

import com.abin.mallchat.transaction.domain.dto.SecureInvokeDTO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 可靠消息本地调用记录实体，对应数据库表 secure_invoke_record（本地消息表）。
 *
 * <p>一条记录 = 一个“将来一定要执行的方法调用”。例如：
 * <pre>
 *   secure_invoke_json = { className: "...MQProducer", methodName: "sendSecureMsg", args: "[...]" }
 * </pre>
 * 事务提交后，程序就按这个 json 把方法重新执行一遍；执行成功就删记录，
 * 执行失败就更新 retry_times / next_retry_time，等定时任务重试。
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "secure_invoke_record", autoResultMap = true)
public class SecureInvokeRecord {
    /** 待执行：还没执行成功，定时任务会继续重试 */
    public final static byte STATUS_WAIT = 1;
    /** 已失败：超过最大重试次数，不再自动重试（需要人工介入排查） */
    public final static byte STATUS_FAIL = 2;
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * 请求快照参数json：记录了“调哪个类、哪个方法、什么参数”，重放时靠它还原
     */
    @TableField(value = "secure_invoke_json", typeHandler = JacksonTypeHandler.class)
    private SecureInvokeDTO secureInvokeDTO;
    /**
     * 状态 1待执行 2已失败
     */
    @TableField("status")
    @Builder.Default
    private byte status = SecureInvokeRecord.STATUS_WAIT;
    /**
     * 下一次重试的时间（到达这个时间后，定时任务才会把它捞出来重做）
     */
    @TableField("next_retry_time")
    @Builder.Default
    private Date nextRetryTime = new Date();
    /**
     * 已经重试的次数
     */
    @TableField("retry_times")
    @Builder.Default
    private Integer retryTimes = 0;
    /**
     * 最大重试次数，来自 @SecureInvoke(maxRetryTimes = ?)
     */
    @TableField("max_retry_times")
    private Integer maxRetryTimes;
    /**
     * 最后一次失败的原因，排障时看这个
     */
    @TableField("fail_reason")
    private String failReason;
    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;

}
