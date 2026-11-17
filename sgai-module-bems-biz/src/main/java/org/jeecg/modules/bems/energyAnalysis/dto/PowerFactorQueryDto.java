package org.jeecg.modules.bems.energyAnalysis.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 功率因数分析查询参数
 */
@Data
public class PowerFactorQueryDto {

    /**
     * 开始时间，默认 endTime 往前 7 天
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 结束时间，默认当前时间
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 设备id，逗号分隔，为空则取全部已接入的功率因数点位
     */
    private String deviceIds;

    /**
     * 聚合粒度：raw(15分钟原始) / hour(默认) / day / month
     */
    private String granularity;

    /**
     * 相别：total(默认，只看总功率因数) / all(总 + A/B/C 相)
     */
    private String phase;

    /**
     * 达标阈值，默认 0.9（参考《功率因数调整电费办法》0.9 标准）
     */
    private BigDecimal threshold;

    /**
     * 是否包含当前值为 0 的点位（如未投运的逆变器 PCS），默认 false
     */
    private Boolean includeZeroValue;
}
