package org.jeecg.modules.bems.echarts.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import org.jeecg.modules.bems.echarts.dto.ReturnAirCo2TrendQueryDto;
import org.jeecg.modules.bems.echarts.service.IReturnAirCo2TrendService;
import org.jeecg.modules.bems.echarts.vo.ReturnAirCo2TrendVo;
import org.jeecg.modules.bems.mdm.dto.DeviceAttributeHistoryQueryDto;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.entity.DeviceAttributeHistory;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeHistoryService;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备属性趋势图服务实现
 *
 * @author sgai-fwbz
 */
@Slf4j
@Service
@AllArgsConstructor
public class ReturnAirCo2TrendServiceImpl implements IReturnAirCo2TrendService {

    /** 时间桶标签格式：小时 -> "HH:00"，15分钟 -> "HH:mm"，天 -> "MM-dd" */
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("HH:00");
    private static final DateTimeFormatter MIN_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final IDeviceAttributeService deviceAttributeService;
    private final IDeviceAttributeHistoryService deviceAttributeHistoryService;
    private final IDeviceService deviceService;


    /** 电表总有功功率测点含义关键字 */
    private static final String ACTIVE_POWER_DESC = "总有功功率";

    @Override
    public ReturnAirCo2TrendVo getReturnAirCo2Trend(ReturnAirCo2TrendQueryDto query) {
        // 0. 入参校验 & 默认值
        if (query == null || CollectionUtil.isEmpty(query.getDeviceIds())) {
            return emptyResult(query);
        }
        if (StringUtils.isBlank(query.getAttributeName())) {
            query.setAttributeName("回风二氧化碳");
        }
        if (StringUtils.isBlank(query.getGranularity())) {
            query.setGranularity("hour");
        }
        normalizeTimeRange(query);

        // 1. 按 deviceId + attributeName 定位属性
        List<DeviceAttribute> attributes = deviceAttributeService.list(
                new LambdaQueryWrapper<DeviceAttribute>()
                        .in(DeviceAttribute::getDeviceId, query.getDeviceIds())
                        .eq(DeviceAttribute::getAttributeName, query.getAttributeName())
        );
        if (CollectionUtil.isEmpty(attributes)) {
            log.warn("[属性趋势] 未在 device_attribute 找到匹配属性：deviceIds={}, attributeName={}",
                    query.getDeviceIds(), query.getAttributeName());
            return emptyResult(query);
        }

        // 2. 取设备信息（图例名取设备名/编号）
        List<Device> devices = deviceService.findByDeviceIds(query.getDeviceIds());
        Map<Long, Device> deviceMap = devices.stream()
                .collect(Collectors.toMap(Device::getId, Function.identity(), (k1, k2) -> k2));

        // 3. 按属性ID批量查历史
        List<Long> attributeIds = attributes.stream()
                .map(DeviceAttribute::getId)
                .collect(Collectors.toList());
        DeviceAttributeHistoryQueryDto historyQuery = new DeviceAttributeHistoryQueryDto();
        historyQuery.setDeviceAttributeIds(attributeIds);
        historyQuery.setStartTime(query.getStartTime());
        historyQuery.setEndTime(query.getEndTime());
        List<DeviceAttributeHistory> histories = deviceAttributeHistoryService.listByAttributeIds(historyQuery);

        // 4. 构造时间桶 xAxis
        int granMinutes = granularityMinutes(query.getGranularity());
        List<LocalDateTime> buckets = buildBuckets(query.getStartTime(), query.getEndTime(), granMinutes);
        List<String> xAxis = buckets.stream()
                .map(t -> formatBucket(t, query.getGranularity()))
                .collect(Collectors.toList());

        // 5. attributeId -> deviceId 反查表
        Map<Long, Long> attrToDevice = attributes.stream()
                .collect(Collectors.toMap(DeviceAttribute::getId, DeviceAttribute::getDeviceId, (k1, k2) -> k2));

        // 6. 按 deviceId 分组聚合（按桶索引聚合）
        //    注意：历史存储为瞬时值（每 15 分钟一个采集点），不做叠加/求和，
        //    桶内若有多个点（如按小时聚合时桶内 4 个 15 分钟点）取平均；
        //    15 分钟粒度下每桶恰好 1 个点，平均即为原值。
        //    deviceId -> 桶索引 -> 值列表
        Map<Long, Map<Integer, List<Double>>> grouped = new LinkedHashMap<>();
        for (Long did : query.getDeviceIds()) {
            grouped.computeIfAbsent(did, k -> new LinkedHashMap<>());
        }

        if (CollectionUtil.isNotEmpty(histories)) {
            for (DeviceAttributeHistory h : histories) {
                if (h.getValue() == null || h.getCollectionTime() == null) {
                    continue;
                }
                Long deviceId = attrToDevice.get(h.getAttributeId());
                if (deviceId == null) {
                    continue;
                }
                int idx = locateBucket(buckets, h.getCollectionTime(), granMinutes);
                if (idx < 0) {
                    continue;
                }
                Double val = parseDouble(h.getValue());
                if (val == null) {
                    continue;
                }
                grouped.computeIfAbsent(deviceId, k -> new LinkedHashMap<>())
                        .computeIfAbsent(idx, k -> new ArrayList<>())
                        .add(val);
            }
        }

        // 7. 拼装 series（按入参设备顺序输出，便于前端稳定展示）
        List<ReturnAirCo2TrendVo.TrendSeries> seriesList = new ArrayList<>();
        List<String> legend = new ArrayList<>();
        int bucketCount = buckets.size();
        for (Long did : query.getDeviceIds()) {
            Device dev = deviceMap.get(did);
            String seriesName = dev == null
                    ? ("设备-" + did)
                    : (StringUtils.isNotBlank(dev.getDeviceName()) ? dev.getDeviceName() : dev.getDeviceCode());
            legend.add(seriesName);

            ReturnAirCo2TrendVo.TrendSeries series = new ReturnAirCo2TrendVo.TrendSeries();
            series.setName(seriesName);
            series.setDeviceId(did);
            // 无数据的时间桶填充 0（前端要求 null 显示为 0）
            List<Double> data = new ArrayList<>(Collections.nCopies(bucketCount, 0.0));
            Map<Integer, List<Double>> bucketMap = grouped.getOrDefault(did, Collections.emptyMap());
            for (Map.Entry<Integer, List<Double>> e : bucketMap.entrySet()) {
                int i = e.getKey();
                if (i < 0 || i >= bucketCount) {
                    continue;
                }
                List<Double> values = e.getValue();
                if (values == null || values.isEmpty()) {
                    continue;
                }
                double sum = 0;
                for (Double d : values) {
                    sum += d;
                }
                double avg = sum / values.size();
                // 保留 1 位小数
                data.set(i, Math.round(avg * 10.0) / 10.0);
            }
            series.setData(data);
            seriesList.add(series);
        }

        // 8. 组装返回
        ReturnAirCo2TrendVo vo = new ReturnAirCo2TrendVo();
        vo.setTitle(query.getAttributeName());
        vo.setUnit("ppm");
        vo.setThreshold(query.getThreshold());
        vo.setXAxis(xAxis);
        vo.setLegend(legend);
        vo.setSeries(seriesList);
        return vo;
    }



