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
 * 电表总有功功率趋势图查询参数
 * <p>
 * 依据设备 ID 列表，从 {@code table_mqtt_history} 中按 desc 列模糊匹配"总有功功率"，
 * 再按时间范围取出遥测值，组装为 ECharts 折线图数据。
 *
 * @author sgai-fwbz
 */
@Data
@ApiModel(value = "ActivePowerTrendQueryDto", description = "电表总有功功率趋势图查询参数")
public class ActivePowerTrendQueryDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 设备 ID 列表
     */
    @NotEmpty(message = "设备ID列表不能为空")
    @ApiModelProperty(value = "设备ID列表", required = true)
    private List<Long> deviceIds;

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
}
