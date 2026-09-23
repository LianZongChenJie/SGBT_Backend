package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 环境监测详情
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("环境监测详情")
public class EnvironmentalMonitoringDetailVO {

    @ApiModelProperty("周期标签：本周/本月/本年")
    private String label;

    @ApiModelProperty("监测值")
    private Double value;
}
