package org.jeecg.modules.bems.visualization.tngl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 光伏发电量柱状图
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("光伏发电量柱状图")
public class PhotovoltaicPowerGenerationVO {

    @ApiModelProperty("周期：本周/本月/本年")
    private String period;

    @ApiModelProperty("X 轴时间标签")
    private List<String> xAxis;

    @ApiModelProperty("Y 轴发电量序列")
    private List<BigDecimal> series;
}
