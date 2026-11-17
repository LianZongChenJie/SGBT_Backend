package org.jeecg.modules.bems.energyAnalysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.bems.energyAnalysis.dto.PowerFactorQueryDto;
import org.jeecg.modules.bems.energyAnalysis.service.IPowerFactorService;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo.Bucket;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo.DataCoverage;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo.DeviceStat;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo.Distribution;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo.Overview;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo.PointInfo;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo.Series;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo.Statistics;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo.Trend;
import org.jeecg.modules.bems.mdm.dto.DeviceAttributeHistoryQueryDto;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.entity.DeviceAttributeHistory;
import org.jeecg.modules.bems.mdm.mapper.DeviceAttributeHistoryMapper;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeHistoryService;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 功率因数分析实现
 * <p>
 * 数据来源：device_attribute（实时值）+ device_attribute_history（15 分钟槽位历史）。
 * 点位识别规则：attribute_name 包含“功率因数”，按“A相/B相/C相”区分相别，无相别视为总值。
 */
@Service
@AllArgsConstructor
public class PowerFactorServiceImpl implements IPowerFactorService {

    /** 点位名称关键字 */
    private static final String PF_KEYWORD = "功率因数";

    /** 相邻样本间隔超过该分钟数视为断采，不计入有效时长 */
    private static final long MAX_GAP_MINUTES = 30L;

    /** 视为有效功率因数的下限（0 值多为未采集或设备未投运） */
    private static final double VALID_PF_MIN = 0.01D;

    /** 视为有效功率因数的上限（超过 1 属异常数据，用于剔除脏数据） */
    private static final double VALID_PF_MAX = 1.5D;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter RAW_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 分布分档下界 */
    private static final double[] BUCKET_LOWER = {0D, 0.80D, 0.85D, 0.90D, 0.95D, 1.00D};

    private final IDeviceAttributeService deviceAttributeService;
    private final IDeviceAttributeHistoryService deviceAttributeHistoryService;
    private final IDeviceService deviceService;
    private final DeviceAttributeHistoryMapper deviceAttributeHistoryMapper;

