package org.jeecg.modules.bems.visualization.ahgl.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.jeecg.modules.bems.alarm.entity.AlarmCategory;
import org.jeecg.modules.bems.alarm.entity.AlarmRecord;
import org.jeecg.modules.bems.alarm.mapper.AlarmCategoryMapper;
import org.jeecg.modules.bems.alarm.mapper.AlarmRecordMapper;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.entity.DeviceAttributeHistory;
import org.jeecg.modules.bems.mdm.mapper.DeviceAttributeHistoryMapper;
import org.jeecg.modules.bems.mdm.mapper.DeviceAttributeMapper;
import org.jeecg.modules.bems.mdm.mapper.DeviceMapper;
import org.jeecg.modules.bems.visualization.ahgl.service.EhsManagementService;
import org.jeecg.modules.bems.visualization.ahgl.vo.AlarmCountVO;
import org.jeecg.modules.bems.visualization.ahgl.vo.AlarmsByTypeNumberVO;
import org.jeecg.modules.bems.visualization.ahgl.vo.DeviceDataVO;
import org.jeecg.modules.bems.visualization.ahgl.vo.DividedIntoSixVO;
import org.jeecg.modules.bems.visualization.ahgl.vo.TimeValueVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EhsManagementServiceImpl implements EhsManagementService {
    @Autowired
    private DeviceMapper deviceMapper;
    @Autowired
    private DeviceAttributeMapper deviceAttributeMapper;
    @Autowired
    private DeviceAttributeHistoryMapper deviceAttributeHistoryMapper;
    @Autowired
    private AlarmRecordMapper alarmRecordMapper;
    @Autowired
    private AlarmCategoryMapper alarmCategoryMapper;


    /**
     * 一天的最早时间 00:00:00.000000000
     */
    private static final LocalTime START_OF_DAY = LocalTime.MIN;
    /**
     * 一天的最晚时间 23:59:59.999999999
     */
    private static final LocalTime END_OF_DAY = LocalTime.MAX;

    /**
     * CEMS 设备编码关键字
     */
    private static final String CEMS_DEVICE_CODE_KEYWORD = "CEMS";

    /**
     * 设备原始名 -> 展示名
     */
    private static final Map<String, String> DEVICE_DISPLAY_NAME = new LinkedHashMap<String, String>() {{
        put("1#CEMS", "CEMS1");
        put("2#CEMS", "CEMS2");
        put("3#CEMS", "CEMS3");
    }};

    /**
     * 监测项原始名 -> 展示名
     */
    private static final Map<String, String> ITEM_DISPLAY_NAME = new LinkedHashMap<String, String>() {{
        put("CO2", "二氧化碳浓度");
        put("粉尘浓度", "粉尘浓度");
    }};

    /**
     * 周期顺序
     */
    private static final String PERIOD_WEEK = "本周";
    private static final String PERIOD_MONTH = "本月";
    private static final String PERIOD_YEAR = "本年";
    private static final String[] PERIODS = {PERIOD_WEEK, PERIOD_MONTH, PERIOD_YEAR};

    @Override
    public List<DividedIntoSixVO> dividedIntoSix(LocalDate startDate, LocalDate endDate, boolean byDay) {
        List<DividedIntoSixVO> result = new ArrayList<>();

        // 1. 查询 CEMS 设备
        List<Device> devices = queryCemsDevices();
        if (devices.isEmpty()) {
            return result;
        }
        List<Long> deviceIds = devices.stream()
                .map(Device::getId)
                .distinct()
                .collect(Collectors.toList());

        // 2. 构建属性元信息索引 attributeId -> AttrMeta
        Map<Long, AttrMeta> attrMetaMap = buildAttrMetaMap(deviceIds);
        if (attrMetaMap.isEmpty()) {
            return result;
        }
        List<Long> attrIds = new ArrayList<>(attrMetaMap.keySet());

        // 3. 查询历史数据
        List<DeviceAttributeHistory> histories = queryHistories(deviceIds, attrIds, startDate, endDate);
        if (histories.isEmpty()) {
            return result;
        }

        // 4. 按 设备 + 属性 分组
        Map<Long, Map<Long, List<DeviceAttributeHistory>>> byDeviceThenAttr =
                groupByDeviceThenAttr(histories);

        // 5. 组装中间结构：itemName -> (deviceName -> dataList)
        Map<String, Map<String, List<TimeValueVO>>> itemToDeviceData =
                buildItemToDeviceData(byDeviceThenAttr, attrMetaMap, byDay);

        // 6. 拼装最终返回结构
        return assembleResult(itemToDeviceData);
    }


    /**
     * 查询所有 CEMS 设备
     */
    private List<Device> queryCemsDevices() {
        return deviceMapper.selectList(
                new QueryWrapper<Device>().like("device_code", CEMS_DEVICE_CODE_KEYWORD));
    }

    /**
     * 查询指定设备、属性在时间范围内的历史数据
     */
    private List<DeviceAttributeHistory> queryHistories(List<Long> deviceIds,
                                                        List<Long> attrIds,
                                                        LocalDate startDate,
                                                        LocalDate endDate) {
        return deviceAttributeHistoryMapper.selectList(
                new QueryWrapper<DeviceAttributeHistory>()
                        .in("device_id", deviceIds)
                        .in("attribute_id", attrIds)
                        .ge("collection_time", LocalDateTime.of(startDate, START_OF_DAY))
                        .le("collection_time", LocalDateTime.of(endDate, END_OF_DAY)));
    }

    /**
     * 按 设备 -> 属性 二级分组
     */
    private Map<Long, Map<Long, List<DeviceAttributeHistory>>> groupByDeviceThenAttr(
            List<DeviceAttributeHistory> histories) {
        return histories.stream()
                .collect(Collectors.groupingBy(
                        DeviceAttributeHistory::getDeviceId,
                        Collectors.groupingBy(DeviceAttributeHistory::getAttributeId)));
    }


    /**
     * 查询 CEMS 设备属性，并建立 attributeId -> AttrMeta 索引。
     * 仅保留属性名同时包含"设备关键字"和"监测项关键字"的记录。
     */
    private Map<Long, AttrMeta> buildAttrMetaMap(List<Long> deviceIds) {
        QueryWrapper<DeviceAttribute> wrapper = buildAttrQueryWrapper(deviceIds);
        List<DeviceAttribute> deviceAttributes = deviceAttributeMapper.selectList(wrapper);

        Map<Long, AttrMeta> attrMetaMap = new HashMap<>();
        for (DeviceAttribute attr : deviceAttributes) {
            String attrName = attr.getAttributeName();
            if (attrName == null) {
                continue;
            }
            String devKey = matchKey(attrName, DEVICE_DISPLAY_NAME.keySet());
            String itemKey = matchKey(attrName, ITEM_DISPLAY_NAME.keySet());
            if (devKey == null || itemKey == null) {
                continue;
            }
            attrMetaMap.put(attr.getId(), new AttrMeta(
                    DEVICE_DISPLAY_NAME.get(devKey),
                    ITEM_DISPLAY_NAME.get(itemKey)));
        }
        return attrMetaMap;
    }

    /**
     * 构造属性查询条件：device_id in (...) AND ( (name like dev & item) OR ... )
     */
    private QueryWrapper<DeviceAttribute> buildAttrQueryWrapper(List<Long> deviceIds) {
        return new QueryWrapper<DeviceAttribute>()
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
    }

    /**
     * 从候选 key 集合中找出第一个被 attrName 包含的 key，找不到返回 null
     */
    private static String matchKey(String attrName, Iterable<String> candidates) {
        for (String key : candidates) {
            if (attrName.contains(key)) {
                return key;
            }
        }
        return null;
    }


    /**
     * 生成 itemName -> (deviceName -> dataList) 中间结构。
     * dataList 内部元素形如 TimeValueVO。
     */
    private Map<String, Map<String, List<TimeValueVO>>> buildItemToDeviceData(
            Map<Long, Map<Long, List<DeviceAttributeHistory>>> byDeviceThenAttr,
            Map<Long, AttrMeta> attrMetaMap,
            boolean byDay) {

        Map<String, Map<String, List<TimeValueVO>>> itemToDeviceData = new LinkedHashMap<>();

        for (Map.Entry<Long, Map<Long, List<DeviceAttributeHistory>>> deviceEntry : byDeviceThenAttr.entrySet()) {
            Map<Long, List<DeviceAttributeHistory>> byAttr = deviceEntry.getValue();

            for (Map.Entry<Long, List<DeviceAttributeHistory>> attrEntry : byAttr.entrySet()) {
                AttrMeta meta = attrMetaMap.get(attrEntry.getKey());
                if (meta == null) {
                    continue;
                }

                List<TimeValueVO> dataList = byDay
                        ? buildDailyAverageList(attrEntry.getValue())
                        : buildRawList(attrEntry.getValue());

                itemToDeviceData
                        .computeIfAbsent(meta.itemName, k -> new LinkedHashMap<>())
                        .put(meta.deviceName, dataList);
            }
        }
        return itemToDeviceData;
    }

    /**
     * 按天求平均：每个自然日一条 {collectionTime=当天0点, value=平均值}
     */
    private List<TimeValueVO> buildDailyAverageList(List<DeviceAttributeHistory> list) {
        Map<LocalDate, List<DeviceAttributeHistory>> byDayMap = list.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getCollectionTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()));

        List<TimeValueVO> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<DeviceAttributeHistory>> dayEntry : byDayMap.entrySet()) {
            LocalDate day = dayEntry.getKey();
            Double avg = averageValue(dayEntry.getValue());
            if (avg == null) {
                continue;
            }
            result.add(new TimeValueVO(
                    day.atStartOfDay(),
                    BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP).doubleValue()));
        }
        return result;
    }

    /**
     * 保留原始记录：按采集时间升序，每条 {collectionTime, value}
     */
    private List<TimeValueVO> buildRawList(List<DeviceAttributeHistory> list) {
        return list.stream()
                .sorted(Comparator.comparing(DeviceAttributeHistory::getCollectionTime))
                .map(h -> new TimeValueVO(h.getCollectionTime(), h.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 计算一组历史记录的平均值，全部非法/为空时返回 null
     */
    private static Double averageValue(List<DeviceAttributeHistory> list) {
        double sum = 0;
        int count = 0;
        for (DeviceAttributeHistory h : list) {
            if (h.getValue() == null) {
                continue;
            }
            try {
                sum += Double.parseDouble(h.getValue());
                count++;
            } catch (NumberFormatException ignored) {
                // 忽略非数值记录
            }
        }
        return count == 0 ? null : sum / count;
    }

    /**
     * 拼装最终返回：每个 itemName 一条，data 为设备列表
     */
    private List<DividedIntoSixVO> assembleResult(Map<String, Map<String, List<TimeValueVO>>> itemToDeviceData) {
        List<DividedIntoSixVO> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<TimeValueVO>>> itemEntry : itemToDeviceData.entrySet()) {
            List<DeviceDataVO> deviceList = new ArrayList<>();
            for (Map.Entry<String, List<TimeValueVO>> deviceEntry : itemEntry.getValue().entrySet()) {
                deviceList.add(new DeviceDataVO(deviceEntry.getKey(), deviceEntry.getValue()));
            }
            result.add(new DividedIntoSixVO(itemEntry.getKey(), deviceList));
        }
        return result;
    }

    @Override
    public List<AlarmsByTypeNumberVO> alarmsByTypeNumber() {
        LocalDateTime[] week = currentWeek();
        LocalDateTime[] month = currentMonth();
        LocalDateTime[] year = currentYear();

        List<AlarmsByTypeNumberVO> result = new ArrayList<>();
        result.add(new AlarmsByTypeNumberVO(PERIOD_WEEK, getAlarmRecordCount(week)));
        result.add(new AlarmsByTypeNumberVO(PERIOD_MONTH, getAlarmRecordCount(month)));
        result.add(new AlarmsByTypeNumberVO(PERIOD_YEAR, getAlarmRecordCount(year)));
        return result;
    }

    private List<AlarmCountVO> getAlarmRecordCount(LocalDateTime[] dateTimes) {
        List<Map<String, Object>> rows = alarmRecordMapper.selectMaps(
                new QueryWrapper<AlarmRecord>()
                        .select("alarm_category_id", "count(1) AS count")
                        .ge("alarm_time", dateTimes[0])
                        .le("alarm_time", dateTimes[1])
                        .groupBy("alarm_category_id")
        );

        List<AlarmCountVO> result = new ArrayList<>();
        if (rows.isEmpty()) {
            return result;
        }

        // 1. 先收集所有告警类别 id
        List<Long> alarmCategoryIds = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long alarmCategoryId = toLong(row.get("alarm_category_id"));
            if (alarmCategoryId != null) {
                alarmCategoryIds.add(alarmCategoryId);
            }
        }

        // 2. 一次性查询告警类别，构建 id -> name 映射
        Map<Long, String> categoryNameMap = new HashMap<>();
        if (!alarmCategoryIds.isEmpty()) {
            List<AlarmCategory> alarmCategorys = alarmCategoryMapper.selectList(
                    new QueryWrapper<AlarmCategory>().in("id", alarmCategoryIds));
            for (AlarmCategory category : alarmCategorys) {
                categoryNameMap.put(category.getId(), category.getAlarmCategoryName());
            }
        }

        // 3. 组装结果，回填 alarmCategoryName
        for (Map<String, Object> row : rows) {
            Long alarmCategoryId = toLong(row.get("alarm_category_id"));
            result.add(new AlarmCountVO(
                    alarmCategoryId,
                    categoryNameMap.get(alarmCategoryId),
                    toLong(row.get("count"))));
        }

        return result;
    }


    /**
     * 属性元信息：展示用的设备名 + 监测项名
     */
    private static class AttrMeta {
        final String deviceName;
        final String itemName;

        AttrMeta(String deviceName, String itemName) {
            this.deviceName = deviceName;
            this.itemName = itemName;
        }
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

    // ---------- 时间区间工具 ----------

    /**
     * 本周区间（周一 00:00:00 ~ 周日 23:59:59.999999999）
     * 以 ISO 标准，周一为一周的第一天
     */
    public static LocalDateTime[] currentWeek() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return new LocalDateTime[]{
                LocalDateTime.of(monday, START_OF_DAY),
                LocalDateTime.of(sunday, END_OF_DAY)
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
                LocalDateTime.of(firstDay, START_OF_DAY),
                LocalDateTime.of(lastDay, END_OF_DAY)
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
                LocalDateTime.of(firstDay, START_OF_DAY),
                LocalDateTime.of(lastDay, END_OF_DAY)
        };
    }
}
