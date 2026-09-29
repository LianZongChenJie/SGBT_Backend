package org.jeecg.modules.bems.visualization.tngl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("锅炉能碳指标，统计1个小时内的数据")
public class BoilerCarbonEmissionsIndexVo {
    @ApiModelProperty("设备名称")
    private String name;

    @ApiModelProperty("值")
    private BigDecimal value;
}
