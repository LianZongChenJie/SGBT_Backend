package org.jeecg.modules.bems.echarts.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 设备属性趋势图响应 VO
 * <p>
 * 直接对接 ECharts option 配置：
 * <ul>
 *   <li>{@code xAxis}     → xAxis.data</li>
 *   <li>{@code legend}    → legend.data</li>
 *   <li>{@code series}    → series.data</li>
 * </ul>
 *
 * @author sgai-fwbz
 */
@Data
@ApiModel(value = "ReturnAirCo2TrendVo", description = "设备属性趋势图响应")
public class ReturnAirCo2TrendVo implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "图表标题")
    private String title;

    @ApiModelProperty(value = "单位")
    private String unit;

    @ApiModelProperty(value = "阈值（CO2 设定值等参考线）")
    private Double threshold;

    @ApiModelProperty(value = "x 轴时间标签", notes = "形如 [00:00, 01:00, ..., 23:00]")
    @JsonProperty("xAxis")
    private List<String> xAxis;

    @ApiModelProperty(value = "图例数据", notes = "每条曲线对应一个设备名称")
    private List<String> legend;

    @ApiModelProperty(value = "折线系列数据")
    private List<TrendSeries> series;

    /**
     * 单条折线系列：name = 设备名（AHU-1），data 与 xAxis 等长；缺失值用 null
     */
    @Data
    public static class TrendSeries {
        @ApiModelProperty(value = "系列名称（设备名/设备编号）")
        private String name;

        @ApiModelProperty(value = "设备ID")
        private Long deviceId;

        @ApiModelProperty(value = "数据点", notes = "缺失值用 null，ECharts 会断开折线")
        private List<Double> data;
    }
}
