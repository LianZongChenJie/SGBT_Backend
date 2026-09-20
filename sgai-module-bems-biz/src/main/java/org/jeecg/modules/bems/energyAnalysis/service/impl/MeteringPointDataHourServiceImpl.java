package org.jeecg.modules.bems.energyAnalysis.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.bems.energyAnalysis.entity.MeteringPointDataHour;
import org.jeecg.modules.bems.energyAnalysis.mapper.MeteringPointDataHourMapper;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointDataHourService;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointRelService;
import org.jeecg.modules.bems.entity.HourData;
import org.jeecg.modules.bems.service.IHourDataService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class MeteringPointDataHourServiceImpl extends ServiceImpl<MeteringPointDataHourMapper, MeteringPointDataHour> implements IMeteringPointDataHourService {

    private final RedisUtil redisUtil;

    private static final String CACHE_KEY_PREFIX_MAX = "metering_point_data_hour_max:";

    private final IMeteringPointRelService meteringPointRelService;

    private final IHourDataService hourDataService;

    private final MeteringPointDataCalculationService calculationService;

    @Override
    public List<MeteringPointDataHour> findByTimeRangeAndPointIds(LocalDateTime startTime, LocalDateTime endTime, List<Long> pointIds) {
//        return list(new LambdaQueryWrapper<MeteringPointDataHour>()
//                .in(MeteringPointDataHour::getMeteringPointId,pointIds)
//                .between(MeteringPointDataHour::getTime,startTime,endTime));
        if (CollectionUtil.isEmpty(pointIds) || startTime == null || endTime == null) {
            return Collections.emptyList();
        }
        // 获取点位关联设备信息
        List<Long> deviceIds = meteringPointRelService.findDeviceIdByPointIds(pointIds);
        // 获取设备小时数据
        List<HourData> dataList = hourDataService.findByDeviceIdsAndTimeRange(deviceIds, startTime, endTime);
        return calculationService.calculation(pointIds, deviceIds, dataList).stream().map(MeteringPointDataHour::convert).toList();
    }


    @Override
    public void save(Long pointId, LocalDateTime time, BigDecimal value) {
        MeteringPointDataHour latest = findLatest(pointId);
        MeteringPointDataHour hourData = null;
        if (latest == null || !latest.getTime().isBefore(time)) {
            hourData = getOne(new LambdaQueryWrapper<MeteringPointDataHour>().eq(MeteringPointDataHour::getMeteringPointId, pointId).eq(MeteringPointDataHour::getTime, time));
        }
        if (hourData == null) {
            hourData = new MeteringPointDataHour();
            hourData.setMeteringPointId(pointId);
            hourData.setTime(time);
        }
        hourData.setValue(value);
        super.saveOrUpdate(hourData);
        if (latest == null || !latest.getTime().isAfter(time)) {
            redisUtil.set(getCacheKeyMax(pointId), hourData);
        }
    }

    @Override
    public List<MeteringPointDataHour> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return list(new LambdaQueryWrapper<MeteringPointDataHour>().between(MeteringPointDataHour::getTime, startTime, endTime));
    }

    @Override
    public List<MeteringPointDataHour> findByPointIdAndTimeRange(Long pointId, LocalDateTime startTime, LocalDateTime endTime) {
//        return list(new LambdaQueryWrapper<MeteringPointDataHour>()
//                .eq(MeteringPointDataHour::getMeteringPointId,pointId)
//                .between(MeteringPointDataHour::getTime,startTime,endTime));
        return findByTimeRangeAndPointIds(startTime, endTime, Collections.singletonList(pointId));
    }

    @Override
    public MeteringPointDataHour findByPointIdAndTime(Long pointId, LocalDateTime hour) {
        if (pointId == null || hour == null) {
            return null;
        }
//        return getOne(new LambdaQueryWrapper<MeteringPointDataHour>()
//                .eq(MeteringPointDataHour::getMeteringPointId,pointId)
//                .eq(MeteringPointDataHour::getTime,hour.withMinute(0).withSecond(0))
//        );
        List<MeteringPointDataHour> dataList = findByTimeRangeAndPointIds(hour.withMinute(0).withSecond(0), hour.withMinute(59).withSecond(59), Collections.singletonList(pointId));
        return CollectionUtil.isEmpty(dataList) ? null : dataList.get(0);
    }

    @Override
    public List<MeteringPointDataHour> getHourLast() {
        // 1. 查询每个测点的最新时间（仍然需要聚合或排序，这里用排序+去重）
        QueryWrapper<MeteringPointDataHour> timeWrapper = new QueryWrapper<>();
        timeWrapper.isNotNull("value")
                .orderByDesc("time");

        List<MeteringPointDataHour> all = list(timeWrapper);

        // 2. Java 分组，取每个测点第一条（时间最大）作为 time，value 累加
        Map<Long, MeteringPointDataHour> timeMap = new LinkedHashMap<>();
        Map<Long, BigDecimal> sumMap = new HashMap<>();

        for (MeteringPointDataHour item : all) {
            timeMap.putIfAbsent(item.getMeteringPointId(), item);
            sumMap.merge(item.getMeteringPointId(),
                    item.getValue() == null ? BigDecimal.ZERO : item.getValue(),
                    BigDecimal::add);
        }

        List<MeteringPointDataHour> list = timeMap.entrySet().stream()
                .map(e -> {
                    MeteringPointDataHour result = new MeteringPointDataHour();
                    result.setMeteringPointId(e.getKey());
                    result.setTime(e.getValue().getTime());
                    result.setValue(sumMap.get(e.getKey()));
                    return result;
                })
                .collect(Collectors.toList());
        return list;
    }

    private MeteringPointDataHour findLatest(Long pointId) {
        MeteringPointDataHour hourData = (MeteringPointDataHour) redisUtil.get(getCacheKeyMax(pointId));
        if (hourData == null) {
            hourData = findLatestByPointId(pointId);
            if (hourData != null) {
                redisUtil.set(getCacheKeyMax(pointId), hourData);
            }
        }
        return hourData;
    }

    private MeteringPointDataHour findLatestByPointId(Long pointId) {
        List<MeteringPointDataHour> list = list(new LambdaQueryWrapper<MeteringPointDataHour>()
                .eq(MeteringPointDataHour::getMeteringPointId, pointId)
                .orderByDesc(MeteringPointDataHour::getTime)
                .last("limit 1")
        );
        if (CollectionUtil.isEmpty(list)) {
            return null;
        }
        return list.get(0);
    }

    private String getCacheKeyMax(Long pointId) {
        return CACHE_KEY_PREFIX_MAX + pointId;
    }
}
