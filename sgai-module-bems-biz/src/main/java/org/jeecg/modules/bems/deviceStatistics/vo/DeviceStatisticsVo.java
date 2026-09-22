package org.jeecg.modules.bems.deviceStatistics.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 设备统计结果
 */
@Data
@ApiModel(value = "DeviceStatisticsVo", description = "设备统计结果")
public class DeviceStatisticsVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备总数量
     */
    @ApiModelProperty(value = "设备总数量")
    private Long deviceCount;

    /**
     * 设备类别数量
     */
    @ApiModelProperty(value = "设备类别数量")
    private Long categoryCount;

    /**
     * 采集点位数量（设备属性数量）
     */
    @ApiModelProperty(value = "采集点位数量（设备属性数量）")
    private Long attributeCount;

    /**
     * 质量戳为“好的数据”的采集点数量
     */
    @ApiModelProperty(value = "质量戳为“好的数据”的采集点数量")
    private Long goodQualityCount;
}
