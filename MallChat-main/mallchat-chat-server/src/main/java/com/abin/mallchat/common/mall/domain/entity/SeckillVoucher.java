package com.abin.mallchat.common.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * Description: 秒杀优惠券，与 {@link Voucher} 一对一
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tb_seckill_voucher")
public class SeckillVoucher implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 关联的优惠券id
     */
    @TableId(value = "voucher_id")
    private Long voucherId;

    /**
     * 库存
     */
    private Integer stock;

    /**
     * 生效时间
     */
    private Date beginTime;

    /**
     * 失效时间
     */
    private Date endTime;

    private Date createTime;

    private Date updateTime;
}
