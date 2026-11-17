package org.jeecg.modules.bems.energyAnalysis.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.bems.energyAnalysis.dto.TransformerLossQueryDto;
import org.jeecg.modules.bems.energyAnalysis.service.ITransformerLossService;
import org.jeecg.modules.bems.energyAnalysis.vo.loss.TransformerLossAnalysisVo;
import org.jeecg.modules.bems.energyAnalysis.vo.loss.TransformerLossAnalysisVo.Allocation;
import org.jeecg.modules.bems.energyAnalysis.vo.loss.TransformerLossAnalysisVo.DataCoverage;
import org.jeecg.modules.bems.energyAnalysis.vo.loss.TransformerLossAnalysisVo.Param;
import org.jeecg.modules.bems.energyAnalysis.vo.loss.TransformerLossAnalysisVo.Series;
import org.jeecg.modules.bems.energyAnalysis.vo.loss.TransformerLossAnalysisVo.Summary;
import org.jeecg.modules.bems.mdm.dto.DeviceAttributeHistoryQueryDto;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.entity.DeviceAttributeHistory;
import org.jeecg.modules.bems.mdm.mapper.DeviceAttributeHistoryMapper;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeHistoryService;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.jeecg.modules.bems.service.IBusinessConfigService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 变损（变压器损耗）分析实现 —— 理论计算口径（模拟）
 * <p>
 * 现场当前只有低压侧出线表、无高压侧计量点，无法用“高压侧电量 - 低压侧电量”的实测差值法，
 * 因此按变压器铭牌参数做理论估算：
 * ΔP = n × (P0 + Pk × β²)，β = S / (n × SN)，ΔA = ΔP × T
 * <p>
 * 参数取值优先级：请求参数 > business_config(energy_analysis:transformer_loss:params) > 内置默认值（SCB10-1000/10）。
 */
@Service
@AllArgsConstructor
public class TransformerLossServiceImpl implements ITransformerLossService {

    /** 参数配置项 key */
    private static final String CONFIG_KEY = "energy_analysis:transformer_loss:params";

    /** 电量点位：优先组合有功总电能，其次正向有功总电能 */
    private static final String ENERGY_COMPOSE = "组合有功总电能";
    private static final String ENERGY_FORWARD = "正向有功总电能";
    private static final String PF_KEYWORD = "功率因数";

    /** 默认铭牌参数：SCB10-1000/10 干式变压器典型值 */
    private static final double DEFAULT_CAPACITY = 1000D;
    private static final double DEFAULT_NO_LOAD_LOSS = 1.7D;
    private static final double DEFAULT_LOAD_LOSS = 10.5D;
    private static final int DEFAULT_COUNT = 1;
    private static final double DEFAULT_COEFFICIENT_RATE = 1.5D;
    private static final double DEFAULT_POWER_FACTOR = 0.9D;

    private static final double VALID_PF_MIN = 0.01D;
    private static final double VALID_PF_MAX = 1.5D;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final IDeviceAttributeService deviceAttributeService;
    private final IDeviceAttributeHistoryService deviceAttributeHistoryService;
    private final IDeviceService deviceService;
    private final IBusinessConfigService businessConfigService;
    private final DeviceAttributeHistoryMapper deviceAttributeHistoryMapper;

