package org.jeecg.modules.bems.echarts.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备属性趋势图查询参数
 * <p>
 * 依据设备 ID 列表 + 属性名称，从 {@code device_attribute} 中定位属性，
 * 再从 {@code device_attribute_history} 中按时间范围取出历史值，组装为 ECharts 折线图数据。
 *
 * @author sgai-fwbz
 */
@Data
@ApiModel(value = "ReturnAirCo2TrendQueryDto", description = "设备属性趋势图查询参数")
public class ReturnAirCo2TrendQueryDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备 ID 列表（如 AHU-1、AHU-2、AHU-3、AHU-4 的主键）
     */
    @NotEmpty(message = "设备ID列表不能为空")
    @ApiModelProperty(value = "设备ID列表", required = true)
    private List<Long> deviceIds;

    /**
     * 属性名称：默认 "回风二氧化碳"，也可传 "回风温度" 等其它属性名复用此接口
     */
    @ApiModelProperty(value = "属性名称", example = "回风二氧化碳")
    private String attributeName = "回风二氧化碳";

    /**
     * 起始时间；为空时默认为当天 00:00:00
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "起始时间")
    private LocalDateTime startTime;

    /**
     * 结束时间；为空时默认为当天 23:59:59
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ApiModelProperty(value = "结束时间")
    private LocalDateTime endTime;

    /**
     * 聚合粒度：hour(小时)/15min(15分钟)/day(天)；默认 hour
     */
    @ApiModelProperty(value = "聚合粒度：hour/15min/day", example = "hour")
    private String granularity = "hour";

    /**
     * 阈值（CO2 设定值等参考线），可选；为空时不在前端绘制设定线
     */
    @ApiModelProperty(value = "阈值（参考线）", example = "800")
    private Double threshold;
}
