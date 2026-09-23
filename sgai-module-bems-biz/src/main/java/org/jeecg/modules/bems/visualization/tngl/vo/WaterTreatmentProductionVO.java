package org.jeecg.modules.bems.visualization.tngl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 水处理生产指标（原水输入/一次成水量/二次成水量）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("水处理生产指标")
public class WaterTreatmentProductionVO {

    @ApiModelProperty("周期：本周/本月/本年")
    private String period;

    @ApiModelProperty("X 轴时间标签")
    private List<String> xAxis;

    @ApiModelProperty("数据系列")
    private List<SeriesVO> series;
}
