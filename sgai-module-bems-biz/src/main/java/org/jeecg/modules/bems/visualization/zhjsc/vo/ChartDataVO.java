package org.jeecg.modules.bems.visualization.zhjsc.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 折线图数据：x 轴 + 数据系列
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("折线图数据")
public class ChartDataVO {

    @ApiModelProperty("X 轴时间标签")
    @JsonProperty("xAxis")
    private List<String> xAxis;

    @ApiModelProperty("Y 轴数据系列")
    private List<BigDecimal> series;
}
