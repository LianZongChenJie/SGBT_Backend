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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 监控点资源表
 *
 * @author bems
 */
@Data
@TableName("table_camera_resource")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "table_camera_resource对象", description = "监控点资源表")
public class CameraResource implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /** 唯一编码 */
    @ApiModelProperty(value = "唯一编码")
    private String indexCode;

    /** 资源类型 */
    @ApiModelProperty(value = "资源类型")
    private String resourceType;

    /** 监控点国标编号 */
    @ApiModelProperty(value = "监控点国标编号")
    private String externalIndexCode;

    /** 资源名称 */
    @ApiModelProperty(value = "资源名称")
    private String name;

    /** 通道号，为级联监控点时该字段为空；本级监控点时非空 */
    @ApiModelProperty(value = "通道号")
    private Integer chanNum;

    /** 级联编号 */
    @ApiModelProperty(value = "级联编号")
    private String cascadeCode;

    /** 父级资源编号 */
    @ApiModelProperty(value = "父级资源编号")
    private String parentIndexCode;

    /** 经度，精确到小数点后8位 */
    @ApiModelProperty(value = "经度")
    private BigDecimal longitude;

    /** 纬度，精确到小数点后8位 */
    @ApiModelProperty(value = "纬度")
    private BigDecimal latitude;

    /** 海拔高度，单位：米 */
    @ApiModelProperty(value = "海拔高度")
    private String elevation;

    /** 监控点类型：0-枪机，1-半球，2-快球，3-带云台枪机 */
    @ApiModelProperty(value = "监控点类型")
    private Integer cameraType;

    /** 能力集 */
    @ApiModelProperty(value = "能力集")
    private String capability;

    /** 录像存储位置 */
    @ApiModelProperty(value = "录像存储位置")
    private String recordLocation;

    /** 通道子类型：analog-模拟通道，digital-数字通道，mirror-镜像通道，record-录播通道，zero-零通道 */
    @ApiModelProperty(value = "通道子类型")
    private String channelType;

    /** 所属区域 */
    @ApiModelProperty(value = "所属区域")
    private String regionIndexCode;

    /** 所属区域目录，以@符号分割，包含本节点 */
    @ApiModelProperty(value = "所属区域目录")
    private String regionPath;

    /** 传输协议：0-UDP，1-TCP */
    @ApiModelProperty(value = "传输协议")
    private Integer transType;

    /** 接入协议 */
    @ApiModelProperty(value = "接入协议")
    private String treatyType;

    /** 安装位置 */
    @ApiModelProperty(value = "安装位置")
    private String installLocation;

    /** 创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 数据在界面上的显示顺序 */
    @ApiModelProperty(value = "显示顺序")
    private Integer disOrder;

    /** 资源唯一编码 */
    @ApiModelProperty(value = "资源唯一编码")
    private String resourceIndexCode;

    /** 解码模式 */
    @ApiModelProperty(value = "解码模式")
    private String decodeTag;

    /** 监控点关联对讲的唯一标志 */
    @ApiModelProperty(value = "监控点关联对讲唯一标志")
    private String cameraRelateTalk;

    /** 所属区域目录，由唯一标示组成，最大10级 */
    @ApiModelProperty(value = "区域名称")
    private String regionName;

    /** 区域目录名称，"/"分隔 */
    @ApiModelProperty(value = "区域目录名称")
    private String regionPathName;

    /** 记录创建时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "记录创建时间")
    private Date gmtCreate;

    /** 记录更新时间 */
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "记录更新时间")
    private Date gmtModified;

    /** 在线状态，0-离线，1-在线 */
    @ApiModelProperty(value = "在线状态，0-离线，1-在线")
    private Integer online;
}
