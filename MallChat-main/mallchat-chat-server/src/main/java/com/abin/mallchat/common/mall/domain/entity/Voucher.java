package com.abin.mallchat.common.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * Description: 优惠券（普通券 / 秒杀券）
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tb_voucher")
public class Voucher implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商铺id
     */
    private Long shopId;

    /**
     * 代金券标题
     */
    private String title;

    /**
     * 副标题
     */
    private String subTitle;

    /**
     * 使用规则
     */
    private String rules;

    /**
     * 支付金额，单位是分
     */
    private Long payValue;

    /**
     * 抵扣金额，单位是分
     */
    private Long actualValue;

    /**
     * 0 普通券；1 秒杀券
     */
    private Integer type;

    /**
     * 1 上架；2 下架；3 过期
     */
    private Integer status;

    private Date createTime;

    private Date updateTime;

    /**
     * 库存，非数据库字段。仅秒杀券返回时由 tb_seckill_voucher 填充
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Integer stock;

    /**
     * 生效时间，非数据库字段。仅秒杀券返回时填充
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Date beginTime;

    /**
     * 失效时间，非数据库字段。仅秒杀券返回时填充
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Date endTime;
}
