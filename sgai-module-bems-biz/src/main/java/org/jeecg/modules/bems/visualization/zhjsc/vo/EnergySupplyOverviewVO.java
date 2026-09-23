package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 供能概况
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("供能概况")
public class EnergySupplyOverviewVO {

    @ApiModelProperty("外供蒸汽量")
    private BigDecimal externalSteamSupplyVolume;

    @ApiModelProperty("换算热能")
    private BigDecimal convertHeatEnergy;

    @ApiModelProperty("余热热水")
    private BigDecimal wasteHeatHotWater;

    @ApiModelProperty("光伏产电")
    private BigDecimal photovoltaicPowerGeneration;
}
