package org.jeecg.modules.bems.energyAnalysis.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 变损分析查询参数
 */
@Data
public class TransformerLossQueryDto {

    /**
     * 开始时间，默认 endTime 往前 30 天
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 结束时间，默认当前时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 参与计算的设备id（须带“组合有功总电能”点位），逗号分隔，为空则自动取全部电表
     */
    private String deviceIds;

    /**
     * 聚合粒度：day(默认) / month
     */
    private String granularity;

    /**
     * 变压器型号，仅用于展示
     */
    private String model;

    /**
     * 单台变压器额定容量 kVA，默认 1000
     */
    private Double capacity;

    /**
     * 单台变压器空载损耗 P0（kW），默认 1.7
     */
    private Double noLoadLoss;

    /**
     * 单台变压器负载损耗（短路损耗）Pk（kW），默认 10.5
     */
    private Double loadLoss;

    /**
     * 变压器台数，默认 1
     */
    private Integer count;

    /**
     * 系数法参考变损率（%），默认 1.5，仅用于对比展示
     */
    private Double coefficientRate;
}