    @Override
    public PowerFactorAnalysisVo analysis(PowerFactorQueryDto query) {
        PowerFactorQueryDto param = query == null ? new PowerFactorQueryDto() : query;
        // 未指定时间时，对齐到“数据最后更新时间”，避免采集中断后默认区间查出来全是空图
        LocalDateTime latest = latestDataTime();
        LocalDateTime end = param.getEndTime() != null ? param.getEndTime()
                : (latest != null ? latest : LocalDateTime.now());
        LocalDateTime start = param.getStartTime() != null ? param.getStartTime() : end.minusDays(7);
        if (start.isAfter(end)) {
            LocalDateTime tmp = start;
            start = end;
            end = tmp;
        }
        String granularity = normalizeGranularity(param.getGranularity());
        boolean phaseAll = "all".equalsIgnoreCase(param.getPhase());
        double threshold = param.getThreshold() == null ? 0.9D : param.getThreshold().doubleValue();
        boolean includeZeroValue = Boolean.TRUE.equals(param.getIncludeZeroValue());

        PowerFactorAnalysisVo vo = new PowerFactorAnalysisVo();
        vo.setStartTime(format(start));
        vo.setEndTime(format(end));
        vo.setGranularity(granularity);
        vo.setThreshold(round(threshold, 4));

        // 1. 找出全部功率因数点位
        Set<Long> deviceFilter = parseIds(param.getDeviceIds());
        List<DeviceAttribute> attributes = deviceAttributeService.list(
                new LambdaQueryWrapper<DeviceAttribute>()
                        .like(DeviceAttribute::getAttributeName, PF_KEYWORD)
                        .orderByAsc(DeviceAttribute::getDeviceId)
                        .orderByAsc(DeviceAttribute::getId));

        List<PfPoint> allPoints = new ArrayList<>();
        List<PointInfo> excluded = new ArrayList<>();
        Set<Long> deviceIds = new LinkedHashSet<>();
        for (DeviceAttribute attribute : attributes) {
            if (attribute.getDeviceId() == null) {
                continue;
            }
            if (!deviceFilter.isEmpty() && !deviceFilter.contains(attribute.getDeviceId())) {
                continue;
            }
            Double value = parseDouble(attribute.getValue());
            PfPoint point = new PfPoint();
            point.attributeId = attribute.getId();
            point.deviceId = attribute.getDeviceId();
            point.pointName = attribute.getAttributeName();
            point.phase = phaseOf(attribute.getAttributeName());
            point.value = value;
            point.gatherTime = attribute.getGatherTime();
            point.unit = attribute.getUnit();
            allPoints.add(point);
            deviceIds.add(attribute.getDeviceId());
        }

        Map<Long, String> deviceNameMap = loadDeviceNames(deviceIds);
        List<PfPoint> activePoints = new ArrayList<>();
        for (PfPoint point : allPoints) {
            point.deviceName = deviceNameMap.get(point.deviceId);
            if (point.value == null) {
                excluded.add(toPointInfo(point, "无采集值"));
                continue;
            }
            if (!includeZeroValue && point.value <= VALID_PF_MIN) {
                excluded.add(toPointInfo(point, "当前值为 0（设备未投运或点位未接入）"));
                continue;
            }
            activePoints.add(point);
        }

        // 全部点位都为 0 时不做剔除，保证页面还有点位可选，仅给出提示
        boolean fallbackToZeroValue = false;
        if (activePoints.isEmpty() && !allPoints.isEmpty()) {
            activePoints.addAll(allPoints);
            excluded.clear();
            for (PfPoint point : activePoints) {
                point.deviceName = deviceNameMap.get(point.deviceId);
            }
            fallbackToZeroValue = true;
        }

        for (PfPoint point : activePoints) {
            vo.getPoints().add(toPointInfo(point, null));
        }
        vo.getExcluded().addAll(excluded);

        // 2. 实时概览
        vo.getOverview().addAll(buildOverview(activePoints, deviceIds, threshold));

        // 3. 查询历史
        List<Long> attributeIds = new ArrayList<>();
        for (PfPoint point : activePoints) {
            attributeIds.add(point.attributeId);
        }
        Map<Long, List<DeviceAttributeHistory>> historyMap = loadHistories(attributeIds, start, end);
        int sampleCount = 0;
        LocalDateTime firstTime = null;
        LocalDateTime lastTime = null;
        for (List<DeviceAttributeHistory> list : historyMap.values()) {
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
        }
        DataCoverage coverage = new DataCoverage();
        coverage.setFirstTime(format(firstTime));
        coverage.setLastTime(format(lastTime));
        coverage.setSampleCount(sampleCount);
        vo.setDataCoverage(coverage);
        vo.setDataAvailable(sampleCount > 0);

        // 4. 趋势
        List<PfPoint> trendPoints = new ArrayList<>();
        for (PfPoint point : activePoints) {
            if (phaseAll || "total".equals(point.phase)) {
                trendPoints.add(point);
            }
        }
        if (trendPoints.isEmpty()) {
            trendPoints.addAll(activePoints);
        }
        vo.setTrend(buildTrend(trendPoints, historyMap, granularity));

        // 5. 分布（按设备取总值点位）
        vo.getDistribution().addAll(buildDistribution(activePoints, historyMap, threshold));

        // 6. 统计
        vo.setStatistics(buildStatistics(activePoints, historyMap, threshold));

        // 7. 口径与告警说明
        buildNotes(vo, activePoints, excluded, allPoints, sampleCount, firstTime, lastTime, fallbackToZeroValue);

        return vo;
    }

