package org.jeecg.modules.bems.visualization.zhjsc.enums;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 能源站集成（device_id=4158）实时点位白名单。
 * <p>
 * 只开放组态画面上实际使用的测点，调用方按 {@link #getKey()} 绑定，不直接接触 pSpace tagId，
 * 后续增删点位、改名称都不影响对方。
 * <p>
 * tagId 取自 pSpace 点表「能源站集成」sheet 的 TagId 列，与 device_attribute.acquisition_coding 一致。
 * unit 为展示单位，scale 为建议小数位（开关量为 0）。
 */
public enum EnergyStationPointEnum {

    // ---------- 协鑫来汽 / 蒸汽计量 ----------
    DN500_SSLL("DN500_SSLL", "DN500差压式流量计-瞬时流量", "t/h", 5201L, false, 2),
    DN500_ZL("DN500_ZL", "DN500差压式流量计-总量", "t", 5200L, false, 2),
    DN500_YL("DN500_YL", "DN500差压式流量计-压力", "MPa", 5202L, false, 2),
    DN500_WD("DN500_WD", "DN500差压式流量计-温度", "℃", 5203L, false, 1),
    DN200_SSLL("DN200_SSLL", "DN200差压式流量计-瞬时流量", "t/h", 5197L, false, 2),
    DN200_ZL("DN200_ZL", "DN200差压式流量计-总量", "t", 5196L, false, 2),
    DN200_YL("DN200_YL", "DN200差压式流量计-压力", "MPa", 5198L, false, 2),
    DN200_WD("DN200_WD", "DN200差压式流量计-温度", "℃", 5199L, false, 1),
    XADN500_YL("XADN500_YL", "协鑫DN500调节阀-压力", "MPa", 5168L, false, 2),
    XADN500_WD("XADN500_WD", "协鑫DN500调节阀-温度", "℃", 5175L, false, 1),
    XADN200_YL("XADN200_YL", "协鑫DN200调节阀-压力", "MPa", 5169L, false, 2),
    XADN200_WD("XADN200_WD", "协鑫DN200调节阀-温度", "℃", 5176L, false, 1),
    VAD_DN500("VAD_DN500", "管网DN500蒸汽电动调节阀-阀位反馈", "%", 5135L, false, 1),
    VAD_DN200("VAD_DN200", "管网DN200蒸汽电动调节阀-阀位反馈", "%", 5136L, false, 1),

    // ---------- 蒸汽 / 冷凝水阀门开关状态 ----------
    V_DN450("V_DN450", "管网DN450蒸汽电动闸板阀", "", 5116L, true, 0),
    V_DN300("V_DN300", "管网DN300蒸汽电动闸板阀", "", 5117L, true, 0),
    V_XA_DN500("V_XA_DN500", "DN500蒸汽开关阀-协鑫供汽", "", 5120L, true, 0),
    V_XA_DN200("V_XA_DN200", "DN200蒸汽开关阀-协鑫供汽", "", 5121L, true, 0),
    V_LNS_DN300("V_LNS_DN300", "DN300冷凝水开关阀", "", 5119L, true, 0),
    V_LNS_DN200("V_LNS_DN200", "DN200冷凝水开关阀", "", 5122L, true, 0),
    V_TRQ("V_TRQ", "锅炉房天然气总管切断阀", "", 5118L, true, 0),

    // ---------- 冷凝水 / 废水 ----------
    LNS_SSLL("LNS_SSLL", "来自稀土冷凝水-瞬时流量", "t/h", 5204L, false, 2),
    LNS_ZL("LNS_ZL", "来自稀土冷凝水-累计流量", "t", 5205L, false, 2),
    LNS_WD("LNS_WD", "来自稀土冷凝水-温度", "℃", 5206L, false, 1),
    XXLNS_SSLL("XXLNS_SSLL", "协鑫冷凝水-瞬时流量", "t/h", 5209L, false, 2),
    XXLNS_JLL("XXLNS_JLL", "协鑫冷凝水-净流量", "t", 5208L, false, 2),
    XXLNS_WD("XXLNS_WD", "协鑫冷凝水-供水温度", "℃", 5207L, false, 1),
    LNS_FT0302("LNS_FT0302", "冷凝水泵出口-瞬时流量", "t/h", 5156L, false, 2),
    LNS_FT0310("LNS_FT0310", "去往协鑫冷凝水-瞬时流量", "t/h", 5163L, false, 2),
    FSLNS_SSLL("FSLNS_SSLL", "稀土冷凝水废水-瞬时流量", "t/h", 5227L, false, 2),
    FSLNS_ZL("FSLNS_ZL", "稀土冷凝水废水-累计流量", "t", 5225L, false, 2),
    FSLNS_WD("FSLNS_WD", "稀土冷凝水废水-温度", "℃", 5226L, false, 1),

    // ---------- 冷凝水泵 1#~4# ----------
    LNSB1_FREQ("LNSB1_FREQ", "冷凝水1#泵-频率反馈", "Hz", 5188L, false, 1),
    LNSB2_FREQ("LNSB2_FREQ", "冷凝水2#泵-频率反馈", "Hz", 5190L, false, 1),
    LNSB3_FREQ("LNSB3_FREQ", "冷凝水3#泵-频率反馈", "Hz", 5192L, false, 1),
    LNSB4_FREQ("LNSB4_FREQ", "冷凝水4#泵-频率反馈", "Hz", 5194L, false, 1),
    LNSB1_RUN("LNSB1_RUN", "冷凝水1#泵-运行", "", 5123L, true, 0),
    LNSB2_RUN("LNSB2_RUN", "冷凝水2#泵-运行", "", 5126L, true, 0),
    LNSB3_RUN("LNSB3_RUN", "冷凝水3#泵-运行", "", 5129L, true, 0),
    LNSB4_RUN("LNSB4_RUN", "冷凝水4#泵-运行", "", 5132L, true, 0),
    LNSB1_TRIP("LNSB1_TRIP", "冷凝水1#泵-故障", "", 5125L, true, 0),
    LNSB2_TRIP("LNSB2_TRIP", "冷凝水2#泵-故障", "", 5128L, true, 0),
    LNSB3_TRIP("LNSB3_TRIP", "冷凝水3#泵-故障", "", 5131L, true, 0),
    LNSB4_TRIP("LNSB4_TRIP", "冷凝水4#泵-故障", "", 5134L, true, 0),

    // ---------- 主蒸汽 / 给水 ----------
    ZQGD1_LL("ZQGD1_LL", "主蒸汽管道流量1", "t/h", 5157L, false, 2),
    ZQGD2_LL("ZQGD2_LL", "主蒸汽管道流量2", "t/h", 5158L, false, 2),
    ZQMG1_LL("ZQMG1_LL", "主蒸汽母管流量1", "t/h", 5159L, false, 2),
    ZQMG2_LL("ZQMG2_LL", "主蒸汽母管流量2", "t/h", 5160L, false, 2),
    GSG1_LL("GSG1_LL", "主给水管流量1", "t/h", 5161L, false, 2),
    GSG2_LL("GSG2_LL", "主给水管流量2", "t/h", 5162L, false, 2),

    // ---------- 采暖（供暖） ----------
    CN_GSL1("CN_GSL1", "采暖供水流量1", "t/h", 5151L, false, 2),
    CN_GSL2("CN_GSL2", "采暖供水流量2", "t/h", 5153L, false, 2),
    CN_GSL3("CN_GSL3", "一次网采暖供水流量", "t/h", 5155L, false, 2),
    CN_GWD("CN_GWD", "采暖供水温度", "℃", 5177L, false, 1),
    CN_HWD("CN_HWD", "采暖回水温度", "℃", 5178L, false, 1),

    // ---------- 天然气 / 二级调压站 ----------
    TRQ_MGYL("TRQ_MGYL", "二级调压站出口压力", "MPa", 5170L, false, 3),
    TRQ_FJB01_PT("TRQ_FJB01_PT", "二级调压装置1#撬-入口压力", "MPa", 5141L, false, 3),
    TRQ_FJB01_TT("TRQ_FJB01_TT", "二级调压装置1#撬-入口温度", "℃", 5142L, false, 1),
    TRQ_FJB02_FT1("TRQ_FJB02_FT1", "二级调压装置2#撬-流量1", "m³/h", 5143L, false, 2),
    TRQ_FJB02_FT2("TRQ_FJB02_FT2", "二级调压装置2#撬-流量2", "m³/h", 5144L, false, 2),
    TRQ_FJB02_FT3("TRQ_FJB02_FT3", "二级调压装置2#撬-流量3", "m³/h", 5145L, false, 2),
    TRQ_FJB02_PT("TRQ_FJB02_PT", "二级调压装置2#撬-出口压力", "MPa", 5149L, false, 3),
    TRQ_FJB02_TT("TRQ_FJB02_TT", "二级调压装置2#撬-出口温度", "℃", 5150L, false, 1),
    TRQ_FJB02_PD1("TRQ_FJB02_PD1", "二级调压装置2#撬-过滤器1差压", "kPa", 5146L, false, 2),
    TRQ_FJB02_PD2("TRQ_FJB02_PD2", "二级调压装置2#撬-过滤器2差压", "kPa", 5147L, false, 2),
    TRQ_FJB02_PD3("TRQ_FJB02_PD3", "二级调压装置2#撬-过滤器3差压", "kPa", 5148L, false, 2);

    private final String key;
    private final String name;
    private final String unit;
    private final Long tagId;
    private final boolean boolPoint;
    private final int scale;

    EnergyStationPointEnum(String key, String name, String unit, Long tagId, boolean boolPoint, int scale) {
        this.key = key;
        this.name = name;
        this.unit = unit;
        this.tagId = tagId;
        this.boolPoint = boolPoint;
        this.scale = scale;
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public Long getTagId() {
        return tagId;
    }

    public boolean isBoolPoint() {
        return boolPoint;
    }

    public int getScale() {
        return scale;
    }

    private static final Map<String, EnergyStationPointEnum> BY_KEY;
    private static final Map<Long, EnergyStationPointEnum> BY_TAG;
    private static final List<Long> ALL_TAG_IDS;

    static {
        Map<String, EnergyStationPointEnum> byKey = new LinkedHashMap<>();
        Map<Long, EnergyStationPointEnum> byTag = new LinkedHashMap<>();
        List<Long> tagIds = new ArrayList<>();
        for (EnergyStationPointEnum p : values()) {
            byKey.put(p.key, p);
            byTag.put(p.tagId, p);
            tagIds.add(p.tagId);
        }
        BY_KEY = Collections.unmodifiableMap(byKey);
        BY_TAG = Collections.unmodifiableMap(byTag);
        ALL_TAG_IDS = Collections.unmodifiableList(tagIds);
    }

    public static Map<String, EnergyStationPointEnum> byKey() {
        return BY_KEY;
    }

    public static Map<Long, EnergyStationPointEnum> byTag() {
        return BY_TAG;
    }

    /** 白名单全部 tagId，顺序与枚举声明一致 */
    public static List<Long> allTagIds() {
        return ALL_TAG_IDS;
    }

    /** 按 key 取点位，未知 key 返回 null */
    public static EnergyStationPointEnum ofKey(String key) {
        return key == null ? null : BY_KEY.get(key.trim());
    }
}
