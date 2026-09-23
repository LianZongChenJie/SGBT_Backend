package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 碳足迹
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("碳足迹")
public class CarbonFootprintVO {

    @ApiModelProperty("碳排放总量")
    private BigDecimal totalCarbonEmissions;

    @ApiModelProperty("等效植树林（棵）")
    private BigDecimal equivalentPlantations;

    @ApiModelProperty("再利用能源减排量（tCO2e）")
    private BigDecimal reuseEnergySavings;

    @ApiModelProperty("绿电减排（MWh）")
    private BigDecimal greenPowerEmissionReduction;

    @ApiModelProperty("绿植固碳（吨地上生物量）")
    private BigDecimal greenPlantsSequestration;
}
