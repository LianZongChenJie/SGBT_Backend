package org.jeecg.modules.bems.hikvision.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.util.Date;

/**
 * 摄像头列表VO，供前端展示摄像头列表
 *
 * @author bems
 */
@Data
public class CameraListVO {

    @Excel(name = "唯一编码", width = 25)
    @ApiModelProperty(value = "唯一编码")
    private String indexCode;

    @Excel(name = "资源名称", width = 30)
    @ApiModelProperty(value = "资源名称")
    private String name;

    @Excel(name = "监控点类型", width = 15, replace = {"枪机_0", "半球_1", "快球_2", "带云台枪机_3"})
    @ApiModelProperty(value = "监控点类型：0-枪机，1-半球，2-快球，3-带云台枪机")
    private Integer cameraType;

    @Excel(name = "安装位置", width = 25)
    @ApiModelProperty(value = "安装位置")
    private String installLocation;

    @ApiModelProperty(value = "所属区域")
    private String regionIndexCode;

    @Excel(name = "所属分组", width = 25)
    @ApiModelProperty(value = "所属区域名称")
    private String regionName;

    @Excel(name = "经度", width = 15)
    @ApiModelProperty(value = "经度")
    private String longitude;

    @Excel(name = "纬度", width = 15)
    @ApiModelProperty(value = "纬度")
    private String latitude;

    @ApiModelProperty(value = "通道子类型")
    private String channelType;

    @Excel(name = "在线状态", width = 12, replace = {"在线_1", "离线_0"})
    @ApiModelProperty(value = "在线状态，0-离线，1-在线")
    private Integer online;

    @ApiModelProperty(value = "监控点国标编号")
    private String externalIndexCode;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;
}
