package org.jeecg.modules.bems.visualization.tngl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单个锅炉的能耗数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("锅炉能耗数据项")
public class BoilerEnergyConsumptionItemVO {

    @ApiModelProperty("设备ID")
    private Long deviceId;

    @ApiModelProperty("设备名称")
    private String deviceName;

    @ApiModelProperty("设备编码")
    private String deviceCode;

    @ApiModelProperty("能耗数据系列（水耗/汽耗）")
    private List<SeriesVO> series;
}
