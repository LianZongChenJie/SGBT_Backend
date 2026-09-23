package org.jeecg.modules.bems.visualization.tngl.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.entity.DeviceAttributeHistory;
import org.jeecg.modules.bems.mdm.entity.EquipmentCategory;
import org.jeecg.modules.bems.mdm.mapper.DeviceAttributeHistoryMapper;
import org.jeecg.modules.bems.mdm.mapper.DeviceAttributeMapper;
import org.jeecg.modules.bems.mdm.mapper.DeviceMapper;
import org.jeecg.modules.bems.mdm.mapper.EquipmentCategoryMapper;
import org.jeecg.modules.bems.visualization.tngl.service.CarbonManagementService;
import org.jeecg.modules.bems.visualization.tngl.vo.BoilerEnergyCarbonConversionVO;
import org.jeecg.modules.bems.visualization.tngl.vo.BoilerEnergyConsumptionItemVO;
import org.jeecg.modules.bems.visualization.tngl.vo.BoilerEnergyConsumptionVO;
import org.jeecg.modules.bems.visualization.tngl.vo.PhotovoltaicEnergyIndexVO;
import org.jeecg.modules.bems.visualization.tngl.vo.PhotovoltaicPowerGenerationVO;
import org.jeecg.modules.bems.visualization.tngl.vo.SeriesVO;
import org.jeecg.modules.bems.visualization.tngl.vo.WaterTreatmentProductionVO;
import org.jeecg.modules.bems.visualization.utils.DateRangeUtils;
import org.jeecg.modules.bems.visualization.vo.PowerTrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CarbonManagementServiceImpl implements CarbonManagementService {

    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private DeviceAttributeMapper deviceAttributeMapper;
    @Autowired
    private DeviceAttributeHistoryMapper deviceAttributeHistoryMapper;
    @Autowired
    private EquipmentCategoryMapper equipmentCategoryMapper;

    /**
     * 日期格式
     */
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    @Override
    public Map<String, BigDecimal> boilerCarbonEmissionsIndex() {
        List<String> deviceNames = Arrays.asList("1#锅炉", "2#锅炉", "3#锅炉");
        // 查询系统
        List<EquipmentCategory> equipmentCategories = equipmentCategoryMapper.selectList(
                new QueryWrapper<EquipmentCategory>().in("category_name", "锅炉系统"));
        List<Long> equipmentCategoryIds = equipmentCategories.stream()
                .map(EquipmentCategory::getId).collect(Collectors.toList());

        // 设备
        List<Device> devices = deviceMapper.selectList(
                new QueryWrapper<Device>()
                        .in("device_name", deviceNames)
                        .in("category_id", equipmentCategoryIds)
        );
        if (CollectionUtils.isEmpty(devices)) {
            return Collections.emptyMap();
        }
        Map<Long, String> deviceMap = devices.stream()
                .collect(Collectors.toMap(Device::getId, Device::getDeviceName, (a, b) -> b));

        // 属性
        List<DeviceAttribute> attrs = deviceAttributeMapper.selectList(
                new QueryWrapper<DeviceAttribute>()
                        .in("device_id", deviceMap.keySet())
                        .eq("attribute_name", "蒸汽累计流量"));
        if (CollectionUtils.isEmpty(attrs)) {
            return Collections.emptyMap();
        }
        Map<Long, Long> attrToDevice = attrs.stream()
                .collect(Collectors.toMap(DeviceAttribute::getId, DeviceAttribute::getDeviceId));
        Set<Long> attrIds = attrToDevice.keySet();

        // 3. 批量查询最近 2 小时历史数据
        LocalDateTime since = LocalDateTime.now().minusHours(2);
        List<DeviceAttributeHistory> histories = deviceAttributeHistoryMapper.selectList(
                new QueryWrapper<DeviceAttributeHistory>()
                        .in("attribute_Id", attrIds)
                        .ge("collection_time", since)
                        .orderByDesc("collection_time"));

        // 4. 按 attributeId 分组
        Map<Long, List<DeviceAttributeHistory>> grouped = histories.stream()
                .filter(h -> h.getValue() != null && h.getCollectionTime() != null)
                .collect(Collectors.groupingBy(DeviceAttributeHistory::getAttributeId));

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<DeviceAttributeHistory>> entry : grouped.entrySet()) {
            Long attributeId = entry.getKey();
            Long deviceId = attrToDevice.get(attributeId);
            if (deviceId == null) {
                continue;
            }
            List<DeviceAttributeHistory> list = entry.getValue();
            // 已按时间倒序，第一条即最新
            DeviceAttributeHistory latest = list.get(0);
            LocalDateTime target = latest.getCollectionTime().minusHours(1);

            // 找 <= target 的最近一条
            DeviceAttributeHistory previous = list.stream()
                    .filter(h -> !h.getCollectionTime().isAfter(target))
                    .findFirst()
                    .orElse(null);

            if (previous == null) {
                continue;
            }

            BigDecimal valueMax = parseDecimal(latest.getValue());
            BigDecimal valueMin = parseDecimal(previous.getValue());
            if (valueMax == null || valueMin == null) {
                continue;
            }

            result.put(deviceMap.get(deviceId), valueMax.subtract(valueMin));
        }
        return result;
    }

    /**
     * 锅炉能耗转换碳排放量
     */
    @Override
    public List<BoilerEnergyCarbonConversionVO> boilerEnergyCarbonConversion() {
        // 标况体积流量
        String attribute_name = "_标况体积流量";
        List<Map<String, Object>> list = deviceAttributeHistoryMapper.sumLatestValueGroupByAttributeName(attribute_name);

        // 天然气简化排放因子：kg CO₂ / Nm³
        BigDecimal emissionFactor = new BigDecimal("1.92");

        List<BoilerEnergyCarbonConversionVO> result = new ArrayList<>();
        for (Map<String, Object> row : list) {
            // 标况体积消耗量（Nm³）
            BigDecimal value = toBigDecimal(row.get("totalValue"));

            // 碳排放量（kg CO₂）= 标况体积消耗量 × 排放因子
            BigDecimal carbonEmission = value.multiply(emissionFactor)
                    .setScale(2, RoundingMode.HALF_UP);

            // 设备名称转换：燃气表1_标况体积流量 -> 锅炉1
            String attributeName = String.valueOf(row.get("attributeName"));
            attributeName = attributeName.replaceAll(attribute_name, "");
            attributeName = attributeName.replaceAll("燃气表", "锅炉");

            result.add(new BoilerEnergyCarbonConversionVO(attributeName, value, carbonEmission));
        }
        return result;
    }


    @Override
    public BoilerEnergyConsumptionVO boilerEnergyConsumption(String period) {
        // 1. 归一化周期 + 解析区间（统一走 DateRangeUtils）
        String normalized = DateRangeUtils.normalizePeriod(period);
        LocalDateTime[] range = DateRangeUtils.resolveRange(normalized);

        BoilerEnergyConsumptionVO result = new BoilerEnergyConsumptionVO();
        result.setPeriod(normalized);

        // 2. 查锅炉系统
        List<EquipmentCategory> categories = equipmentCategoryMapper.selectList(
                new QueryWrapper<EquipmentCategory>().eq("category_name", "锅炉系统"));
        List<Long> categoryIds = categories.stream()
                .map(EquipmentCategory::getId).collect(Collectors.toList());
        if (categoryIds.isEmpty()) {
            result.setBoilers(new ArrayList<>());
            return result;
        }

        // 3. 查 3 个锅炉
        List<String> boilerNames = Arrays.asList("1#锅炉", "2#锅炉", "3#锅炉");
        List<Device> boilers = deviceMapper.selectList(
                new QueryWrapper<Device>()
                        .in("category_id", categoryIds)
                        .in("device_name", boilerNames)
                        .orderByAsc("device_code"));
        if (CollectionUtils.isEmpty(boilers)) {
            result.setBoilers(new ArrayList<>());
            return result;
        }
        // 属性名关键字
        String waterKey = "锅炉给水流量";
        String steamKey = "蒸汽累计流量";;

        // 4. 一次性查出这些锅炉下所有属性，避免循环查库
        List<Long> boilerIds = boilers.stream().map(Device::getId).collect(Collectors.toList());
        List<DeviceAttribute> allAttrs = deviceAttributeMapper.selectList(
                new QueryWrapper<DeviceAttribute>().in("device_id", boilerIds).in("attribute_name", Arrays.asList(waterKey, steamKey)));

        Map<Long, List<DeviceAttribute>> attrsByDevice = allAttrs.stream()
                .filter(a -> a.getDeviceId() != null)
                .collect(Collectors.groupingBy(DeviceAttribute::getDeviceId));


        List<BoilerEnergyConsumptionItemVO> boilersList = new ArrayList<>();

        for (Device boiler : boilers) {
            BoilerEnergyConsumptionItemVO item = new BoilerEnergyConsumptionItemVO();
            item.setDeviceId(boiler.getId());
            item.setDeviceName(boiler.getDeviceName());
            item.setDeviceCode(boiler.getDeviceCode());

            List<DeviceAttribute> attrs = attrsByDevice.getOrDefault(boiler.getId(), Collections.emptyList());

            Long waterAttrId = matchAttrId(attrs, waterKey);
            Long steamAttrId = matchAttrId(attrs, steamKey);

            Map<String, BigDecimal> waterMap = dailyConsumptionByAttrIds(
                    Collections.singletonList(waterAttrId), range[0], range[1]);
            Map<String, BigDecimal> steamMap = dailyConsumptionByAttrIds(
                    Collections.singletonList(steamAttrId), range[0], range[1]);

            List<PowerTrendVO> waterList = fillTrend(waterMap, range[0], range[1]);
            List<PowerTrendVO> steamList = fillTrend(steamMap, range[0], range[1]);

            List<SeriesVO> series = new ArrayList<>();
            series.add(buildSeries("水耗", waterList, "t"));
            series.add(buildSeries("汽耗", steamList, "t"));
            item.setSeries(series);

            boilersList.add(item);
        }

        result.setBoilers(boilersList);
        return result;
    }


    @Override
    public WaterTreatmentProductionVO waterTreatmentProduction(String period) {
        String normalized = DateRangeUtils.normalizePeriod(period);
        LocalDateTime[] range = DateRangeUtils.resolveRange(normalized);

        final String rawWaterKey = "超滤进水流量";    // 原水输入
        final String firstRoKey = "一级RO产水流量";  // 一次成水量
        final String secondRoKey = "二级RO产水流量";  // 二次成水量

        Map<String, BigDecimal> rawMap = dailyConsumptionByAttr(rawWaterKey, range[0], range[1]);
        Map<String, BigDecimal> firstMap = dailyConsumptionByAttr(firstRoKey, range[0], range[1]);
        Map<String, BigDecimal> secondMap = dailyConsumptionByAttr(secondRoKey, range[0], range[1]);

        List<PowerTrendVO> rawList = fillTrend(rawMap, range[0], range[1]);
        List<PowerTrendVO> firstList = fillTrend(firstMap, range[0], range[1]);
        List<PowerTrendVO> secondList = fillTrend(secondMap, range[0], range[1]);

        WaterTreatmentProductionVO result = new WaterTreatmentProductionVO();
        result.setPeriod(normalized);
        result.setXAxis(rawList.stream()
                .map(PowerTrendVO::getBucketTime).collect(Collectors.toList()));

        List<SeriesVO> series = new ArrayList<>();
        series.add(buildSeries("原水输入", rawList, "t"));
        series.add(buildSeries("一次成水量", firstList, "t"));
        series.add(buildSeries("二次成水量", secondList, "t"));
        result.setSeries(series);

        return result;
    }


    /**
     * 按属性名（模糊匹配）按天聚合累计流量的日消耗量（MAX - MIN）
     */
    private Map<String, BigDecimal> dailyConsumptionByAttr(String attrName,
                                                           LocalDateTime start,
                                                           LocalDateTime end) {
        if (attrName == null || attrName.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DeviceAttribute> attrs = deviceAttributeMapper.selectList(
                new QueryWrapper<DeviceAttribute>().like("attribute_name", attrName));
        if (CollectionUtils.isEmpty(attrs)) {
            return Collections.emptyMap();
        }
        List<Long> attrIds = attrs.stream()
                .map(DeviceAttribute::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return dailyConsumptionByAttrIds(attrIds, start, end);
    }

    /**
     * 按 attributeId 列表按天聚合累计流量的日消耗量（MAX - MIN）
     */
    private Map<String, BigDecimal> dailyConsumptionByAttrIds(List<Long> attrIds,
                                                              LocalDateTime start,
                                                              LocalDateTime end) {
        List<Long> ids = attrIds == null ? Collections.emptyList()
                : attrIds.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> rows = deviceAttributeHistoryMapper.selectMaps(
                new QueryWrapper<DeviceAttributeHistory>()
                        .select("DATE_FORMAT(collection_time, '%Y-%m-%d') AS bucket_time",
                                "MAX(value) AS max_value",
                                "MIN(value) AS min_value")
                        .in("attribute_id", ids)
                        .ge("collection_time", start)
                        .le("collection_time", end)
                        .groupBy("DATE_FORMAT(collection_time, '%Y-%m-%d')")
        );

        Map<String, BigDecimal> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String day = String.valueOf(row.get("bucket_time"));
            BigDecimal max = toBigDecimal(row.get("max_value"));
            BigDecimal min = toBigDecimal(row.get("min_value"));
            //保留两位四舍五入
            result.put(day, max.subtract(min).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }
        return result;
    }

    /**
     * 把 dayMap 补全为从 start 到 end 的连续日期序列（无数据补 0）
     */
    private List<PowerTrendVO> fillTrend(Map<String, BigDecimal> dayMap,
                                         LocalDateTime start, LocalDateTime end) {
        List<PowerTrendVO> result = new ArrayList<>();
        LocalDate cursor = start.toLocalDate();
        LocalDate last = end.toLocalDate();
        while (!cursor.isAfter(last)) {
            String key = cursor.format(DAY_FMT);
            result.add(new PowerTrendVO(key, dayMap.getOrDefault(key, BigDecimal.ZERO)));
            cursor = cursor.plusDays(1);
        }
        return result;
    }


    private SeriesVO buildSeries(String name, List<PowerTrendVO> list, String unit) {
        List<BigDecimal> data = list.stream()
                .map(PowerTrendVO::getTotalValue)
                .collect(Collectors.toList());
        return new SeriesVO(name, data, unit);
    }

    /**
     * 从属性列表中匹配关键字对应的属性 id（包含匹配，取第一个）
     */
    private Long matchAttrId(List<DeviceAttribute> attrs, String keyword) {
        if (CollectionUtils.isEmpty(attrs) || keyword == null || keyword.isEmpty()) {
            return null;
        }
        return attrs.stream()
                .filter(a -> a.getAttributeName() != null
                        && a.getAttributeName().contains(keyword))
                .map(DeviceAttribute::getId)
                .findFirst()
                .orElse(null);
    }
    /**
     * 光伏属性名
     */
    @Override
    public PhotovoltaicEnergyIndexVO photovoltaicEnergyIndex(String period) {
        String PV_CUMULATIVE_ATTR = "累计发电量";
        //电力碳排放因子 (kg CO2 / kWh)
        BigDecimal ELECTRICITY_CARBON_FACTOR = new BigDecimal("0.5810");
        String normalized = DateRangeUtils.normalizePeriod(period);
        LocalDateTime[] range = DateRangeUtils.resolveRange(normalized);

        // 1. 查询光伏设备的累计发电量（按天聚合）
        List<PowerTrendVO> cumulativeList = getDailyCumulativePvTrend(
                PV_CUMULATIVE_ATTR, range[0], range[1]);

        // 2. 计算累计发电量（区间内最后一天的值 - 第一天之前的值）
        BigDecimal totalPowerGeneration = calculateTotalGeneration(cumulativeList);

        // 3. 计算碳排放量 (kg CO2) = 发电量(kWh) × 碳排因子
        // 注意：TODO 如果发电量单位是 MWh，需要先 ×1000 转为 kWh
        BigDecimal carbonEmission = totalPowerGeneration
                .multiply(ELECTRICITY_CARBON_FACTOR)
                .setScale(2, RoundingMode.HALF_UP);

        return new PhotovoltaicEnergyIndexVO(normalized, totalPowerGeneration, carbonEmission);
    }

    @Override
    public PhotovoltaicPowerGenerationVO photovoltaicPowerGeneration(String period) {
        String PV_DAILY_ATTR = "日发电量";
        String normalized = DateRangeUtils.normalizePeriod(period);
        LocalDateTime[] range = DateRangeUtils.resolveRange(normalized);

        // 按天聚合日发电量
        List<PowerTrendVO> dailyList = getDailyPvTrend(PV_DAILY_ATTR, range[0], range[1]);

        PhotovoltaicPowerGenerationVO result = new PhotovoltaicPowerGenerationVO();
        result.setPeriod(normalized);
        result.setXAxis(dailyList.stream()
                .map(PowerTrendVO::getBucketTime)
                .collect(Collectors.toList()));
        result.setSeries(dailyList.stream()
                .map(PowerTrendVO::getTotalValue)
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 按天聚合光伏日发电量
     */
    private List<PowerTrendVO> getDailyPvTrend(String attrName,
                                               LocalDateTime start,
                                               LocalDateTime end) {
        List<DeviceAttribute> attrs = deviceAttributeMapper.selectList(
                new QueryWrapper<DeviceAttribute>().like("attribute_name", attrName));

        if (CollectionUtils.isEmpty(attrs)) {
            return fillTrend(Collections.emptyMap(), start, end);
        }

        List<Long> attrIds = attrs.stream()
                .map(DeviceAttribute::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (attrIds.isEmpty()) {
            return fillTrend(Collections.emptyMap(), start, end);
        }

        // 日发电量是瞬时累加值，按天 SUM
        List<Map<String, Object>> rows = deviceAttributeHistoryMapper.selectMaps(
                new QueryWrapper<DeviceAttributeHistory>()
                        .select("DATE_FORMAT(collection_time, '%Y-%m-%d') AS bucket_time",
                                "SUM(value) AS total_value")
                        .in("attribute_id", attrIds)
                        .ge("collection_time", start)
                        .le("collection_time", end)
                        .groupBy("DATE_FORMAT(collection_time, '%Y-%m-%d')")
        );

        Map<String, BigDecimal> dayMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String day = String.valueOf(row.get("bucket_time"));
            BigDecimal value = toBigDecimal(row.get("total_value"))
                    .setScale(2, RoundingMode.HALF_UP);
            dayMap.put(day, value);
        }

        return fillTrend(dayMap, start, end);
    }

    /**
     * 按天聚合光伏累计发电量（取每日 MAX-MIN 作为当日增量）
     */
    private List<PowerTrendVO> getDailyCumulativePvTrend(String attrName,
                                                         LocalDateTime start,
                                                         LocalDateTime end) {
        List<DeviceAttribute> attrs = deviceAttributeMapper.selectList(
                new QueryWrapper<DeviceAttribute>().like("attribute_name", attrName));

        if (CollectionUtils.isEmpty(attrs)) {
            return fillTrend(Collections.emptyMap(), start, end);
        }

        List<Long> attrIds = attrs.stream()
                .map(DeviceAttribute::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (attrIds.isEmpty()) {
            return fillTrend(Collections.emptyMap(), start, end);
        }

        // 累计值：每天 MAX - MIN = 当日增量
        List<Map<String, Object>> rows = deviceAttributeHistoryMapper.selectMaps(
                new QueryWrapper<DeviceAttributeHistory>()
                        .select("DATE_FORMAT(collection_time, '%Y-%m-%d') AS bucket_time",
                                "MAX(value) AS max_value",
                                "MIN(value) AS min_value")
                        .in("attribute_id", attrIds)
                        .ge("collection_time", start)
                        .le("collection_time", end)
                        .groupBy("DATE_FORMAT(collection_time, '%Y-%m-%d')")
        );

        Map<String, BigDecimal> dayMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String day = String.valueOf(row.get("bucket_time"));
            BigDecimal max = toBigDecimal(row.get("max_value"));
            BigDecimal min = toBigDecimal(row.get("min_value"));
            BigDecimal diff = max.subtract(min).max(BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            dayMap.put(day, diff);
        }

        return fillTrend(dayMap, start, end);
    }

    /**
     * 计算区间内累计发电量（各天增量之和）
     */
    private BigDecimal calculateTotalGeneration(List<PowerTrendVO> dailyList) {
        if (CollectionUtils.isEmpty(dailyList)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return dailyList.stream()
                .map(PowerTrendVO::getTotalValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
    // ---------- 类型安全转换 ----------

    private BigDecimal parseDecimal(String v) {
        try {
            return new BigDecimal(v);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(o.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
