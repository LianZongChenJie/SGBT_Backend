package org.jeecg.modules.bems.visualization.tngl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 锅炉能耗转换碳排放量
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("锅炉能耗转换碳排放量")
public class BoilerEnergyCarbonConversionVO {

    @ApiModelProperty("设备名称（如：锅炉1）")
    private String attributeName;

    @ApiModelProperty("标况体积消耗量（Nm³）")
    private BigDecimal totalValue;

    @ApiModelProperty("碳排放量（kg CO₂）")
    private BigDecimal carbonEmission;
}