    /**
     * 实时概览：取同一设备下的总功率因数、分相功率因数及功率/电压电流
     */
    private List<Overview> buildOverview(List<PfPoint> activePoints, Set<Long> deviceIds, double threshold) {
        List<Overview> result = new ArrayList<>();
        if (activePoints.isEmpty()) {
            return result;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (PfPoint point : activePoints) {
            ids.add(point.deviceId);
        }
        Map<Long, List<DeviceAttribute>> attrMap = new HashMap<>();
        if (!ids.isEmpty()) {
            List<DeviceAttribute> all = deviceAttributeService.findByDeviceIds(ids);
            if (all != null) {
                for (DeviceAttribute attribute : all) {
                    if (attribute.getDeviceId() == null) {
                        continue;
                    }
                    List<DeviceAttribute> list = attrMap.get(attribute.getDeviceId());
                    if (list == null) {
                        list = new ArrayList<>();
                        attrMap.put(attribute.getDeviceId(), list);
                    }
                    list.add(attribute);
                }
            }
        }

        Map<Long, Overview> overviewMap = new LinkedHashMap<>();
        for (PfPoint point : activePoints) {
            Overview overview = overviewMap.get(point.deviceId);
            if (overview == null) {
                overview = new Overview();
                overview.setDeviceId(point.deviceId);
                overview.setDeviceName(point.deviceName);
                overview.setGatherTime(format(point.gatherTime));

                List<DeviceAttribute> attrs = attrMap.get(point.deviceId);
                if (attrs != null) {
                    overview.setActivePower(valueOf(matchAttr(attrs, "总有功功率", "电能")));
                    overview.setReactivePower(valueOf(matchAttr(attrs, "总无功功率", "电能", "额定")));
                    overview.setApparentPower(valueOf(matchAttr(attrs, "总视在功率")));
                    overview.setVoltage(valueOf(matchAttr(attrs, "A相电压")));
                    overview.setCurrent(valueOf(matchAttr(attrs, "A相电流")));
                    overview.setPhaseA(valueOf(matchAttr(attrs, "A相" + PF_KEYWORD)));
                    overview.setPhaseB(valueOf(matchAttr(attrs, "B相" + PF_KEYWORD)));
                    overview.setPhaseC(valueOf(matchAttr(attrs, "C相" + PF_KEYWORD)));
                }
                overviewMap.put(point.deviceId, overview);
            }
            if ("total".equals(point.phase) || overview.getPowerFactor() == null) {
                overview.setPowerFactor(round(point.value, 4));
            }
        }

        for (Overview overview : overviewMap.values()) {
            if (overview.getPowerFactor() == null) {
                overview.setStatus("无数据");
            } else if (overview.getPowerFactor() >= threshold) {
                overview.setStatus("达标");
            } else {
                overview.setStatus("不达标");
            }
            if (overview.getReactivePower() != null) {
                overview.setReactiveDirection(overview.getReactivePower() < 0 ? "倒送" : "正常");
            }
        }
        result.addAll(overviewMap.values());
        return result;
    }

    /**
     * 趋势：按粒度分桶取均值，缺失桶补 null 以便前端断线
     */
    private Trend buildTrend(List<PfPoint> points, Map<Long, List<DeviceAttributeHistory>> historyMap, String granularity) {
        Trend trend = new Trend();
        if (points.isEmpty()) {
            return trend;
        }
        Set<String> bucketSet = new TreeSet<>();
        Map<Long, Map<String, List<Double>>> bucketValues = new HashMap<>();
        for (PfPoint point : points) {
            List<DeviceAttributeHistory> list = historyMap.get(point.attributeId);
            if (list == null) {
                continue;
            }
            Map<String, List<Double>> map = new HashMap<>();
            for (DeviceAttributeHistory history : list) {
                Double value = parseDouble(history.getValue());
                if (value == null || value < VALID_PF_MIN || value > VALID_PF_MAX) {
                    continue;
                }
                if (history.getCollectionTime() == null) {
                    continue;
                }
                String key = bucketKey(history.getCollectionTime(), granularity);
                bucketSet.add(key);
                List<Double> values = map.get(key);
                if (values == null) {
                    values = new ArrayList<>();
                    map.put(key, values);
                }
                values.add(value);
            }
            bucketValues.put(point.attributeId, map);
        }

        List<String> xAxis = new ArrayList<>(bucketSet);
        trend.setXAxis(xAxis);
        for (PfPoint point : points) {
            Series series = new Series();
            series.setDeviceId(point.deviceId);
            series.setPhase(point.phase);
            series.setUnit(point.unit == null ? "" : point.unit);
            series.setName(buildSeriesName(point.deviceName, point.phase));
            Map<String, List<Double>> map = bucketValues.get(point.attributeId);
            List<Double> data = new ArrayList<>();
            for (String key : xAxis) {
                List<Double> values = map == null ? null : map.get(key);
                data.add(values == null || values.isEmpty() ? null : round(avg(values), 4));
            }
            series.setData(data);
            trend.getSeries().add(series);
        }
        return trend;
    }

    /**
     * 分布：每台设备取总值点位的样本分档占比
     */
    private List<Distribution> buildDistribution(List<PfPoint> points, Map<Long, List<DeviceAttributeHistory>> historyMap, double threshold) {
        List<Distribution> result = new ArrayList<>();
        Map<Long, PfPoint> totalPointMap = new LinkedHashMap<>();
        for (PfPoint point : points) {
            if ("total".equals(point.phase) && !totalPointMap.containsKey(point.deviceId)) {
                totalPointMap.put(point.deviceId, point);
            }
        }
        if (totalPointMap.isEmpty()) {
            for (PfPoint point : points) {
                if (!totalPointMap.containsKey(point.deviceId)) {
                    totalPointMap.put(point.deviceId, point);
                }
            }
        }

        for (PfPoint point : totalPointMap.values()) {
            Distribution distribution = new Distribution();
            distribution.setDeviceId(point.deviceId);
            distribution.setDeviceName(point.deviceName);
            int[] counts = new int[BUCKET_LOWER.length];
            int total = 0;
            List<DeviceAttributeHistory> list = historyMap.get(point.attributeId);
            if (list != null) {
                for (DeviceAttributeHistory history : list) {
                    Double value = parseDouble(history.getValue());
                    if (value == null || value < VALID_PF_MIN || value > VALID_PF_MAX) {
                        continue;
                    }
                    counts[bucketIndex(value)]++;
                    total++;
                }
            }
            for (int i = 0; i < BUCKET_LOWER.length; i++) {
                Bucket bucket = new Bucket();
                bucket.setRange(bucketRange(i));
                bucket.setCount(counts[i]);
                bucket.setRatio(total == 0 ? 0D : round(counts[i] * 100D / total, 2));
                // ">=1.00" 档位属于异常数据，不计入达标
                bucket.setQualified(BUCKET_LOWER[i] >= threshold && BUCKET_LOWER[i] < 1.00D);
                distribution.getBuckets().add(bucket);
            }
            result.add(distribution);
        }
        return result;
    }

    /**
     * 统计：达标率与超限时长，超限时长按相邻样本时间差累计
     */
    private Statistics buildStatistics(List<PfPoint> points, Map<Long, List<DeviceAttributeHistory>> historyMap, double threshold) {
        Statistics statistics = new Statistics();
        statistics.setQualifiedThreshold(round(threshold, 4));

        Map<Long, PfPoint> totalPointMap = new LinkedHashMap<>();
        for (PfPoint point : points) {
            if ("total".equals(point.phase) && !totalPointMap.containsKey(point.deviceId)) {
                totalPointMap.put(point.deviceId, point);
            }
        }
        if (totalPointMap.isEmpty()) {
            for (PfPoint point : points) {
                if (!totalPointMap.containsKey(point.deviceId)) {
                    totalPointMap.put(point.deviceId, point);
                }
            }
        }

        double totalMinutes = 0D;
        double exceedMinutes = 0D;
        double sum = 0D;
        int count = 0;
        Double min = null;
        Double max = null;

        for (PfPoint point : totalPointMap.values()) {
            StatResult stat = calcStat(historyMap.get(point.attributeId), threshold);
            DeviceStat deviceStat = new DeviceStat();
            deviceStat.setDeviceId(point.deviceId);
            deviceStat.setDeviceName(point.deviceName);
            deviceStat.setAvgPowerFactor(stat.avg);
            deviceStat.setMinPowerFactor(stat.min);
            deviceStat.setMaxPowerFactor(stat.max);
            deviceStat.setTotalHours(round(stat.totalMinutes / 60D, 2));
            deviceStat.setExceedHours(round(stat.exceedMinutes / 60D, 2));
            deviceStat.setQualifiedRate(stat.totalMinutes <= 0 ? 0D : round((stat.totalMinutes - stat.exceedMinutes) * 100D / stat.totalMinutes, 2));
            statistics.getDeviceStats().add(deviceStat);

            totalMinutes += stat.totalMinutes;
            exceedMinutes += stat.exceedMinutes;
            if (stat.avg != null) {
                sum += stat.avg * stat.count;
                count += stat.count;
            }
            if (stat.min != null && (min == null || stat.min < min)) {
                min = stat.min;
            }
            if (stat.max != null && (max == null || stat.max > max)) {
                max = stat.max;
            }
        }

        statistics.setAvgPowerFactor(count == 0 ? null : round(sum / count, 4));
        statistics.setMinPowerFactor(round(min, 4));
        statistics.setMaxPowerFactor(round(max, 4));
        statistics.setTotalHours(round(totalMinutes / 60D, 2));
        statistics.setExceedHours(round(exceedMinutes / 60D, 2));
        statistics.setQualifiedRate(totalMinutes <= 0 ? 0D : round((totalMinutes - exceedMinutes) * 100D / totalMinutes, 2));
        return statistics;
    }

    private StatResult calcStat(List<DeviceAttributeHistory> list, double threshold) {
        StatResult result = new StatResult();
        if (list == null || list.isEmpty()) {
            return result;
        }
        Double prevValue = null;
        LocalDateTime prevTime = null;
        double sum = 0D;
        for (DeviceAttributeHistory history : list) {
            Double value = parseDouble(history.getValue());
            LocalDateTime time = history.getCollectionTime();
            if (value == null || value < VALID_PF_MIN || value > VALID_PF_MAX || time == null) {
                continue;
            }
            sum += value;
            result.count++;
            if (result.min == null || value < result.min) {
                result.min = value;
            }
            if (result.max == null || value > result.max) {
                result.max = value;
            }
            if (prevValue != null && prevTime != null) {
                long minutes = Duration.between(prevTime, time).toMinutes();
                if (minutes > 0 && minutes <= MAX_GAP_MINUTES) {
                    result.totalMinutes += minutes;
                    if (prevValue < threshold) {
                        result.exceedMinutes += minutes;
                    }
                }
            }
            prevValue = value;
            prevTime = time;
        }
        result.avg = result.count == 0 ? null : round(sum / result.count, 4);
        result.min = round(result.min, 4);
        result.max = round(result.max, 4);
        return result;
    }

    private void buildNotes(PowerFactorAnalysisVo vo, List<PfPoint> activePoints, List<PointInfo> excluded,
                            List<PfPoint> allPoints, int sampleCount, LocalDateTime firstTime,
                            LocalDateTime lastTime, boolean fallbackToZeroValue) {
        vo.getNotes().add("平均功率因数采用样本算术平均；达标率与超限时长按相邻样本时间差累计，间隔超过 "
                + MAX_GAP_MINUTES + " 分钟视为断采不计入。");
        if (allPoints.isEmpty()) {
            vo.getNotes().add("未找到名称包含“功率因数”的点位，请先在设备属性中维护功率因数点位。");
            return;
        }
        if (sampleCount == 0) {
            vo.getNotes().add("查询区间内无历史数据。历史表最新数据时间为 "
                    + (lastTime == null ? "无" : format(lastTime)) + "，请检查采集任务是否正常运行。");
        } else if (lastTime != null && lastTime.isBefore(LocalDateTime.now().minusDays(1))) {
            vo.getNotes().add("历史数据最新时间为 " + format(lastTime) + "，采集可能已中断，趋势末端为空属正常现象。");
        }
        if (fallbackToZeroValue) {
            vo.getNotes().add("全部功率因数点位当前值均为 0（设备未投运或点位未接入），已按原值返回，统计结果无业务意义。");
        }
        if (!excluded.isEmpty()) {
            vo.getNotes().add("已排除 " + excluded.size() + " 个有效值为 0 的点位（多为未投运的逆变器/储能 PCS），如需查看可传 includeZeroValue=true。");
        }
        boolean noGatherTime = true;
        for (PfPoint point : activePoints) {
            if (point.gatherTime != null) {
                noGatherTime = false;
                break;
            }
        }
        if (noGatherTime && !activePoints.isEmpty()) {
            vo.getNotes().add("实时表 device_attribute.gather_time 为空，概览中的采集时间不可用，数值为最近一次落库值。");
        }
        vo.getNotes().add("功率因数点位覆盖面有限（当前仅接入部分低压出线表），按区域/回路分析需先补充计量点位。");
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
        if (deviceIds == null || deviceIds.isEmpty()) {
            return map;
        }
        List<Device> devices = deviceService.listByIds(new ArrayList<>(deviceIds));
        if (devices == null) {
            return map;
        }
        for (Device device : devices) {
            map.put(device.getId(), device.getDeviceName());
        }
        return map;
    }

    private PointInfo toPointInfo(PfPoint point, String reason) {
        PointInfo info = new PointInfo();
        info.setAttributeId(point.attributeId);
        info.setDeviceId(point.deviceId);
        info.setDeviceName(point.deviceName);
        info.setPointName(point.pointName);
        info.setPhase(point.phase);
        info.setValue(round(point.value, 4));
        info.setGatherTime(format(point.gatherTime));
        info.setUnit(point.unit);
        info.setReason(reason);
        return info;
    }

    private String buildSeriesName(String deviceName, String phase) {
        String prefix = StringUtils.isBlank(deviceName) ? "未知设备" : deviceName;
        return prefix + "-" + phaseLabel(phase);
    }

    private String phaseLabel(String phase) {
        if ("A".equals(phase)) {
            return "A相";
        }
        if ("B".equals(phase)) {
            return "B相";
        }
        if ("C".equals(phase)) {
            return "C相";
        }
        return "总";
    }

    private String phaseOf(String attributeName) {
        if (attributeName == null) {
            return "total";
        }
        if (attributeName.contains("A相")) {
            return "A";
        }
        if (attributeName.contains("B相")) {
            return "B";
        }
        if (attributeName.contains("C相")) {
            return "C";
        }
        return "total";
    }

    private DeviceAttribute matchAttr(List<DeviceAttribute> attrs, String include, String... excludes) {
        for (DeviceAttribute attribute : attrs) {
            String name = attribute.getAttributeName();
            if (name == null || !name.contains(include)) {
                continue;
            }
            boolean skip = false;
            for (String exclude : excludes) {
                if (name.contains(exclude)) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                return attribute;
            }
        }
        return null;
    }

    private Double valueOf(DeviceAttribute attribute) {
        return attribute == null ? null : round(parseDouble(attribute.getValue()), 4);
    }

    private String normalizeGranularity(String granularity) {
        if ("raw".equalsIgnoreCase(granularity) || "day".equalsIgnoreCase(granularity)
                || "month".equalsIgnoreCase(granularity)) {
            return granularity.toLowerCase();
        }
        return "hour";
    }

    private String bucketKey(LocalDateTime time, String granularity) {
        if ("day".equals(granularity)) {
            return time.format(DAY_FMT);
        }
        if ("month".equals(granularity)) {
            return time.format(MONTH_FMT);
        }
        if ("raw".equals(granularity)) {
            return time.format(RAW_FMT);
        }
        return time.format(HOUR_FMT);
    }

    private int bucketIndex(double value) {
        if (value < 0.80D) {
            return 0;
        }
        if (value < 0.85D) {
            return 1;
        }
        if (value < 0.90D) {
            return 2;
        }
        if (value < 0.95D) {
            return 3;
        }
        if (value < 1.00D) {
            return 4;
        }
        return 5;
    }

    private String bucketRange(int index) {
        switch (index) {
            case 0:
                return "<0.80";
            case 1:
                return "0.80~0.85";
            case 2:
                return "0.85~0.90";
            case 3:
                return "0.90~0.95";
            case 4:
                return "0.95~1.00";
            default:
                return ">=1.00";
        }
    }

    private Set<Long> parseIds(String ids) {
        Set<Long> result = new HashSet<>();
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

    private double avg(List<Double> values) {
        double sum = 0D;
        for (Double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private Double round(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return BigDecimalUtil.round(value, scale);
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(FMT);
    }

    /**
     * 内部点位模型
     */
    private static class PfPoint {
        private Long attributeId;
        private Long deviceId;
        private String deviceName;
        private String pointName;
        private String phase;
        private Double value;
        private LocalDateTime gatherTime;
        private String unit;
    }

    /**
     * 统计中间结果
     */
    private static class StatResult {
        private double totalMinutes;
        private double exceedMinutes;
        private Double avg;
        private Double min;
        private Double max;
        private int count;
    }

    /**
     * 保留小数工具，避免依赖外部工具类行为
     */
    private static class BigDecimalUtil {
        private static Double round(double value, int scale) {
            java.math.BigDecimal decimal = java.math.BigDecimal.valueOf(value);
            return decimal.setScale(scale, java.math.RoundingMode.HALF_UP).doubleValue();
        }
    }
}
