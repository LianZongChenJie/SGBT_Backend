package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 生产概况（风电、光伏产能折线图）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("生产概况")
public class ProductionOverviewVO {

    @ApiModelProperty("周期：本周/本月/本年")
    private String period;

    @ApiModelProperty("光伏产能数据")
    private ChartDataVO pv;

    @ApiModelProperty("风电产能数据")
    private ChartDataVO wind;
}
