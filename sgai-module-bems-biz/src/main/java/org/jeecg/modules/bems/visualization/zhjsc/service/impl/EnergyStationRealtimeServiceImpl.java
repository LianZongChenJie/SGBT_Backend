package org.jeecg.modules.bems.visualization.zhjsc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.jeecg.modules.bems.visualization.zhjsc.enums.EnergyStationPointEnum;
import org.jeecg.modules.bems.visualization.zhjsc.service.EnergyStationRealtimeService;
import org.jeecg.modules.bems.visualization.zhjsc.vo.RealtimePointVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.RealtimeSnapshotVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 能源站实时数据实现（查库版）。
 * <p>
 * 数据来源为 {@code device_attribute} 的当前值，由定时任务 {@code ReadDataJob} 每 15 分钟从采集通道刷新一次，
 * 因此本接口的时效等于采集周期（默认最长 15 分钟）。不做缓存、不直连采集通道：
 * <ul>
 *   <li>纯查询、无状态，调用频率不受限制；</li>
 *   <li>与采集任务完全解耦，不会挤占采集通道；</li>
 *   <li>数据时间取 {@code device_attribute.gather_time}，缺失时回退到 {@code device.last_gather_time}；</li>
 *   <li>数据时间超过 {@code bems.realtime.stale-minutes}（默认 30 分钟）时 {@code stale=true}，
 *       让调用方识别“采集停更”，避免把陈旧值当实时值展示。</li>
 * </ul>
 */
@Slf4j
@Service
public class EnergyStationRealtimeServiceImpl implements EnergyStationRealtimeService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IDeviceAttributeService deviceAttributeService;

    private final IDeviceService deviceService;

    /** 数据过期阈值（分钟）：超过该时长未更新即视为采集停更 */
    @Value("${bems.realtime.stale-minutes:30}")
    private long staleMinutes;

    public EnergyStationRealtimeServiceImpl(IDeviceAttributeService deviceAttributeService,
                                            IDeviceService deviceService) {
        this.deviceAttributeService = deviceAttributeService;
        this.deviceService = deviceService;
    }

    @Override
    public RealtimeSnapshotVO snapshot(List<String> keys) {
        List<EnergyStationPointEnum> points = resolve(keys);
        if (points.isEmpty()) {
            return buildVO(null, new HashMap<>(), points);
        }
        Object[] codings = points.stream()
                .map(p -> (Object) String.valueOf(p.getTagId()))
                .distinct()
                .toArray();

        List<DeviceAttribute> attributes = deviceAttributeService.list(
                new LambdaQueryWrapper<DeviceAttribute>()
                        .in(DeviceAttribute::getAcquisitionCoding, codings));

        Map<String, DeviceAttribute> byCoding = attributes.stream()
                .filter(a -> a.getAcquisitionCoding() != null)
                .collect(Collectors.toMap(DeviceAttribute::getAcquisitionCoding, a -> a, (a, b) -> a));

        LocalDateTime dataTime = latestOf(attributes);
        if (dataTime == null) {
            dataTime = latestDeviceGatherTime(attributes);
        }
        return buildVO(dataTime, byCoding, points);
    }

    /**
     * 点位级采集时间：取本次涉及属性中最大的 gather_time
     */
    private LocalDateTime latestOf(List<DeviceAttribute> attributes) {
        return attributes.stream()
                .map(DeviceAttribute::getGatherTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * 兜底：点位级 gather_time 尚未写入时，用设备最后采集时间（采集任务每轮会更新）
     */
    private LocalDateTime latestDeviceGatherTime(List<DeviceAttribute> attributes) {
        Set<Long> deviceIds = new HashSet<>();
        for (DeviceAttribute a : attributes) {
            if (a.getDeviceId() != null) {
                deviceIds.add(a.getDeviceId());
            }
        }
        if (deviceIds.isEmpty()) {
            return null;
        }
        return deviceService.listByIds(deviceIds).stream()
                .map(Device::getLastGatherTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private RealtimeSnapshotVO buildVO(LocalDateTime dataTime, Map<String, DeviceAttribute> byCoding,
                                       List<EnergyStationPointEnum> points) {
        List<RealtimePointVO> list = new ArrayList<>(points.size());
        for (EnergyStationPointEnum point : points) {
            DeviceAttribute attr = byCoding.get(String.valueOf(point.getTagId()));
            RealtimePointVO vo = new RealtimePointVO();
            vo.setKey(point.getKey());
            vo.setName(point.getName());
            vo.setUnit(point.getUnit());
            if (attr == null) {
                list.add(vo);
                continue;
            }
            vo.setValue(formatValue(point, attr.getValue()));
            vo.setDataType(point.isBoolPoint() ? "BOOL" : "DOUBLE");
            vo.setQuality(attr.getQualityStampName() != null ? attr.getQualityStampName() : attr.getQualityStamp());
            vo.setQualityCode(attr.getQualityStampEn());
            LocalDateTime gatherTime = attr.getGatherTime();
            if (gatherTime != null) {
                vo.setTimestamp(toMillis(gatherTime));
                vo.setTime(gatherTime.format(TIME_FORMAT));
            }
            list.add(vo);
        }

        RealtimeSnapshotVO result = new RealtimeSnapshotVO();
        result.setStale(isStale(dataTime));
        result.setDataTime(dataTime == null ? null : dataTime.format(TIME_FORMAT));
        result.setDataTimestamp(dataTime == null ? null : toMillis(dataTime));
        result.setPoints(list);
        return result;
    }

    private boolean isStale(LocalDateTime dataTime) {
        if (dataTime == null) {
            return true;
        }
        return Duration.between(dataTime, LocalDateTime.now()).toMinutes() > staleMinutes;
    }

    /**
     * 库里存的是字符串，按点位约定的小数位统一格式；开关量输出 0/1
     */
    private String formatValue(EnergyStationPointEnum point, String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();
        if (point.isBoolPoint()) {
            if ("true".equalsIgnoreCase(value)) {
                return "1";
            }
            if ("false".equalsIgnoreCase(value)) {
                return "0";
            }
            return value;
        }
        try {
            return new BigDecimal(value).setScale(point.getScale(), RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private List<EnergyStationPointEnum> resolve(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new ArrayList<>(EnergyStationPointEnum.byKey().values());
        }
        List<EnergyStationPointEnum> list = new ArrayList<>(keys.size());
        for (String key : keys) {
            EnergyStationPointEnum point = EnergyStationPointEnum.ofKey(key);
            if (point == null) {
                log.debug("未知的实时点位 key，已忽略: {}", key);
                continue;
            }
            if (!list.contains(point)) {
                list.add(point);
            }
        }
        return list;
    }

    private Long toMillis(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