    @Override
    public TransformerLossAnalysisVo analysis(TransformerLossQueryDto query) {
        TransformerLossQueryDto param = query == null ? new TransformerLossQueryDto() : query;
        // 未指定时间时，对齐到“数据最后更新时间”，避免采集中断后默认区间查出来全是空图
        LocalDateTime latest = latestDataTime();
        LocalDateTime end = param.getEndTime() != null ? param.getEndTime()
                : (latest != null ? latest : LocalDateTime.now());
        LocalDateTime start = param.getStartTime() != null ? param.getStartTime() : end.minusDays(30);
        if (start.isAfter(end)) {
            LocalDateTime tmp = start;
            start = end;
            end = tmp;
        }
        String granularity = "month".equalsIgnoreCase(param.getGranularity()) ? "month" : "day";

        TransformerLossAnalysisVo vo = new TransformerLossAnalysisVo();
        vo.setSimulated(true);
        vo.setMode("theory");
        vo.setStartTime(format(start));
        vo.setEndTime(format(end));
        vo.setGranularity(granularity);

        // 1. 变压器参数
        param.setModel(StringUtils.isBlank(param.getModel()) ? null : param.getModel());
        vo.setTransformer(resolveTransformerParam(param));

        // 2. 电量点位（按设备取一个）
        Set<Long> deviceFilter = parseIds(param.getDeviceIds());
        if (deviceFilter.isEmpty()) {
            deviceFilter.addAll(parseIds(fromConfig("deviceIds")));
        }
        Map<Long, DeviceAttribute> energyAttrMap = new LinkedHashMap<>();
        List<DeviceAttribute> energyAttrs = deviceAttributeService.list(
                new LambdaQueryWrapper<DeviceAttribute>()
                        .like(DeviceAttribute::getAttributeName, "电能")
                        .orderByAsc(DeviceAttribute::getDeviceId)
                        .orderByAsc(DeviceAttribute::getId));
        for (DeviceAttribute attribute : energyAttrs) {
            if (attribute.getDeviceId() == null) {
                continue;
            }
            if (!deviceFilter.isEmpty() && !deviceFilter.contains(attribute.getDeviceId())) {
                continue;
            }
            String name = attribute.getAttributeName() == null ? "" : attribute.getAttributeName();
            boolean isCompose = name.contains(ENERGY_COMPOSE);
            boolean isForward = name.contains(ENERGY_FORWARD);
            if (!isCompose && !isForward) {
                continue;
            }
            DeviceAttribute exist = energyAttrMap.get(attribute.getDeviceId());
            if (exist == null) {
                energyAttrMap.put(attribute.getDeviceId(), attribute);
                continue;
            }
            // 组合有功优先于正向有功
            boolean existCompose = exist.getAttributeName() != null && exist.getAttributeName().contains(ENERGY_COMPOSE);
            if (!existCompose && isCompose) {
                energyAttrMap.put(attribute.getDeviceId(), attribute);
            }
        }

        if (energyAttrMap.isEmpty()) {
            vo.getNotes().add("未找到“组合有功总电能/正向有功总电能”点位，无法计算变损，请先维护电表电量点位。");
            return vo;
        }

        Set<Long> deviceIds = energyAttrMap.keySet();
        Map<Long, String> deviceNameMap = loadDeviceNames(deviceIds);

        // 3. 功率因数点位（用于反推无功电量）
        Map<Long, DeviceAttribute> pfAttrMap = new HashMap<>();
        List<DeviceAttribute> pfAttrs = deviceAttributeService.list(
                new LambdaQueryWrapper<DeviceAttribute>()
                        .like(DeviceAttribute::getAttributeName, PF_KEYWORD)
                        .orderByAsc(DeviceAttribute::getId));
        for (DeviceAttribute attribute : pfAttrs) {
            if (attribute.getDeviceId() == null || !deviceIds.contains(attribute.getDeviceId())) {
                continue;
            }
            String name = attribute.getAttributeName() == null ? "" : attribute.getAttributeName();
            if (name.contains("A相") || name.contains("B相") || name.contains("C相")) {
                continue;
            }
            if (!pfAttrMap.containsKey(attribute.getDeviceId())) {
                pfAttrMap.put(attribute.getDeviceId(), attribute);
            }
        }

        // 4. 查询历史
        List<Long> attributeIds = new ArrayList<>();
        for (DeviceAttribute attribute : energyAttrMap.values()) {
            attributeIds.add(attribute.getId());
        }
        for (DeviceAttribute attribute : pfAttrMap.values()) {
            attributeIds.add(attribute.getId());
        }
        Map<Long, List<DeviceAttributeHistory>> historyMap = loadHistories(attributeIds, start, end);

        // 5. 逐设备计算电量与功率因数
        Param transformer = vo.getTransformer();
        double capacity = transformer.getCapacity();
        double noLoadLoss = transformer.getNoLoadLoss();
        double loadLoss = transformer.getLoadLoss();
        int count = transformer.getCount() == null || transformer.getCount() < 1 ? 1 : transformer.getCount();

        double lowSideEnergy = 0D;
        double totalHours = 0D;
        double pfSum = 0D;
        int pfCount = 0;
        int validDevice = 0;
        LocalDateTime firstTime = null;
        LocalDateTime lastTime = null;
        int sampleCount = 0;

        Map<Long, DeviceEnergy> deviceEnergyMap = new LinkedHashMap<>();
        for (Map.Entry<Long, DeviceAttribute> entry : energyAttrMap.entrySet()) {
            Long deviceId = entry.getKey();
            List<DeviceAttributeHistory> list = historyMap.get(entry.getValue().getId());
            DeviceEnergy energy = calcDeviceEnergy(list);
            if (energy == null) {
                continue;
            }
            energy.deviceId = deviceId;
            energy.deviceName = deviceNameMap.get(deviceId);
            deviceEnergyMap.put(deviceId, energy);
            lowSideEnergy += energy.energy;
            totalHours = Math.max(totalHours, energy.hours);
            validDevice++;

            for (DeviceAttributeHistory history : list) {
                if (history.getCollectionTime() == null) {
                    continue;
                }
                sampleCount++;
                if (firstTime == null || history.getCollectionTime().isBefore(firstTime)) {
                    firstTime = history.getCollectionTime();
                }
                if (lastTime == null || history.getCollectionTime().isAfter(lastTime)) {
                    lastTime = history.getCollectionTime();
                }
            }

            DeviceAttribute pfAttr = pfAttrMap.get(deviceId);
            if (pfAttr != null) {
                List<DeviceAttributeHistory> pfList = historyMap.get(pfAttr.getId());
                if (pfList != null) {
                    for (DeviceAttributeHistory history : pfList) {
                        Double value = parseDouble(history.getValue());
                        if (value == null || value < VALID_PF_MIN || value > VALID_PF_MAX) {
                            continue;
                        }
                        pfSum += value;
                        pfCount++;
                    }
                }
            }
        }

        DataCoverage coverage = new DataCoverage();
        coverage.setFirstTime(format(firstTime));
        coverage.setLastTime(format(lastTime));
        coverage.setSampleCount(sampleCount);
        vo.setDataCoverage(coverage);
        vo.setDataAvailable(validDevice > 0 && lowSideEnergy > 0);

        if (validDevice == 0) {
            vo.getNotes().add("查询区间内无有效电量数据。历史表最新数据时间为 "
                    + (lastTime == null ? "无" : format(lastTime)) + "，请先检查采集任务。");
        }

        // 6. 汇总计算
        double powerFactor = pfCount == 0 ? DEFAULT_POWER_FACTOR : pfSum / pfCount;
        if (powerFactor <= 0D || powerFactor > 1D) {
            powerFactor = DEFAULT_POWER_FACTOR;
        }
        double runHours = totalHours <= 0 ? 0D : totalHours;

        // 无功电量按有功电量与平均功率因数反推
        double tanPhi = Math.tan(Math.acos(powerFactor));
        double reactiveEnergy = lowSideEnergy * tanPhi;

        // 平均视在功率 kVA
        double apparentPower = runHours <= 0 ? 0D : Math.sqrt(lowSideEnergy * lowSideEnergy + reactiveEnergy * reactiveEnergy) / runHours;
        double beta = capacity * count <= 0 ? 0D : apparentPower / (capacity * count);
        double lossPower = count * (noLoadLoss + loadLoss * beta * beta);
        double lossEnergy = lossPower * runHours;

        Summary summary = new Summary();
        summary.setLowSideEnergy(round(lowSideEnergy, 2));
        summary.setLossEnergy(round(lossEnergy, 2));
        summary.setEstimateHighSideEnergy(round(lowSideEnergy + lossEnergy, 2));
        summary.setLossRate(lowSideEnergy + lossEnergy <= 0 ? 0D : round(lossEnergy * 100D / (lowSideEnergy + lossEnergy), 2));
        summary.setAvgLoadRate(round(beta * 100D, 2));
        summary.setPowerFactor(round(powerFactor, 4));
        summary.setOperationHours(round(runHours, 2));
        summary.setNoLoadLossEnergy(round(count * noLoadLoss * runHours, 2));
        summary.setLoadLossEnergy(round(count * loadLoss * beta * beta * runHours, 2));
        Double coefficientRate = param.getCoefficientRate();
        if (coefficientRate == null || coefficientRate <= 0D) {
            coefficientRate = configDouble("coefficientRate", DEFAULT_COEFFICIENT_RATE);
        }
        summary.setCoefficientRate(round(coefficientRate, 2));
        summary.setCoefficientLossEnergy(round(lowSideEnergy * coefficientRate / 100D, 2));
        double lossRateValue = lowSideEnergy + lossEnergy <= 0 ? 0D : lossEnergy * 100D / (lowSideEnergy + lossEnergy);
        summary.setLossRateReasonable(lossRateValue >= 1D && lossRateValue <= 3D);
        summary.setCapacitySuggestion(round(apparentPower / 0.3D, 2));
        vo.setSummary(summary);

        // 7. 分摊
        for (DeviceEnergy energy : deviceEnergyMap.values()) {
            Allocation allocation = new Allocation();
            allocation.setDeviceId(energy.deviceId);
            allocation.setDeviceName(energy.deviceName);
            allocation.setEnergy(round(energy.energy, 2));
            double share = lowSideEnergy <= 0 ? 0D : energy.energy * 100D / lowSideEnergy;
            allocation.setShare(round(share, 2));
            allocation.setLossEnergy(round(lossEnergy * share / 100D, 2));
            vo.getAllocation().add(allocation);
        }

        // 8. 趋势
        vo.setTrend(buildTrend(deviceEnergyMap.keySet(), historyMap, energyAttrMap, granularity,
                capacity, noLoadLoss, loadLoss, count));

        // 9. 说明
        buildNotes(vo, param, lowSideEnergy, lossEnergy, beta, runHours, sampleCount, lastTime, validDevice);
        return vo;
    }