    // ---------------- private helpers ----------------

    /**
     * 默认时间范围：当天 00:00:00 ~ 23:59:59。
     * 历史数据按 15 分钟周期整点槽位存储（00:00/00:15/00:30/00:45...），
     * 因此将起止时间对齐到 15 分钟网格，保证聚合桶与存储槽位对齐。
     */
    private void normalizeTimeRange(ReturnAirCo2TrendQueryDto q) {
        LocalDate today = LocalDate.now();
        if (q.getStartTime() == null) {
            q.setStartTime(today.atStartOfDay());
        }
        if (q.getEndTime() == null) {
            q.setEndTime(today.atTime(23, 59, 59));
        }
        if (q.getStartTime().isAfter(q.getEndTime())) {
            LocalDateTime tmp = q.getStartTime();
            q.setStartTime(q.getEndTime());
            q.setEndTime(tmp);
        }
        // 对齐到 15 分钟网格：start 向下取整、end 向上取整
        q.setStartTime(alignDown15Min(q.getStartTime()));
        q.setEndTime(alignUp15Min(q.getEndTime()));
    }



    /** 向下对齐到 15 分钟槽位（如 00:07 -> 00:00） */
    private LocalDateTime alignDown15Min(LocalDateTime t) {
        int remainder = t.getMinute() % 15;
        return remainder == 0 ? t : t.minusMinutes(remainder).withSecond(0).withNano(0);
    }

    /** 向上对齐到 15 分钟槽位（如 23:59 -> 次日 00:00） */
    private LocalDateTime alignUp15Min(LocalDateTime t) {
        int remainder = t.getMinute() % 15;
        return remainder == 0 ? t : t.plusMinutes(15 - remainder).withSecond(0).withNano(0);
    }

    /**
     * 按粒度生成时间桶起点列表（含 start、end；end 自动对齐到桶起点）
     */
    private List<LocalDateTime> buildBuckets(LocalDateTime start, LocalDateTime end, int minutes) {
        List<LocalDateTime> list = new ArrayList<>();
        LocalDateTime cur = start;
        while (!cur.isAfter(end)) {
            list.add(cur);
            cur = cur.plusMinutes(minutes);
        }
        return list;
    }

    private int granularityMinutes(String granularity) {
        if (granularity == null) {
            return 60;
        }
        switch (granularity.toLowerCase()) {
            case "15min":
            case "15":
                return 15;
            case "day":
            case "d":
                return 24 * 60;
            case "hour":
            case "h":
            default:
                return 60;
        }
    }

    private String formatBucket(LocalDateTime t, String granularity) {
        if (granularity == null) {
            return t.format(HOUR_FMT);
        }
        switch (granularity.toLowerCase()) {
            case "15min":
            case "15":
                return t.format(MIN_FMT);
            case "day":
            case "d":
                return t.format(DAY_FMT);
            default:
                return t.format(HOUR_FMT);
        }
    }

    /**
     * 二分查找时间所属桶序号
     */
    private int locateBucket(List<LocalDateTime> buckets, LocalDateTime t, int minutes) {
        int lo = 0, hi = buckets.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            LocalDateTime b = buckets.get(mid);
            LocalDateTime next = b.plusMinutes(minutes);
            if (!t.isBefore(b) && t.isBefore(next)) {
                return mid;
            }
            if (t.isBefore(b)) {
                hi = mid - 1;
            } else {
                lo = mid + 1;
            }
        }
        return -1;
    }

    private Double parseDouble(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ReturnAirCo2TrendVo emptyResult(ReturnAirCo2TrendQueryDto q) {
        ReturnAirCo2TrendVo vo = new ReturnAirCo2TrendVo();
        vo.setTitle(q == null || q.getAttributeName() == null ? "回风二氧化碳" : q.getAttributeName());
        vo.setUnit("ppm");
        vo.setThreshold(q == null ? null : q.getThreshold());
        vo.setXAxis(Collections.emptyList());
        vo.setLegend(Collections.emptyList());
        vo.setSeries(Collections.emptyList());
        return vo;
    }

}
