package org.jeecg.modules.bems.energyAnalysis.vo.loss;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 变损分析结果
 * <p>
 * 当前现场无高压侧计量，本结果按变压器铭牌参数的理论公式估算，
 * simulated=true 表示数据为模拟/估算口径，不可当作实测变损使用。
 */
@Data
public class TransformerLossAnalysisVo {

    /** 是否为模拟/估算口径 */
    private Boolean simulated = true;

    /** 口径说明：theory(理论计算) / measure(实测差值) */
    private String mode = "theory";

    private String startTime;
    private String endTime;
    private String granularity;

    /** 本次查询范围内是否有数据 */
    private Boolean dataAvailable = false;

    private DataCoverage dataCoverage;

    /** 变压器参数 */
    private Param transformer = new Param();

    /** 汇总指标 */
    private Summary summary = new Summary();

    /** 变损趋势 */
    private Trend trend = new Trend();

    /** 按计量点分摊 */
    private List<Allocation> allocation = new ArrayList<>();

    /** 口径说明与数据告警 */
    private List<String> notes = new ArrayList<>();

    @Data
    public static class DataCoverage {
        private String firstTime;
        private String lastTime;
        private Integer sampleCount;
    }

    @Data
    public static class Param {
        private String model;
        /** 额定容量 kVA */
        private Double capacity;
        /** 空载损耗 kW */
        private Double noLoadLoss;
        /** 负载损耗 kW */
        private Double loadLoss;
        /** 台数 */
        private Integer count;
        /** 参数来源：request / business_config / default */
        private String source;
    }

    @Data
    public static class Summary {
        /** 低压侧电量 kWh */
        private Double lowSideEnergy;
        /** 推算高压侧电量 kWh */
        private Double estimateHighSideEnergy;
        /** 变损电量 kWh */
        private Double lossEnergy;
        /** 变损率 % */
        private Double lossRate;
        /** 平均负载率 % */
        private Double avgLoadRate;
        /** 平均功率因数 */
        private Double powerFactor;
        /** 运行小时数 h */
        private Double operationHours;
        /** 系数法参考变损电量 kWh */
        private Double coefficientLossEnergy;
        /** 系数法参考变损率 % */
        private Double coefficientRate;
        /** 空载损耗电量 kWh */
        private Double noLoadLossEnergy;
        /** 负载损耗电量 kWh */
        private Double loadLossEnergy;
        /** 变损率是否落在常规区间（配电变 1%~3%），false 说明参数或数据源需校准 */
        private Boolean lossRateReasonable;
        /** 按 30% 经济负载率反算的推荐变压器容量 kVA，用于说明当前容量是否明显偏大 */
        private Double capacitySuggestion;
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
        private String unit;
        private List<Double> data = new ArrayList<>();
    }

    @Data
    public static class Allocation {
        private Long deviceId;
        private String deviceName;
        /** 该计量点区间电量 kWh */
        private Double energy;
        /** 电量占比 % */
        private Double share;
        /** 分摊变损电量 kWh */
        private Double lossEnergy;
    }
}
