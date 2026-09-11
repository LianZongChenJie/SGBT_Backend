package org.jeecg.modules.bems.hikvision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * 区域资源表
 *
 * @author bems
 */
@Data
@TableName("table_region_resource")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "table_region_resource对象", description = "区域资源表")
public class RegionResource implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /** 区域编号 */
    @ApiModelProperty(value = "区域编号")
    private String indexCode;

    /** 区域名称 */
    @ApiModelProperty(value = "区域名称")
    private String name;

    /** 区域完整目录，含本节点，/进行分割，上级节点在前 */
    @ApiModelProperty(value = "区域完整目录")
    private String regionPath;

    /** 父区域唯一标识码 */
    @ApiModelProperty(value = "父区域唯一标识码")
    private String parentIndexCode;

    /** 是否有权限操作：1-有权限，0-无权限 */
    @ApiModelProperty(value = "是否有权限操作")
    private Integer available;

    /** 是否叶子节点：1-是叶子节点（该区域下未挂区域），0-不是叶子节点（该区域下挂有区域） */
    @ApiModelProperty(value = "是否叶子节点")
    private Integer leaf;

    /** 级联平台标识，多个级联编号以@分隔，本级区域默认值"0" */
    @ApiModelProperty(value = "级联平台标识")
    private String cascadeCode;

    /** 区域标识：0-本级，1-级联，2-混合 */
    @ApiModelProperty(value = "区域标识")
    private Integer cascadeType;

    /** 区域类型：0-国标区域，1-雪亮工程区域，2-司法行政区域，9-自定义区域，10-历史兼容普通区域，11-历史兼容级联区域，12-楼栋单元 */
    @ApiModelProperty(value = "区域类型")
    private Integer catalogType;

    /** 外码（如：国际码） */
    @ApiModelProperty(value = "外码")
    private String externalIndexCode;

    /** 父外码（如：国际码） */
    @ApiModelProperty(value = "父外码")
    private String parentExternalIndexCode;

    /** 同级区域顺序 */
    @ApiModelProperty(value = "同级区域顺序")
    private Integer sort;

    /** 本区域资源数量（只统计本级挂的资源数量，不包含下级及下下级等） */
    @ApiModelProperty(value = "本区域资源数量")
    private Integer localQuantity;

    /** 本区域及下级区域资源数量（包含本级及下级） */
    @ApiModelProperty(value = "本区域及下级区域资源数量")
    private Integer totalQuantity;

    /** 创建时间（ISO8601格式） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新时间（ISO8601格式） */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 记录创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "记录创建时间")
    private Date gmtCreate;

    /** 记录更新时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "记录更新时间")
    private Date gmtModified;
}
