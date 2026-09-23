package org.jeecg.modules.bems.visualization.tngl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 锅炉能耗（电、水、汽耗折线图）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("锅炉能耗")
public class BoilerEnergyConsumptionVO {

    @ApiModelProperty("周期：本周/本月/本年")
    private String period;

    @ApiModelProperty("各锅炉能耗数据")
    private List<BoilerEnergyConsumptionItemVO> boilers;
}
