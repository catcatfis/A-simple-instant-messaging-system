package com.abin.mallchat.common.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * Description: 优惠券订单。
 * 主键由 Redis 全局唯一ID 生成，不使用自增。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tb_voucher_order")
public class VoucherOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id")
    private Long id;

    /**
     * 下单的用户id
     */
    private Long userId;

    /**
     * 购买的代金券id
     */
    private Long voucherId;

    /**
     * 支付方式 1 余额支付 2 支付宝 3 微信
     */
    private Integer payType;

    /**
     * 订单状态 1 未支付 2 已支付 3 已核销 4 已取消 5 退款中 6 已退款
     */
    private Integer status;

    private Date createTime;

    private Date payTime;

    private Date useTime;

    private Date refundTime;

    private Date updateTime;
}
