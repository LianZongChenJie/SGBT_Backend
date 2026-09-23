package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 环境监测项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("环境监测项")
public class EnvironmentalMonitoringItemVO {

    @ApiModelProperty("监测项类型：二氧化碳浓度/粉尘浓度")
    private String type;

    @ApiModelProperty("详情数据")
    private List<EnvironmentalMonitoringDetailVO> detailData;
}
