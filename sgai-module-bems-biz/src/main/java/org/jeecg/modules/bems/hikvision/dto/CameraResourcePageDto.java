package org.jeecg.modules.bems.hikvision.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 摄像头分页查询DTO
 *
 * @author bems
 */
@Data
public class CameraResourcePageDto {

    @ApiModelProperty(value = "当前页码")
    private int pageNo = 1;

    @ApiModelProperty(value = "每页大小")
    private int pageSize = 10;

    @ApiModelProperty(value = "唯一编码（精确查询）")
    private String indexCode;

    @ApiModelProperty(value = "资源名称（模糊查询）")
    private String name;

    @ApiModelProperty(value = "区域名称（模糊查询）")
    private String regionName;

    @ApiModelProperty(value = "接入协议（精确查询）")
    private String treatyType;

    @ApiModelProperty(value = "安装位置（模糊查询）")
    private String installLocation;

    @ApiModelProperty(value = "在线状态，0-离线，1-在线（精确查询）")
    private Integer online;

    @ApiModelProperty(value = "监控点类型：0-枪机，1-半球，2-快球，3-带云台枪机（精确查询）")
    private Integer cameraType;
}