    /**
     * 逐桶计算变损趋势：桶内取电量首末差，空载损耗按名义时长累计
     */
    private TransformerLossAnalysisVo.Trend buildTrend(Set<Long> deviceIds,
                                                       Map<Long, List<DeviceAttributeHistory>> historyMap,
                                                       Map<Long, DeviceAttribute> energyAttrMap,
                                                       String granularity,
                                                       double capacity, double noLoadLoss,
                                                       double loadLoss, int count) {
        TransformerLossAnalysisVo.Trend trend = new TransformerLossAnalysisVo.Trend();
        Map<Long, TreeMap<String, double[]>> bucketMap = new HashMap<>();
        Set<String> bucketSet = new TreeSet<>();

        for (Long deviceId : deviceIds) {
            DeviceAttribute attribute = energyAttrMap.get(deviceId);
            if (attribute == null) {
                continue;
            }
            List<DeviceAttributeHistory> list = historyMap.get(attribute.getId());
            if (list == null || list.isEmpty()) {
                continue;
            }
            TreeMap<String, double[]> map = new TreeMap<>();
            for (DeviceAttributeHistory history : list) {
                Double value = parseDouble(history.getValue());
                if (value == null || history.getCollectionTime() == null) {
                    continue;
                }
                String key = bucketKey(history.getCollectionTime(), granularity);
                double[] firstLast = map.get(key);
                if (firstLast == null) {
                    firstLast = new double[]{value, value};
                    map.put(key, firstLast);
                } else {
                    firstLast[1] = value;
                }
                bucketSet.add(key);
            }
            bucketMap.put(deviceId, map);
        }

        List<String> xAxis = new ArrayList<>(bucketSet);
        trend.setXAxis(xAxis);
        List<Double> lossData = new ArrayList<>();
        List<Double> rateData = new ArrayList<>();
        List<Double> energyData = new ArrayList<>();
        List<Double> loadRateData = new ArrayList<>();

        for (String bucket : xAxis) {
            double bucketEnergy = 0D;
            for (Map.Entry<Long, TreeMap<String, double[]>> entry : bucketMap.entrySet()) {
                double[] firstLast = entry.getValue().get(bucket);
                if (firstLast == null || firstLast[1] < firstLast[0]) {
                    continue;
                }
                bucketEnergy += firstLast[1] - firstLast[0];
            }
            double hours = nominalHours(bucket, granularity);
            double bucketLoss = 0D;
            double bucketLoadRate = 0D;
            if (bucketEnergy > 0 && hours > 0) {
                // 桶内功率因数按 0.9 粗估，避免逐桶再查一次功率因数历史
                double tanPhi = Math.tan(Math.acos(DEFAULT_POWER_FACTOR));
                double reactive = bucketEnergy * tanPhi;
                double apparent = Math.sqrt(bucketEnergy * bucketEnergy + reactive * reactive) / hours;
                double beta = capacity * count <= 0 ? 0D : apparent / (capacity * count);
                bucketLoadRate = beta * 100D;
                bucketLoss = count * (noLoadLoss + loadLoss * beta * beta) * hours;
            } else if (hours > 0) {
                // 无电量但有运行时段，只计空载损耗
                bucketLoss = count * noLoadLoss * hours;
            }
            energyData.add(round(bucketEnergy, 2));
            lossData.add(round(bucketLoss, 2));
            loadRateData.add(round(bucketLoadRate, 2));
            rateData.add(bucketEnergy + bucketLoss <= 0 ? 0D : round(bucketLoss * 100D / (bucketEnergy + bucketLoss), 2));
        }

        trend.getSeries().add(series("低压侧电量", "kWh", energyData));
        trend.getSeries().add(series("变损电量", "kWh", lossData));
        trend.getSeries().add(series("变损率", "%", rateData));
        trend.getSeries().add(series("负载率", "%", loadRateData));
        return trend;
    }

