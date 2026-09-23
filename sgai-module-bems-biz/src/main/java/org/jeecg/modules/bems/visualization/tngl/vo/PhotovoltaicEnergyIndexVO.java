package org.jeecg.modules.bems.visualization.tngl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 光伏能源指标
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("光伏能源指标")
public class PhotovoltaicEnergyIndexVO {

    @ApiModelProperty("周期：本周/本月/本年")
    private String period;

    @ApiModelProperty("累计光伏发电量")
    private BigDecimal totalPowerGeneration;

    @ApiModelProperty("碳排放量（kg CO₂）")
    private BigDecimal carbonEmission;
}
