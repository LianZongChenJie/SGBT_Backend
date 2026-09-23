package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 环境监测数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("环境监测数据")
public class EnvironmentalMonitoringVO {

    @ApiModelProperty("设备名：CEMS1/CEMS2/CEMS3")
    private String name;

    @ApiModelProperty("监测项列表")
    private List<EnvironmentalMonitoringItemVO> data;
}