    private Series series(String name, String unit, List<Double> data) {
        Series series = new Series();
        series.setName(name);
        series.setUnit(unit);
        series.setData(data);
        return series;
    }

    /**
     * 取区间内电量首末差值
     */
    private DeviceEnergy calcDeviceEnergy(List<DeviceAttributeHistory> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        Double firstValue = null;
        LocalDateTime firstTime = null;
        Double lastValue = null;
        LocalDateTime lastTime = null;
        for (DeviceAttributeHistory history : list) {
            Double value = parseDouble(history.getValue());
            if (value == null || history.getCollectionTime() == null) {
                continue;
            }
            if (firstValue == null) {
                firstValue = value;
                firstTime = history.getCollectionTime();
            }
            lastValue = value;
            lastTime = history.getCollectionTime();
        }
        if (firstValue == null || lastValue == null || lastTime == null || firstTime == null) {
            return null;
        }
        // 电量倒走视为换表/清零，本区间不参与计算
        if (lastValue < firstValue) {
            return null;
        }
        long minutes = Duration.between(firstTime, lastTime).toMinutes();
        if (minutes <= 0) {
            return null;
        }
        DeviceEnergy energy = new DeviceEnergy();
        energy.energy = lastValue - firstValue;
        energy.hours = minutes / 60D;
        return energy;
    }

