package org.jeecg.modules.bems.visualization.tngl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 折线/柱状图单条数据系列
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("能碳指标数据系列")
public class SeriesVO {

    @ApiModelProperty("系列名称")
    private String name;

    @ApiModelProperty("数据值列表")
    private List<BigDecimal> data;

    @ApiModelProperty("单位")
    private String unit;
}
