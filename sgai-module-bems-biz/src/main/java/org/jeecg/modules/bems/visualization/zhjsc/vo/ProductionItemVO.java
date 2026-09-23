package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 蒸汽/电能产量项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("蒸汽/电能产量项")
public class ProductionItemVO {

    @ApiModelProperty("设备名称")
    private String deviceName;

    @ApiModelProperty("区间内最大值")
    private String maxValue;

    @ApiModelProperty("区间内最小值")
    private String minValue;

    @ApiModelProperty("产量（最大值-最小值）")
    private BigDecimal production;
}