    private Param resolveTransformerParam(TransformerLossQueryDto query) {
        Param param = new Param();
        JSONObject config = loadConfig();

        String model = query.getModel();
        if (StringUtils.isBlank(model) && config != null) {
            model = config.getString("model");
        }
        param.setModel(StringUtils.isBlank(model) ? "SCB10-1000/10" : model);

        param.setCapacity(resolveDouble(query.getCapacity(), config, "capacity", DEFAULT_CAPACITY));
        param.setNoLoadLoss(resolveDouble(query.getNoLoadLoss(), config, "noLoadLoss", DEFAULT_NO_LOAD_LOSS));
        param.setLoadLoss(resolveDouble(query.getLoadLoss(), config, "loadLoss", DEFAULT_LOAD_LOSS));

        Integer count = query.getCount();
        if (count == null && config != null) {
            count = config.getInteger("count");
        }
        param.setCount(count == null || count < 1 ? DEFAULT_COUNT : count);

        if (query.getCapacity() != null || query.getNoLoadLoss() != null || query.getLoadLoss() != null || query.getCount() != null) {
            param.setSource("request");
        } else if (config != null) {
            param.setSource("business_config");
        } else {
            param.setSource("default");
        }
        return param;
    }

    private Double resolveDouble(Double value, JSONObject config, String key, double defaultValue) {
        if (value != null && value > 0) {
            return value;
        }
        if (config != null) {
            Double configValue = config.getDouble(key);
            if (configValue != null && configValue > 0) {
                return configValue;
            }
        }
        return defaultValue;
    }

