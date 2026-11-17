package org.jeecg.modules.bems.energyAnalysis.vo.power;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 功率因数分析结果
 * <p>
 * 一次返回页面所需的全部数据集：可用点位、实时概览、趋势、分布、统计。
 */
@Data
public class PowerFactorAnalysisVo {

    /** 查询开始时间 yyyy-MM-dd HH:mm:ss */
    private String startTime;

    /** 查询结束时间 */
    private String endTime;

    /** 聚合粒度 */
    private String granularity;

    /** 达标阈值 */
    private Double threshold;

    /** 本次查询范围内是否有数据 */
    private Boolean dataAvailable = false;

    /** 数据实际覆盖范围（用于解释“图为什么是空的”） */
    private DataCoverage dataCoverage;

    /** 参与分析的点位 */
    private List<PointInfo> points = new ArrayList<>();

    /** 被排除的点位及原因 */
    private List<PointInfo> excluded = new ArrayList<>();

    /** 实时概览 */
    private List<Overview> overview = new ArrayList<>();

    /** 趋势曲线 */
    private Trend trend = new Trend();

    /** 功率因数分布 */
    private List<Distribution> distribution = new ArrayList<>();

    /** 达标率与超限统计 */
    private Statistics statistics = new Statistics();

    /** 口径说明与数据告警 */
    private List<String> notes = new ArrayList<>();

    @Data
    public static class DataCoverage {
        /** 最早样本时间 */
        private String firstTime;
        /** 最晚样本时间 */
        private String lastTime;
        /** 样本条数 */
        private Integer sampleCount;
    }

    @Data
    public static class PointInfo {
        private Long attributeId;
        private Long deviceId;
        private String deviceName;
        /** 点位名称，如“总功率因数”“A相功率因数” */
        private String pointName;
        /** 相别：total / A / B / C */
        private String phase;
        /** 当前值 */
        private Double value;
        /** 采集时间 */
        private String gatherTime;
        /** 单位 */
        private String unit;
        /** 排除原因 */
        private String reason;
    }

    @Data
    public static class Overview {
        private Long deviceId;
        private String deviceName;
        /** 总功率因数 */
        private Double powerFactor;
        /** 分相功率因数 */
        private Double phaseA;
        private Double phaseB;
        private Double phaseC;
        /** 瞬时有功功率 kW */
        private Double activePower;
        /** 瞬时无功功率 kvar */
        private Double reactivePower;
        /** 瞬时视在功率 kVA */
        private Double apparentPower;
        /** 电压 V */
        private Double voltage;
        /** 电流 A */
        private Double current;
        private String gatherTime;
        /** 状态：达标 / 不达标 / 无数据 */
        private String status;
        /** 无功方向提示：倒送 / 正常 */
        private String reactiveDirection;
    }

    @Data
    public static class Trend {
        @JsonProperty("xAxis")
        private List<String> xAxis = new ArrayList<>();
        private List<Series> series = new ArrayList<>();
    }

    @Data
    public static class Series {
        private String name;
        private String unit = "";
        private Long deviceId;
        private String phase;
        private List<Double> data = new ArrayList<>();
    }

    @Data
    public static class Distribution {
        private Long deviceId;
        private String deviceName;
        private List<Bucket> buckets = new ArrayList<>();
    }

    @Data
    public static class Bucket {
        /** 区间，如 "0.90~0.95" */
        private String range;
        /** 样本数 */
        private Integer count = 0;
        /** 占比 % */
        private Double ratio = 0D;
        /** 是否达标区间 */
        private Boolean qualified = false;
    }

    @Data
    public static class Statistics {
        /** 全口径平均功率因数 */
        private Double avgPowerFactor;
        private Double minPowerFactor;
        private Double maxPowerFactor;
        /** 达标率 % */
        private Double qualifiedRate;
        /** 阈值 */
        private Double qualifiedThreshold;
        /** 有效统计时长 h */
        private Double totalHours;
        /** 超限时长 h */
        private Double exceedHours;
        /** 按设备统计 */
        private List<DeviceStat> deviceStats = new ArrayList<>();
    }

    @Data
    public static class DeviceStat {
        private Long deviceId;
        private String deviceName;
        private Double avgPowerFactor;
        private Double minPowerFactor;
        private Double maxPowerFactor;
        private Double qualifiedRate;
        private Double totalHours;
        private Double exceedHours;
    }
}
