package org.jeecg.modules.bems.visualization.zhjsc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import org.jeecg.modules.bems.alarm.mapper.AlarmLevelMapper;
import org.jeecg.modules.bems.alarm.mapper.AlarmRecordMapper;
import org.jeecg.modules.bems.energyAnalysis.constant.BusinessConfigConstant;
import org.jeecg.modules.bems.energyAnalysis.entity.MeteringPointDataMonth;
import org.jeecg.modules.bems.energyAnalysis.service.ICarbonEmissionFactorService;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointDataDayService;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointDataMonthService;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.entity.DeviceAttributeHistory;
import org.jeecg.modules.bems.mdm.entity.EquipmentCategory;
import org.jeecg.modules.bems.mdm.mapper.DeviceAttributeHistoryMapper;
import org.jeecg.modules.bems.mdm.mapper.DeviceAttributeMapper;
import org.jeecg.modules.bems.mdm.mapper.DeviceMapper;
import org.jeecg.modules.bems.mdm.mapper.EquipmentCategoryMapper;
import org.jeecg.modules.bems.service.IBusinessConfigService;
import org.jeecg.modules.bems.visualization.utils.DateRangeUtils;
import org.jeecg.modules.bems.visualization.vo.PowerTrendVO;
import org.jeecg.modules.bems.visualization.zhjsc.service.SmartCockpitService;
import org.jeecg.modules.bems.visualization.zhjsc.vo.AlarmCategoryCountVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.AlarmListVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.AlarmRecordSimpleVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.CarbonFootprintVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.ChartDataVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.DeviceAttributeStatusVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.DeviceStatusVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.EnergySupplyOverviewVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.EnvironmentalMonitoringDetailVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.EnvironmentalMonitoringItemVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.EnvironmentalMonitoringVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.OperationStatusKeyEquipmentVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.ProductionItemVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.ProductionOverviewVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.SteamElectricityProductionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SmartCockpitServiceImpl implements SmartCockpitService {
    @Autowired
    private EquipmentCategoryMapper equipmentCategoryMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private DeviceAttributeMapper deviceAttributeMapper;
    @Autowired
    private DeviceAttributeHistoryMapper deviceAttributeHistoryMapper;
    @Autowired
    private AlarmRecordMapper alarmRecordMapper;
    @Autowired
    private AlarmLevelMapper alarmLevelMapper;


    /**
     * cems1、cems2、cems3的二氧化碳浓度、粉尘浓度
     * 根据传入周期（本周 / 本月 / 本年）查询对应区间
     *
     * @param period 周期：本周/WEEK、本月/MONTH、本年/YEAR
     * @return 三层结构：设备 -> 监测项 -> 周期 -> 数值
     */
    @Override
    public List<EnvironmentalMonitoringVO> environmentalMonitoring(String period) {
        // 归一化入参
        String normalized = DateRangeUtils.normalizePeriod(period);
        String periodLabel = DateRangeUtils.toPeriodLabel(normalized);

        /**
         * 输出 key：设备名 -> 展示名
         */
        Map<String, String> DEVICE_DISPLAY_NAME = new LinkedHashMap<>() {{
            put("1#CEMS", "CEMS1");
            put("2#CEMS", "CEMS2");
            put("3#CEMS", "CEMS3");
        }};

        /**
         * 监测项：属性名关键字 -> 展示名
         */
        Map<String, String> ITEM_DISPLAY_NAME = new LinkedHashMap<>() {{
            put("CO2", "二氧化碳浓度");
            put("粉尘浓度", "粉尘浓度");
        }};

        // 查询 CEMS 系统
        List<EquipmentCategory> equipmentCategories = equipmentCategoryMapper.selectList(
                new QueryWrapper<EquipmentCategory>().eq("category_name", "cems系统"));
        List<Long> equipmentCategoryIds = equipmentCategories.stream()
                .map(EquipmentCategory::getId).collect(Collectors.toList());
        if (equipmentCategoryIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 查询 CEMS 系统的设备
        List<Device> devices = deviceMapper.selectList(
                new QueryWrapper<Device>().in("category_id", equipmentCategoryIds)
        );
        if (devices.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> deviceIds = devices.stream().map(Device::getId).collect(Collectors.toList());

        // 建立属性元信息索引：attributeId -> AttrMeta
        Map<Long, AttrMeta> attrMetaMap = buildAttrMetaMap(deviceIds, DEVICE_DISPLAY_NAME, ITEM_DISPLAY_NAME);
        if (attrMetaMap.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> attrIds = new ArrayList<>(attrMetaMap.keySet());

        // 只查询传入周期对应的时间区间
        LocalDateTime[] range = DateRangeUtils.resolveRange(normalized);
        Map<Long, Double> valueMap = sumByAttribute(deviceIds, attrIds, range[0], range[1]);

        // 组装中间结构：设备名 -> 监测项 -> 周期 -> 数值
        Map<String, Map<String, Map<String, Double>>> data = new LinkedHashMap<>();
        for (Map.Entry<Long, AttrMeta> e : attrMetaMap.entrySet()) {
            Long attrId = e.getKey();
            AttrMeta meta = e.getValue();

            Map<String, Double> periodMap = data
                    .computeIfAbsent(meta.deviceName, k -> new LinkedHashMap<>())
                    .computeIfAbsent(meta.itemName, k -> new LinkedHashMap<>());
            periodMap.put(periodLabel, valueMap.getOrDefault(attrId, 0.0));
        }

        // 按固定结构补全并返回数组格式
        return buildArrayResult(data, DEVICE_DISPLAY_NAME, ITEM_DISPLAY_NAME, periodLabel);
    }

    /**
     * 查询 CEMS 设备属性，并建立 attributeId -> AttrMeta 索引
     */
    private Map<Long, AttrMeta> buildAttrMetaMap(List<Long> deviceIds, Map<String, String> DEVICE_DISPLAY_NAME, Map<String, String> ITEM_DISPLAY_NAME) {

        QueryWrapper<DeviceAttribute> wrapper = new QueryWrapper<DeviceAttribute>()
                .in("device_id", deviceIds)
                .and(w -> {
                    boolean first = true;
                    for (String devKey : DEVICE_DISPLAY_NAME.keySet()) {
                        for (String itemKey : ITEM_DISPLAY_NAME.keySet()) {
                            if (!first) {
                                w.or();
                            }
                            first = false;
                            w.nested(i -> i
                                    .like("attribute_name", devKey)
                                    .like("attribute_name", itemKey));
                        }
                    }
                });

        List<DeviceAttribute> deviceAttributes = deviceAttributeMapper.selectList(wrapper);

        Map<Long, AttrMeta> attrMetaMap = new HashMap<>();
        for (DeviceAttribute attr : deviceAttributes) {
            String attrName = attr.getAttributeName();
            if (attrName == null) {
                continue;
            }
            String devKey = DEVICE_DISPLAY_NAME.keySet().stream()
                    .filter(attrName::contains)
                    .findFirst()
                    .orElse(null);
            String itemKey = ITEM_DISPLAY_NAME.keySet().stream()
                    .filter(attrName::contains)
                    .findFirst()
                    .orElse(null);
            if (devKey == null || itemKey == null) {
                continue;
            }
            attrMetaMap.put(attr.getId(), new AttrMeta(
                    attr.getDeviceId(),
                    DEVICE_DISPLAY_NAME.get(devKey),
                    ITEM_DISPLAY_NAME.get(itemKey)));
        }
        return attrMetaMap;
    }

    /**
     * 按 attribute_id 聚合指定时间区间的 SUM(value)
     *
     * @return key = attributeId, value = 聚合值
     */
    private Map<Long, Double> sumByAttribute(List<Long> deviceIds,
                                             List<Long> attrIds,
                                             LocalDateTime start,
                                             LocalDateTime end) {
        List<Map<String, Object>> rows = deviceAttributeHistoryMapper.selectMaps(
                new QueryWrapper<DeviceAttributeHistory>()
                        .select("attribute_id", "SUM(value) AS total_value")
                        .in("device_id", deviceIds)
                        .in("attribute_id", attrIds)
                        .ge("collection_time", start)
                        .le("collection_time", end)
                        .groupBy("attribute_id")
        );

        Map<Long, Double> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long attrId = toLong(row.get("attribute_id"));
            if (attrId != null) {
                result.put(attrId, toDouble(row.get("total_value")));
            }
        }
        return result;
    }
    /**
     * 组装环境监测结果，只输出当前传入的一个周期
     */
    private List<EnvironmentalMonitoringVO> buildArrayResult(Map<String, Map<String, Map<String, Double>>> data,
                                                             Map<String, String> DEVICE_DISPLAY_NAME,
                                                             Map<String, String> ITEM_DISPLAY_NAME,
                                                             String periodLabel) {
        List<EnvironmentalMonitoringVO> result = new ArrayList<>();

        for (Map.Entry<String, String> devEntry : DEVICE_DISPLAY_NAME.entrySet()) {
            String deviceDisplayName = devEntry.getValue();

            EnvironmentalMonitoringVO deviceVO = new EnvironmentalMonitoringVO();
            deviceVO.setName(deviceDisplayName);

            List<EnvironmentalMonitoringItemVO> dataArray = new ArrayList<>();
            Map<String, Map<String, Double>> itemMap =
                    data.getOrDefault(deviceDisplayName, Collections.emptyMap());

            for (Map.Entry<String, String> itemEntry : ITEM_DISPLAY_NAME.entrySet()) {
                String itemDisplayName = itemEntry.getValue();

                Map<String, Double> periodMap =
                        itemMap.getOrDefault(itemDisplayName, Collections.emptyMap());

                EnvironmentalMonitoringItemVO itemVO = new EnvironmentalMonitoringItemVO();
                itemVO.setType(itemDisplayName);

                // 只输出当前周期一个节点
                EnvironmentalMonitoringDetailVO detail = new EnvironmentalMonitoringDetailVO();
                detail.setLabel(periodLabel);
                detail.setValue(periodMap.getOrDefault(periodLabel, 0.0));

                List<EnvironmentalMonitoringDetailVO> detailData = new ArrayList<>();
                detailData.add(detail);
                itemVO.setDetailData(detailData);

                dataArray.add(itemVO);
            }

            deviceVO.setData(dataArray);
            result.add(deviceVO);
        }

        return result;
    }
    @Override
    public OperationStatusKeyEquipmentVO operationStatusKeyEquipment() {

        // 查询 系统
        List<EquipmentCategory> equipmentCategories = equipmentCategoryMapper.selectList(
                new QueryWrapper<EquipmentCategory>().in("category_name", "锅炉系统"));
        List<Long> equipmentCategoryIds = equipmentCategories.stream()
                .map(EquipmentCategory::getId).collect(Collectors.toList());

        // 查询重点设备
        List<String> keywords = Arrays.asList("1#锅炉", "2#锅炉", "3#锅炉", "锅炉辅机");

        // 1. 先查询符合条件的设备
        QueryWrapper<Device> deviceWrapper = new QueryWrapper<>();
        if (!equipmentCategoryIds.isEmpty()) {
            deviceWrapper.in("category_id", equipmentCategoryIds);
        }
        if (!keywords.isEmpty()) {
            deviceWrapper.and(w -> {
                boolean first = true;
                for (String kw : keywords) {
                    if (!first) {
                        w.or();
                    }
                    first = false;
                    w.like("device_name", kw);
                }
            });
        }
        deviceWrapper.orderByAsc("device_code");
        List<Device> devices = deviceMapper.selectList(deviceWrapper);

        OperationStatusKeyEquipmentVO result = new OperationStatusKeyEquipmentVO();
        List<DeviceStatusVO> devicesList = new ArrayList<>();

        if (CollectionUtils.isEmpty(devices)) {
            result.setTotal(0);
            result.setDevices(devicesList);
            return result;
        }

        List<Long> deviceIds = devices.stream().map(Device::getId).collect(Collectors.toList());

        // 2. 查询这些设备对应的属性
        List<DeviceAttribute> attributes = deviceAttributeMapper.selectList(
                new QueryWrapper<DeviceAttribute>()
                        .in("device_id", deviceIds)
                        .and(w -> w.like("attribute_code", "CYSB1YX") //锅炉1运行状态
                                .or().like("attribute_code", "CYSB2YX")//锅炉2运行状态
                                .or().like("attribute_code", "CYSB3YX")//锅炉3运行状态
                                .or().like("attribute_code", "RSSDXS"))//除氧水泵运行
                        .orderByAsc("device_id", "sort"));

        // 按 deviceId 分组
        Map<Long, List<DeviceAttribute>> attrMap = attributes.stream()
                .collect(Collectors.groupingBy(DeviceAttribute::getDeviceId));

        // 3. 组装结果
        for (Device device : devices) {
            List<DeviceAttribute> deviceAttributes = attrMap.get(device.getId());
            if (CollectionUtils.isEmpty(deviceAttributes)) {
                continue;
            }
            for (DeviceAttribute attr : deviceAttributes) {
                DeviceStatusVO deviceVO = new DeviceStatusVO();
                String deviceName = device.getDeviceName();
                String deviceCode = device.getDeviceCode();
                if (device.getDeviceName().contains("锅炉辅机")) {
                    deviceName = device.getDeviceName() + "-" + attr.getAttributeName().replaceAll("运行", "");
                }
                deviceVO.setDeviceName(deviceName);
                deviceVO.setDeviceCode(deviceCode);

                DeviceAttributeStatusVO attribute = new DeviceAttributeStatusVO();
                attribute.setAttributeCode(attr.getAttributeCode());
                attribute.setAttributeName(attr.getAttributeName());
                attribute.setValue(attr.getValue());
                deviceVO.setAttribute(attribute);

                devicesList.add(deviceVO);
            }
        }

        result.setTotal(devicesList.size());
        result.setDevices(devicesList);
        return result;
    }

    @Override
    public AlarmListVO alarmList() {
        // 1. 简单告警列表
        List<Map<String, Object>> list = alarmRecordMapper.selectSimpleAlarmList();
        List<AlarmRecordSimpleVO> records = list.stream()
                .map(row -> new AlarmRecordSimpleVO(
                        toStr(row.get("deviceName")),
                        toLocalDateTime(row.get("alarmTime")),
                        toStr(row.get("alarmCategoryName"))))
                .collect(Collectors.toList());

        // 2. 当日告警按告警类型统计
        List<Map<String, Object>> pieData = alarmRecordMapper.selectAlarmCountByCategory();
        List<AlarmCategoryCountVO> pie = pieData.stream()
                .map(row -> new AlarmCategoryCountVO(
                        toStr(row.get("name")),
                        toLong(row.get("value"))))
                .collect(Collectors.toList());

        return new AlarmListVO(records, records.size(), pie);
    }

    @Override
    public List<SteamElectricityProductionVO> steamElectricityProduction() {
        // 当日时间范围
        LocalDateTime[] localDateTimes = DateRangeUtils.currentDay();
        List<ProductionItemVO> steamProductionData = getSteamProductionData(localDateTimes);
        List<ProductionItemVO> electricityProductionData = getElectricityProductionData(localDateTimes);

        List<SteamElectricityProductionVO> result = new ArrayList<>();
        result.add(new SteamElectricityProductionVO("蒸汽产量", steamProductionData));
        result.add(new SteamElectricityProductionVO("电能产量", electricityProductionData));
        return result;
    }


    private List<ProductionItemVO> getElectricityProductionData(LocalDateTime[] localDateTimes) {
        // 表1-组合有功总电能 表2-组合有功总电能
        // 同一个device 有两个电表 分别是表1，表2
        String deviceAttributeKey = "组合有功总电能";
        List<DeviceAttribute> deviceAttributes = deviceAttributeMapper.selectList(new QueryWrapper<DeviceAttribute>().like("attribute_name", deviceAttributeKey));
        // 根据id和名称构建Map
        Map<Long, String> deviceAttributeNameMap = deviceAttributes.stream().collect(Collectors.toMap(DeviceAttribute::getId, DeviceAttribute::getAttributeName));
        // 获取属性id
        List<Long> deviceAttributeIds = deviceAttributes.stream().map(DeviceAttribute::getId).collect(Collectors.toList());
        // 获取设备id
        List<Long> deviceIds = deviceAttributes.stream().map(DeviceAttribute::getDeviceId).distinct().collect(Collectors.toList());
        // 根据设备id获取设备名称
        List<Device> devices = deviceMapper.selectList(new QueryWrapper<Device>().in("id", deviceIds));
        // 根据id和名称构建Map
        Map<Long, String> deviceNameMap = devices.stream().collect(Collectors.toMap(Device::getId, Device::getDeviceName));
        // 查询当天历史数据
        List<DeviceAttributeHistory> deviceAttributeHistories = deviceAttributeHistoryMapper.selectList(new QueryWrapper<DeviceAttributeHistory>()
                .in("device_id", deviceIds)
                .in("attribute_id", deviceAttributeIds)
                .ge("collection_time", localDateTimes[0])
                .le("collection_time", localDateTimes[1]));
        //根据设备+属性进行分组
        Map<String, List<DeviceAttributeHistory>> deviceAttributeHistoryMap = deviceAttributeHistories.stream()
                .collect(Collectors.groupingBy(deviceAttributeHistory -> deviceAttributeHistory.getDeviceId() + "_" + deviceAttributeHistory.getAttributeId()));
        //进行循环，且根据时间进行从大到小的排序
        deviceAttributeHistoryMap.forEach((key, value) -> {
            value.sort(Comparator.comparing(DeviceAttributeHistory::getCollectionTime).reversed());
        });
        List<ProductionItemVO> result = new ArrayList<>();
        //遍历获取最大的和最小的时间的数据
        deviceAttributeHistoryMap.forEach((key, value) -> {
            if (value.size() > 1) {
                String[] parts = key.split("_");
                String deviceId = parts[0];
                String deviceName = deviceNameMap.get(toLong(deviceId));
                String attributeId = parts[1];
                String attributeName = deviceAttributeNameMap.get(toLong(attributeId));
                attributeName = attributeName.replaceAll(deviceAttributeKey, "");
                DeviceAttributeHistory first = value.get(0);
                DeviceAttributeHistory last = value.get(value.size() - 1);
                String max_value = first.getValue();
                String mmin_value = last.getValue();
                // 计算蒸汽产量
                BigDecimal steamProduction = new BigDecimal(max_value).subtract(new BigDecimal(mmin_value));

                result.add(new ProductionItemVO(
                        deviceName + "-" + attributeName,
                        max_value,
                        mmin_value,
                        steamProduction));
            }
        });
        if (result.isEmpty()) {
            result.add(new ProductionItemVO("无数据", "0", "0", BigDecimal.ZERO));
        }
        return result;
    }

    private List<ProductionItemVO> getSteamProductionData(LocalDateTime[] localDateTimes) {
        List<DeviceAttribute> deviceAttributes = deviceAttributeMapper.selectList(new QueryWrapper<DeviceAttribute>().eq("attribute_name", "蒸汽累计流量"));
        // 获取属性id
        List<Long> deviceAttributeIds = deviceAttributes.stream().map(DeviceAttribute::getId).collect(Collectors.toList());
        // 获取设备id
        List<Long> deviceIds = deviceAttributes.stream().map(DeviceAttribute::getDeviceId).distinct().collect(Collectors.toList());
        // 根据设备id获取设备名称
        List<Device> devices = deviceMapper.selectList(new QueryWrapper<Device>().in("id", deviceIds));
        // 根据id和名称构建Map
        Map<Long, String> deviceNameMap = devices.stream().collect(Collectors.toMap(Device::getId, Device::getDeviceName));
        // 查询当天历史数据
        List<DeviceAttributeHistory> deviceAttributeHistories = deviceAttributeHistoryMapper.selectList(new QueryWrapper<DeviceAttributeHistory>()
                .in("device_id", deviceIds)
                .in("attribute_id", deviceAttributeIds)
                .ge("collection_time", localDateTimes[0])
                .le("collection_time", localDateTimes[1]));
        //根据设备+属性进行分组
        Map<String, List<DeviceAttributeHistory>> deviceAttributeHistoryMap = deviceAttributeHistories.stream()
                .collect(Collectors.groupingBy(deviceAttributeHistory -> deviceAttributeHistory.getDeviceId() + "_" + deviceAttributeHistory.getAttributeId()));
        //进行循环，且根据时间进行从大到小的排序
        deviceAttributeHistoryMap.forEach((key, value) -> {
            value.sort(Comparator.comparing(DeviceAttributeHistory::getCollectionTime).reversed());
        });
        List<ProductionItemVO> result = new ArrayList<>();
        //遍历获取最大的和最小的时间的数据
        deviceAttributeHistoryMap.forEach((key, value) -> {
            if (value.size() > 1) {
                String[] parts = key.split("_");
                String deviceId = parts[0];
                String deviceName = deviceNameMap.get(toLong(deviceId));
                String attributeId = parts[1];
                DeviceAttributeHistory first = value.get(0);
                DeviceAttributeHistory last = value.get(value.size() - 1);
                String max_value = first.getValue();
                String mmin_value = last.getValue();
                // 计算蒸汽产量
                BigDecimal steamProduction = new BigDecimal(max_value).subtract(new BigDecimal(mmin_value));
                result.add(new ProductionItemVO(
                        deviceName,
                        max_value,
                        mmin_value,
                        steamProduction));
            }
        });
        if (result.isEmpty()) {
            result.add(new ProductionItemVO("无数据", "0", "0", BigDecimal.ZERO));
        }
        return result;
    }


    @Override
    public CarbonFootprintVO carbonFootprint() {
        // ==================== 换算系数 ====================
        // 单棵树年固碳量 (kg CO2/棵/年)
        final BigDecimal TREE_ANNUAL_CO2_KG = new BigDecimal("18.3");
        // 树木寿命 (年)
        final BigDecimal TREE_LIFESPAN_YEARS = new BigDecimal("40");
        // 绿电转换系数：1 tCO2e 对应绿电量 (MWh)
        final BigDecimal GREEN_ELEC_MWH_PER_TCO2 = new BigDecimal("1.25");
        // 绿植固碳系数：1 tCO2e 对应地上生物量 (吨)
        final BigDecimal BIOMASS_TON_PER_TCO2 = new BigDecimal("2.0");
        // 再利用能源减排系数（按需调整，默认 1.0）
        final BigDecimal REUSE_ENERGY_FACTOR = BigDecimal.ONE;

        // ==================== 碳排放总量 ====================
        BigDecimal totalCarbonEmissions = getTotalCarbonEmissions();
        if (totalCarbonEmissions == null || totalCarbonEmissions.compareTo(BigDecimal.ZERO) < 0) {
            totalCarbonEmissions = BigDecimal.ZERO;
        }

        // ==================== 1. 等效植树林（棵） ====================
        // 总碳排(kg) / (单树年固碳 × 寿命)
        BigDecimal totalCarbonKg = totalCarbonEmissions.multiply(new BigDecimal("1000"));
        BigDecimal treeFactor = TREE_ANNUAL_CO2_KG.multiply(TREE_LIFESPAN_YEARS);
        BigDecimal equivalentPlantations = totalCarbonKg.divide(treeFactor, 2, RoundingMode.HALF_UP);

        // ==================== 2. 再利用能源减排量（tCO2e） ====================
        BigDecimal reuseEnergySavings = totalCarbonEmissions
                .multiply(REUSE_ENERGY_FACTOR)
                .setScale(2, RoundingMode.HALF_UP);

        // ==================== 3. 绿电减排（MWh） ====================
        BigDecimal greenPowerEmissionReduction = totalCarbonEmissions
                .multiply(GREEN_ELEC_MWH_PER_TCO2)
                .setScale(2, RoundingMode.HALF_UP);

        // ==================== 4. 绿植固碳（吨地上生物量） ====================
        BigDecimal greenPlantsSequestration = totalCarbonEmissions
                .multiply(BIOMASS_TON_PER_TCO2)
                .setScale(2, RoundingMode.HALF_UP);

        return new CarbonFootprintVO(
                totalCarbonEmissions,
                equivalentPlantations,
                reuseEnergySavings,
                greenPowerEmissionReduction,
                greenPlantsSequestration);
    }

    @Override
    public EnergySupplyOverviewVO energySupplyOverview() {
        BigDecimal externalSteamSupplyVolume = getExternalSteamSupplyVolume();
        BigDecimal convertHeatEnergy = calcMWFromTon(externalSteamSupplyVolume);
        BigDecimal wasteHeatHotWater = getWasteHeatHotWater();
        BigDecimal photovoltaicPowerGeneration = getPhotovoltaicPowerGeneration();
        return new EnergySupplyOverviewVO(
                externalSteamSupplyVolume,
                convertHeatEnergy,
                wasteHeatHotWater,
                photovoltaicPowerGeneration);
    }
    private LocalDateTime[] resolveRange(String normalized) {
        return switch (normalized) {
            case DateRangeUtils.PERIOD_WEEK  -> DateRangeUtils.currentWeek();
            case DateRangeUtils.PERIOD_MONTH-> DateRangeUtils.currentMonth();
            case DateRangeUtils.PERIOD_YEAR  -> DateRangeUtils.currentYear();
            default -> throw new IllegalArgumentException("period 非法: " + normalized);
        };
    }
    /**
     * 按“天”聚合，返回区间内每一天的值，无数据补 0
     *
     * @param attrName    属性名（日发电量 / 直流侧累计发电量）
     * @param start       起始时间
     * @param end         结束时间
     * @param normalized  WEEK / MONTH / YEAR
     */
    private List<PowerTrendVO> getDailyTrend(String attrName,
                                             LocalDateTime start,
                                             LocalDateTime end,
                                             String normalized) {
        // 1. 按天聚合查询
        List<PowerTrendVO> daily = deviceAttributeHistoryMapper.selectPowerTrend(
                attrName, start, end, "%Y-%m-%d");

        // 2. 天 -> 值
        Map<String, BigDecimal> dayMap = daily == null ? Collections.emptyMap()
                : daily.stream().collect(Collectors.toMap(
                PowerTrendVO::getBucketTime,
                v -> v.getTotalValue() == null ? BigDecimal.ZERO : v.getTotalValue(),
                (a, b) -> a));

        // 3. 按周期生成连续日期并补 0
        List<PowerTrendVO> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate cursor = start.toLocalDate();
        LocalDate last = end.toLocalDate();

        while (!cursor.isAfter(last)) {
            String key = cursor.format(fmt);
            result.add(new PowerTrendVO(key, dayMap.getOrDefault(key, BigDecimal.ZERO)));
            cursor = cursor.plusDays(1);
        }
        return result;
    }
    private ChartDataVO buildChartData(List<PowerTrendVO> list) {
        return new ChartDataVO(
                list.stream().map(PowerTrendVO::getBucketTime).collect(Collectors.toList()),
                list.stream().map(PowerTrendVO::getTotalValue).collect(Collectors.toList()));
    }
    public ProductionOverviewVO productionOverview(String period) {
        //风电、光伏两种类型设备产能折线图；X轴为时间，Y轴为发电量,折线图 (本周、本月、本年) 统计
        //日发电量 —— 统计当日光伏产电
        //累计发电量 —— 统计历史总光伏产电
        //对应设备：
        //锅炉房屋顶北1、北2、南1、南2、南3
        //调度中心屋顶2
        //水处理间1
        //注意：调度中心屋顶1 只有“日发电量”，没有“累计发电量”。

        //方案使用 日发电量 直流侧累计发电量 进行统计计算
        // 光伏：日发电量
        // 风电：直流侧累计发电量
        String pvAttrName = "日发电量";
        String windAttrName = "直流侧累计发电量";

        // 归一化入参：兼容 中文 / 英文 / 大小写
        String normalized = DateRangeUtils.normalizePeriod(period);

        // 计算该周期的起止时间
        LocalDateTime[] range = resolveRange(normalized);

        // 直接按“天”聚合，本周就是 7 天，本月就是当月每天，本年就是当年每天
        List<PowerTrendVO> pvList = getDailyTrend(pvAttrName, range[0], range[1], normalized);
        List<PowerTrendVO> windList = getDailyTrend(windAttrName, range[0], range[1], normalized);

        return new ProductionOverviewVO(normalized, buildChartData(pvList), buildChartData(windList));
    }

    /**
     * 中国习惯周划分：
     * 一周 = 周一 ~ 周日
     * 第 1 周 = 1/1 所在周（整周不拆，周一可能在去年）
     * 最后一周 = 12/31 所在周（整周不拆，周日可能在明年）
     * 跨年周整周归属 1/1 所在年
     *
     * @return 每周 [周一日期, 周日日期] 列表
     */
    private List<LocalDate[]> buildChinaWeekRanges(int year) {
        List<LocalDate[]> ranges = new ArrayList<>();

        LocalDate firstDay = LocalDate.of(year, 1, 1);
        LocalDate lastDay = LocalDate.of(year, 12, 31);

        // 第 1 周的周一：1/1 所在周的周一（可能去年）
        LocalDate cursor = firstDay.with(DayOfWeek.MONDAY);

        // 最后一周的周一：12/31 所在周的周一
        LocalDate lastWeekMonday = lastDay.with(DayOfWeek.MONDAY);

        while (!cursor.isAfter(lastWeekMonday)) {
            LocalDate weekSunday = cursor.plusDays(6); // 周日
            ranges.add(new LocalDate[]{cursor, weekSunday});
            cursor = cursor.plusWeeks(1);
        }
        return ranges;
    }

    private BigDecimal getExternalSteamSupplyVolume() {
        //锅炉蒸汽流量 ->统计出总量 ->外供蒸汽量
        BigDecimal total = deviceAttributeHistoryMapper.sumLatestValueByAttributeName("锅炉蒸汽流量");
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 根据外供蒸汽量（吨）计算热能
     *
     * @param flowTonPerHour 蒸汽流量 t/h
     * @return 热能 MW
     */
    public BigDecimal calcMWFromTon(BigDecimal flowTonPerHour) {
        return flowTonPerHour.multiply(new BigDecimal(0.7)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getWasteHeatHotWater() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getPhotovoltaicPowerGeneration() {
        //光伏产电
        //各光伏设备的“日发电量” —— 统计当日光伏产电
        //各光伏设备的“累计发电量” —— 统计历史总光伏产电
        //各光伏设备的“有功功率” —— 实时功率，可积分算电量
        //对应光伏设备为：
        //锅炉房屋顶北1、北2、南1、南2、南3
        //调度中心屋顶1、2
        //水处理间1
        //其中 调度中心屋顶1、水处理间1 没有累计发电量，只有日发电量和有功功率，

        //当前方案 使用 累计发电量
        BigDecimal total = deviceAttributeHistoryMapper.sumLatestValueByAttributeName("累计发电量");
        return total.setScale(2, RoundingMode.HALF_UP);
    }


    private BigDecimal getGreenPlantsSequestration() {
        return BigDecimal.ZERO;
    }

    private BigDecimal getGreenPowerEmissionReduction() {
        return BigDecimal.ZERO;
    }

    private BigDecimal getReuseEnergySavings() {
        return BigDecimal.ZERO;
    }

    private BigDecimal getEquivalentPlantations() {
        return BigDecimal.ZERO;
    }

    @Autowired
    private IBusinessConfigService businessConfigService;
    @Autowired
    private ICarbonEmissionFactorService carbonEmissionFactorService;
    @Autowired
    private IMeteringPointDataMonthService meteringPointDataMonthService;
    @Autowired
    private IMeteringPointDataDayService meteringPointDataDayService;

    private BigDecimal getTotalCarbonEmissions() {
        // 获取计量规则点位
        List<Long> pointIds = businessConfigService.getListByKey(BusinessConfigConstant.CARBON_EMISSION_POINT, Long.class);
        // 获取碳排系数
        BigDecimal coefficient = carbonEmissionFactorService.getElectricityCarbonEmissionFactor();

        // 获取所有能耗
        LocalDateTime startTime = LocalDateTime.of(2025, 1, 1, 0, 0, 0);
        List<MeteringPointDataMonth> byTimeRangeAndPointIds = meteringPointDataMonthService.findByTimeRangeAndPointIds(startTime, LocalDateTime.now(), pointIds);
        BigDecimal monthConsumption = byTimeRangeAndPointIds
                .stream()
                .map(MeteringPointDataMonth::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 碳排放总量
        BigDecimal totalCarbonEmissions = monthConsumption.multiply(coefficient).setScale(2, RoundingMode.HALF_UP);
        return totalCarbonEmissions;
    }

    /**
     * 安全转 Long
     */
    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全转 Double
     */
    private static double toDouble(Object o) {
        if (o == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 安全转 String
     */
    private static String toStr(Object o) {
        return o == null ? null : o.toString();
    }

    /**
     * 安全转 LocalDateTime
     */
    private static LocalDateTime toLocalDateTime(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalDateTime) {
            return (LocalDateTime) o;
        }
        if (o instanceof java.util.Date) {
            return ((java.util.Date) o).toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        }
        return null;
    }

    /**
     * 属性元信息
     */
    private static class AttrMeta {
        final Long deviceId;
        final String deviceName;
        final String itemName;

        AttrMeta(Long deviceId, String deviceName, String itemName) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.itemName = itemName;
        }
    }

    // ---------- 时间区间工具 ----------

    /**
     * 当日区间（00:00:00 ~ 23:59:59.999999999）
     */
    public static LocalDateTime[] currentDay() {
        LocalDate today = LocalDate.now();
        return new LocalDateTime[]{
                LocalDateTime.of(today, DateRangeUtils.START_OF_DAY),
                LocalDateTime.of(today, DateRangeUtils.END_OF_DAY)
        };
    }

    /**
     * 本周区间（周一 00:00:00 ~ 周日 23:59:59.999999999）
     * 以 ISO 标准，周一为一周的第一天
     */
    public static LocalDateTime[] currentWeek() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return new LocalDateTime[]{
                LocalDateTime.of(monday, DateRangeUtils.START_OF_DAY),
                LocalDateTime.of(sunday, DateRangeUtils.END_OF_DAY)
        };
    }

    /**
     * 本月区间（1 号 00:00:00 ~ 月末 23:59:59.999999999）
     */
    public static LocalDateTime[] currentMonth() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate lastDay = today.with(TemporalAdjusters.lastDayOfMonth());
        return new LocalDateTime[]{
                LocalDateTime.of(firstDay, DateRangeUtils.START_OF_DAY),
                LocalDateTime.of(lastDay, DateRangeUtils.END_OF_DAY)
        };
    }

    /**
     * 本年区间（1 月 1 日 00:00:00 ~ 12 月 31 日 23:59:59.999999999）
     */
    public static LocalDateTime[] currentYear() {
        LocalDate today = LocalDate.now();
        LocalDate firstDay = today.with(TemporalAdjusters.firstDayOfYear());
        LocalDate lastDay = today.with(TemporalAdjusters.lastDayOfYear());
        return new LocalDateTime[]{
                LocalDateTime.of(firstDay, DateRangeUtils.START_OF_DAY),
                LocalDateTime.of(lastDay, DateRangeUtils.END_OF_DAY)
        };
    }
}