    private JSONObject loadConfig() {
        String json = configValue(CONFIG_KEY);
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String configValue(String key) {
        try {
            return businessConfigService.getValueByKey(key);
        } catch (Exception e) {
            return null;
        }
    }

    private Double configDouble(String key, double defaultValue) {
        JSONObject config = loadConfig();
        if (config == null) {
            return defaultValue;
        }
        Double value = config.getDouble(key);
        return value == null ? defaultValue : value;
    }

    private String fromConfig(String key) {
        JSONObject config = loadConfig();
        return config == null ? null : config.getString(key);
    }

    private void buildNotes(TransformerLossAnalysisVo vo, TransformerLossQueryDto query,
                            double lowSideEnergy, double lossEnergy, double beta,
                            double runHours, int sampleCount, LocalDateTime lastTime, int validDevice) {
        vo.getNotes().add("【模拟口径】现场未接入高压侧计量点，无法用“高压侧电量 - 低压侧电量”的实测差值法，"
                + "本结果按变压器铭牌参数（ΔP = n×(P0+Pk×β²)，ΔA = ΔP×T）理论估算，不能作为考核依据。");
        if ("default".equals(vo.getTransformer().getSource())) {
            vo.getNotes().add("变压器参数为内置默认值（SCB10-1000/10：1000kVA、P0=1.7kW、Pk=10.5kW），"
                    + "请按现场实际铭牌通过请求参数或 business_config(" + CONFIG_KEY + ") 修正。");
        }
        vo.getNotes().add("电表未接入无功电能量点位，无功电量由有功电量与平均功率因数反推，精度受限。");
        if (validDevice == 0) {
            vo.getNotes().add("查询区间内没有可用的电量数据，指标全部为 0。");
        }
        if (sampleCount == 0) {
            vo.getNotes().add("查询区间内无历史数据，历史表最新时间为 " + (lastTime == null ? "无" : format(lastTime)) + "。");
        } else if (lastTime != null && lastTime.isBefore(LocalDateTime.now().minusDays(1))) {
            vo.getNotes().add("历史数据最新时间为 " + format(lastTime) + "，采集可能已中断。");
        }
        if (runHours > 0 && beta > 0 && beta < 0.05D) {
            vo.getNotes().add("当前平均负载率仅 " + round(beta * 100D, 2) + "%，变压器长期接近空载，"
                    + "空载损耗占主导，变损率会明显偏高，属数据现状而非算法错误。"
                    + "按 30% 经济负载率反算，当前负荷仅需约 "
                    + vo.getSummary().getCapacitySuggestion() + " kVA 容量，请核实参与计算的表是否为变压器低压侧出口总表。");
        }
        if (lowSideEnergy > 0 && lossEnergy > lowSideEnergy * 0.1D) {
            vo.getNotes().add("变损电量已超过低压侧电量的 10%，超出常规（配电变通常 1%~3%），"
                    + "请核对变压器容量/损耗参数与低压侧计量范围是否匹配。");
        }
        vo.getNotes().add("若后续加装高压侧电表（或取供电局关口表数据），可将本接口切换为实测差值口径，届时结果为真实变损。");
    }

    private Map<Long, List<DeviceAttributeHistory>> loadHistories(List<Long> attributeIds, LocalDateTime start, LocalDateTime end) {
        Map<Long, List<DeviceAttributeHistory>> result = new HashMap<>();
        if (attributeIds == null || attributeIds.isEmpty()) {
            return result;
        }
        DeviceAttributeHistoryQueryDto dto = new DeviceAttributeHistoryQueryDto();
        dto.setDeviceAttributeIds(attributeIds);
        dto.setStartTime(start);
        dto.setEndTime(end);
        List<DeviceAttributeHistory> list = deviceAttributeHistoryService.listByAttributeIds(dto);
        if (list == null || list.isEmpty()) {
            return result;
        }
        for (DeviceAttributeHistory history : list) {
            if (history.getAttributeId() == null) {
                continue;
            }
            List<DeviceAttributeHistory> values = result.get(history.getAttributeId());
            if (values == null) {
                values = new ArrayList<>();
                result.put(history.getAttributeId(), values);
            }
            values.add(history);
        }
        for (List<DeviceAttributeHistory> values : result.values()) {
            values.sort((a, b) -> {
                LocalDateTime t1 = a.getCollectionTime();
                LocalDateTime t2 = b.getCollectionTime();
                if (t1 == null && t2 == null) {
                    return 0;
                }
                if (t1 == null) {
                    return 1;
                }
                if (t2 == null) {
                    return -1;
                }
                return t1.compareTo(t2);
            });
        }
        return result;
    }

    /**
     * 历史表最新数据时间，用于默认查询区间对齐
     */
    private LocalDateTime latestDataTime() {
        try {
            return deviceAttributeHistoryMapper.selectLatestCollectionTime();
        } catch (Exception e) {
            return null;
        }
    }

    private Map<Long, String> loadDeviceNames(Set<Long> deviceIds) {
        Map<Long, String> map = new HashMap<>();
        List<Device> devices = deviceService.listByIds(new ArrayList<>(deviceIds));
        if (devices == null) {
            return map;
        }
        for (Device device : devices) {
            map.put(device.getId(), device.getDeviceName());
        }
        return map;
    }

    private String bucketKey(LocalDateTime time, String granularity) {
        return "month".equals(granularity) ? time.format(MONTH_FMT) : time.format(DAY_FMT);
    }

    /**
     * 桶的名义时长（小时）：日 24h、月按当月天数
     */
    private double nominalHours(String bucket, String granularity) {
        if ("month".equals(granularity)) {
            try {
                return YearMonth.parse(bucket, MONTH_FMT).lengthOfMonth() * 24D;
            } catch (Exception e) {
                return 24D * 30;
            }
        }
        return 24D;
    }

    private Set<Long> parseIds(String ids) {
        Set<Long> result = new LinkedHashSet<>();
        if (StringUtils.isBlank(ids)) {
            return result;
        }
        String[] arr = ids.split(",");
        for (String item : arr) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            try {
                result.add(Long.valueOf(item.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非数字参数
            }
        }
        return result;
    }

    private Double parseDouble(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double round(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(FMT);
    }

    /**
     * 单设备电量计算结果
     */
    private static class DeviceEnergy {
        private Long deviceId;
        private String deviceName;
        /** 区间电量 kWh */
        private double energy;
        /** 区间小时数 */
        private double hours;
    }
}
