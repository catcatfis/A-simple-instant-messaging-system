package com.abin.mallchat.common.mall.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * Description: 商户
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("tb_shop")
public class Shop implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商铺名称
     */
    private String name;

    /**
     * 商铺类型的id
     */
    private Long typeId;

    /**
     * 商铺图片，多个图片以逗号隔开
     */
    private String images;

    /**
     * 商圈
     */
    private String area;

    /**
     * 地址
     */
    private String address;

    /**
     * 经度
     */
    private Double x;

    /**
     * 纬度
     */
    private Double y;

    /**
     * 均价，取整数
     */
    private Long avgPrice;

    /**
     * 销量
     */
    private Integer sold;

    /**
     * 评论数量
     */
    private Integer comments;

    /**
     * 评分，1~5分，乘10保存
     */
    private Integer score;

    /**
     * 营业时间
     */
    private String openHours;

    private Date createTime;

    private Date updateTime;

    /**
     * 距离，单位米。非数据库字段，由按距离查询时从 Redis GEO 结果回填
     */
    @TableField(exist = false)
    private Double distance;
}
