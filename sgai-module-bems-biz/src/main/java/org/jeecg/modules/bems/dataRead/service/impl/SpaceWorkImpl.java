package org.jeecg.modules.bems.dataRead.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sunwayland.pspace.entity.PsDataWithTagId;
import com.sunwayland.pspace.entity.PsResult;
import com.sunwayland.pspace.enums.PsErrorCodeEnum;
import com.sunwayland.pspace.enums.PsQualityEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.boot.starter.lock.client.RedissonLockClient;
import org.jeecg.modules.bems.alarm.service.IAlarmRecordService;
import org.jeecg.modules.bems.dataRead.service.IPspaceWork;
import org.jeecg.modules.bems.dataRead.util.PspaceUtils;
import org.jeecg.modules.bems.entity.*;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.entity.DeviceAttributeHistory;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeHistoryService;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.jeecg.modules.bems.service.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SpaceWorkImpl implements IPspaceWork {
    private final PspaceUtils pspaceUtils;

    private final IDeviceAttributeService deviceAttributeService;

    private final IDeviceService deviceService;

    private final IDeviceAttributeHistoryService deviceAttributeHistoryService;

    private final IAlarmRecordService alarmRecordService;

    private final IRealDataService realDataService;
    private final IHourDataService hourDataService;

    private final IMinuteDataService minuteDataService;

    private final IDayDataService dayDataService;

    private final IYearDataService yearDataService;

    private final IMonthDataService monthDataService;

    private final RedissonLockClient redissonLockClient;

    public SpaceWorkImpl(PspaceUtils pspaceUtils, IDeviceAttributeService deviceAttributeService,
                         IDeviceService deviceService, IDeviceAttributeHistoryService deviceAttributeHistoryService,
                         IAlarmRecordService alarmRecordService, IRealDataService realDataService,RedissonLockClient redissonLockClient,
                         IHourDataService hourDataService, IMinuteDataService minuteDataService, IDayDataService dayDataService, IYearDataService yearDataService, IMonthDataService monthDataService) {
        this.pspaceUtils = pspaceUtils;
        this.deviceAttributeService = deviceAttributeService;
        this.deviceService = deviceService;
        this.deviceAttributeHistoryService = deviceAttributeHistoryService;
        this.alarmRecordService = alarmRecordService;
        this.realDataService = realDataService;
        this.minuteDataService = minuteDataService;
        this.dayDataService = dayDataService;
        this.yearDataService = yearDataService;
        this.monthDataService = monthDataService;
        this.hourDataService = hourDataService;
        this.redissonLockClient = redissonLockClient;

    }

    /**
     * 批量获取实时数据
     *
     * @param tagIds 标签id集合
     * @return 实时数据集合；未连接 pspace 或读取失败时返回 null
     */
    @Override
    public List<PsDataWithTagId> realReadList(List<Long> tagIds) {
        if (pspaceUtils.client == null) {
            log.warn("pspace 未连接（mock 模式），走降级逻辑");
            return null;
        }
        PsResult<PsDataWithTagId> psResult = pspaceUtils.client.realReadListV2(tagIds);
        if (Objects.equals(psResult.getCode(), PsErrorCodeEnum.PSRET_OK)
                && psResult.getData() != null && !psResult.getData().isEmpty()) {
            return psResult.getData();
        } else {
            log.warn("冷源历史数据无缓存且处于 mock 模式(connect 不可用), 无法读取: 读取点位数={}", tagIds.size());
            return null;
        }
    }

    /**
     * 从 device_attribute 查询所有采集编码(acquisition_coding)为纯数字的属性，
     * 以其作为 tagId 批量读取实时数据，并按返回数据中的 tagId 回写对应行的 value。
     *
     * @return 实时数据集合；读取失败或返回空时为空集合
     */
    @Override
    public int refreshRealValueByNumericAcquisition(String type) {
        // 1.只查询采集编码为纯数字的属性（兼容 MySQL 的 REGEXP）
        List<DeviceAttribute> attributes = new ArrayList<>();
        if (type.equals("ELDB")) {
            List<String> zxygzdns = List.of("\\ZBLN\\ELDB\\B2_ZXYGZDN", "\\ZBLN\\ELDB\\B1_ZXYGZDN");
            attributes = deviceAttributeService.list(
                    new LambdaQueryWrapper<DeviceAttribute>()
                            .in(DeviceAttribute::getAttributeCode, zxygzdns)
                            .isNotNull(DeviceAttribute::getAcquisitionCoding)
                            .ne(DeviceAttribute::getAcquisitionCoding, "")
                            .apply("acquisition_coding REGEXP '^[0-9]+$'"));
        } else {
            attributes = deviceAttributeService.list(
                    new LambdaQueryWrapper<DeviceAttribute>()
                            .isNotNull(DeviceAttribute::getAcquisitionCoding)
                            .ne(DeviceAttribute::getAcquisitionCoding, "")
                            .apply("acquisition_coding REGEXP '^[0-9]+$'"));
        }
        // 2.过滤采集编码为纯数字的记录，作为 tagId 集合
        List<Long> tagIds = attributes.stream()
                .map(DeviceAttribute::getAcquisitionCoding)
                .filter(StringUtils::isNumeric)
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());
        if (tagIds.isEmpty()) {
            log.info("device_attribute 中未找到采集编码为纯数字的属性点");
            return 0;
        }
        // 3.批量读取实时数据
        List<PsDataWithTagId> dataList = this.realReadList(tagIds);
        if (dataList == null || dataList.isEmpty()) {
            return 0;
        }
        // 4.按返回的 tagId(=采集编码) 回写 value（BOOL 转 0/1，其余类型转字符串）：
        //   先更新数据库，再把新值同步写回 attributes 内存对象，并收集本次刷新到的属性行
        int updateCount = 0;
        List<DeviceAttribute> newAttributes = new ArrayList<>();
        Set<Long> collectedAttrIds = new HashSet<>();
        for (PsDataWithTagId item : dataList) {
            if (item == null || item.getTagId() == null || item.getValue() == null) {
                continue;
            }
            String coding = String.valueOf(item.getTagId());
            Object rawValue = item.getValue();
            String valueStr = rawValue instanceof Boolean
                    ? (Boolean.TRUE.equals(rawValue) ? "1" : "0")
                    : String.valueOf(rawValue);
            PsQualityEnum quality = item.getQuality();
            boolean updated = deviceAttributeService.update(new LambdaUpdateWrapper<DeviceAttribute>()
                    .eq(DeviceAttribute::getAcquisitionCoding, coding)
                    .set(DeviceAttribute::getValue, valueStr)
                    .set(DeviceAttribute::getQualityStamp, quality.getDesc()));
            if (updated) {
                updateCount++;
            }
            // 编码匹配的属性行：写回 value 与质量戳，并作为“本次刷新点”收集（同一属性行重复返回时只保留一条）
            for (DeviceAttribute attr : attributes) {
                if (attr != null && attr.getId() != null && Objects.equals(attr.getAcquisitionCoding(), coding)) {
                    attr.setValue(valueStr);
                    attr.setQualityStamp(quality.getDesc());
                    if (collectedAttrIds.add(attr.getId())) {
                        newAttributes.add(attr);
                    }
                }
            }
        }
        log.info("数字采集编码实时值刷新完成: 请求点数={}, 返回点数={}, 命中更新行数={}",
                tagIds.size(), dataList.size(), updateCount);
        // 5.更新所属设备的运行状态与最后采集时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dataTime = now.withMinute((now.getMinute() / 15) * 15).withSecond(0).withNano(0);
        this.updateDeviceGatherStatus(newAttributes, dataTime, "在线");
        //6.存储设备属性历史数据（仅本次刷新到实时值的属性行）
        this.saveDeviceAttributeHistory(newAttributes, dataTime);
        if (type.equals("ELDB")) {
//            //8.正向有功总电能写入data_real 因alertDeviceEldb方法中已经有了
//            this.saveDataReal(newAttributes, dataTime);
            //7.存储告警记录 耗电特殊处理
            this.alertDeviceEldb(newAttributes);
        } else {
            //7.存储告警记录 通用存储
            this.alertDevice(newAttributes);
        }
        return updateCount;
    }
    /**
     * 累计值告警检测线程池
     * <p>告警检测含多次DB查询（规则、点位、能耗数据），若在MQTT消费线程同步执行，
     * 会阻塞Paho心跳PINGREQ发送，超过keepalive时间被broker断开连接(EOFException)。
     * 故移出消费主线程异步执行，队列满时丢弃并告警，避免内存堆积。</p>
     */
    private static final ExecutorService ALARM_DETECT_EXECUTOR = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10000),
            r -> {
                Thread t = new Thread(r, "alarm-detect");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    private void alertDeviceEldb(List<DeviceAttribute> newAttributes) {
        //根据设备id 获取deviceCode
        List<Long> deviceIds = newAttributes.stream().map(v -> v.getDeviceId()).collect(Collectors.toList());
        List<Device> devices = deviceService.list(new QueryWrapper<Device>().in("id", deviceIds));
        Map<Long, List<DeviceAttribute>> deviceAttributeMap = newAttributes.stream().collect(Collectors.groupingBy(DeviceAttribute::getDeviceId));
        for (Device device : devices){
            String deviceCode = device.getDeviceCode();
            DeviceAttribute deviceAttribute = deviceAttributeMap.get(device.getId()).get(0);
            // 预检查设备存在性，避免能耗计算内部静默返回
            if (device == null) {
                log.warn("能耗计算跳过：设备不存在, deviceCode={}, uniqueKey={}", deviceCode);
                return;
            }
            String lockKey = getLockKey(deviceCode);
            boolean locked = false;
            boolean needAlarmDetect = false;
            LocalDateTime alarmHourTime = null;
            LocalDateTime now = LocalDateTime.now();
            deviceAttribute.setGatherTime(now);
            try {
                locked = redissonLockClient.tryLock(lockKey, 10, 60);
                if (locked) {
                    BigDecimal value = new BigDecimal(deviceAttribute.getValue().trim());
                    // 接收正向有功电能表底值，更新 实时/分钟/小时/日/月/年 数据
                    calculateEnergy(device, deviceAttribute.getGatherTime(), value);
                    // 锁内仅做能耗计算，告警检测放到解锁后异步执行，避免占用锁时间与阻塞消费线程心跳
                    alarmHourTime = deviceAttribute.getGatherTime().withMinute(0).withSecond(0).withNano(0);
                    needAlarmDetect = true;
                } else {
                }
            } catch (Exception e) {
                log.error("电度数据能耗计算失败, deviceCode={}, deviceAttributeId={}, value={}",
                        deviceCode, deviceAttribute.getId(), deviceAttribute.getValue(), e);
            } finally {
                if (locked) {
                    redissonLockClient.unlock(lockKey);
                }
            }
            // 累计值告警检测：在锁外、能耗计算完成后异步执行（此时小时/日/月/年累计值已落库，能查到最新值），
            // 传入数据时间戳对齐到整点的小时时间，与 calculateEnergy 写入小时数据的 hourTime 保持一致
            if (needAlarmDetect) {
                Long alarmDeviceId = device.getId();
                log.info("累计值告警检测提交, deviceCode={}, hourTime={}, deviceId={}", deviceCode, alarmHourTime, alarmDeviceId);
                LocalDateTime finalAlarmHourTime = alarmHourTime;
                ALARM_DETECT_EXECUTOR.submit(() -> {
                    try {
                        alarmRecordService.alarmDetection(alarmDeviceId, finalAlarmHourTime);
                    } catch (Exception e) {
                        log.error("累计值告警检测失败, deviceId={}, hourTime={}", alarmDeviceId, finalAlarmHourTime, e);
                    }
                });
            }
        }

    }

    /**
     * 正向有功电能表底值能耗计算：
     * 1. data_real：保存当前表底值
     * 2. 校验：若最新一条数据的结束值大于接收的表底数（表底倒退，疑似换表/重置），
     * 仅保存实时数据，不进行分钟/小时/日/月/年计算
     * 3. data_minute：无上一条则开始值=结束值=表底数；否则开始值=上一条结束值，结束值=当前表底数
     * 4. data_hour：本小时无记录则开始值=结束值=表底数；否则开始值不变，结束值=当前表底数
     * 5. data_day：今日所有小时value之和，有则更新，无则新增
     * 6. data_month：本月所有天value之和，有则更新，无则新增
     * 7. data_year：本年所有月value之和，有则更新，无则新增
     */
    private void calculateEnergy(Device device, LocalDateTime timeStamp, BigDecimal value) {
        Long deviceId = device.getId();

        // 1. 实时数据 data_real：保存当前表底值
        realDataService.save(deviceId, timeStamp, value);

        // 2. 校验表底数是否倒退：最新一条数据的结束值大于接收的表底数时，
        //    不进行分钟/小时/日/月/年计算，仅保留实时数据
        MinuteData lastMinute = minuteDataService.findLatest(deviceId);
        if (lastMinute != null && lastMinute.getEndValue() != null
                && lastMinute.getEndValue().compareTo(value) > 0) {
            return;
        }

        // 3. 分钟数据 data_minute
        LocalDateTime minuteTime = timeStamp.withSecond(0).withNano(0);
        if (lastMinute == null) {
            // 无上一条记录：开始值=结束值=表底数，value=结束值-开始值=0
            MinuteData minute = new MinuteData();
            minute.setDeviceId(deviceId);
            minute.setTime(minuteTime);
            minute.setStartValue(value);
            minute.setEndValue(value);
            minute.setValue(BigDecimal.ZERO);
            minuteDataService.saveOrUpdate(minute);
        } else if (lastMinute.getTime().equals(minuteTime)) {
            // 同一分钟再次上报：更新该条，开始值不变，结束值=当前表底数
            lastMinute.setEndValue(value);
            lastMinute.setValue(value.subtract(lastMinute.getStartValue() == null ? BigDecimal.ZERO : lastMinute.getStartValue()));
            minuteDataService.saveOrUpdate(lastMinute);
        } else {
            // 有上一条记录：开始值=上一条结束值，结束值=当前表底数
            MinuteData minute = new MinuteData();
            minute.setDeviceId(deviceId);
            minute.setTime(minuteTime);
            minute.setStartValue(lastMinute.getEndValue());
            minute.setEndValue(value);
            minute.setValue(value.subtract(lastMinute.getEndValue() == null ? BigDecimal.ZERO : lastMinute.getEndValue()));
            minuteDataService.saveOrUpdate(minute);
        }

        // 3. 小时数据 data_hour：时间为本小时的那条
        LocalDateTime hourTime = timeStamp.withMinute(0).withSecond(0).withNano(0);
        HourData hour = hourDataService.getOne(new LambdaQueryWrapper<HourData>()
                .eq(HourData::getDeviceId, deviceId)
                .eq(HourData::getTime, hourTime), false);
        if (hour == null) {
            // 本小时无记录：开始值=结束值=表底数，value=结束值-开始值=0
            hour = new HourData();
            hour.setDeviceId(deviceId);
            hour.setTime(hourTime);
            hour.setStartValue(value);
            hour.setEndValue(value);
            hour.setValue(BigDecimal.ZERO);
            hour.setComputeValue(BigDecimal.ZERO);
        } else {
            // 已有记录：开始值不变，结束值=当前表底数
            hour.setEndValue(value);
            hour.setValue(value.subtract(hour.getStartValue() == null ? BigDecimal.ZERO : hour.getStartValue()));
            hour.setComputeValue(hour.getValue());
        }
        hourDataService.saveOrUpdate(hour);

        // 4. 日数据 data_day：今天所有小时value之和
        LocalDateTime dayStart = hourTime.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        BigDecimal dayValue = sum(hourDataService.findByDeviceIdAndTimeRange(deviceId, dayStart, dayEnd), HourData::getValue);
        DayData day = dayDataService.findByDeviceIdAndTime(deviceId, dayStart);
        if (day == null) {
            day = new DayData();
            day.setDeviceId(deviceId);
            day.setTime(dayStart);
        }
        day.setValue(dayValue);
        dayDataService.saveOrUpdate(day);

        // 5. 月数据 data_month：本月所有天value之和
        LocalDateTime monthStart = dayStart.withDayOfMonth(1);
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        BigDecimal monthValue = sum(dayDataService.findByDeviceIdsAndTimeRange(Collections.singletonList(deviceId), monthStart, monthEnd), DayData::getValue);
        MonthData month = monthDataService.findByDeviceIdAndTime(deviceId, monthStart);
        if (month == null) {
            month = new MonthData();
            month.setDeviceId(deviceId);
            month.setTime(monthStart);
        }
        month.setValue(monthValue);
        monthDataService.saveOrUpdate(month);

        // 6. 年数据 data_year：本年所有月value之和
        LocalDateTime yearStart = monthStart.withMonth(1);
        LocalDateTime yearEnd = yearStart.plusYears(1);
        BigDecimal yearValue = sum(monthDataService.findByDeviceIdsAndTimeRange(Collections.singletonList(deviceId), yearStart, yearEnd), MonthData::getValue);
        YearData year = yearDataService.findByDeviceIdAndTime(deviceId, yearStart);
        if (year == null) {
            year = new YearData();
            year.setDeviceId(deviceId);
            year.setTime(yearStart);
        }
        year.setValue(yearValue);
        yearDataService.saveOrUpdate(year);
    }
    /**
     * 对列表中的值字段求和，跳过空值
     */
    private <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> valueGetter) {
        if (list == null || list.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return list.stream()
                .map(valueGetter)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private String getLockKey(String deviceCode) {
        return "lock:device:data:gather" + deviceCode;
    }

    private void saveDataReal(List<DeviceAttribute> newAttributes, LocalDateTime time) {
        //获取正向有功总电能id
        List<String> zxygzdns = List.of("\\ZBLN\\ELDB\\B2_ZXYGZDN", "\\ZBLN\\ELDB\\B1_ZXYGZDN");
        //查询 newAttributes 中 attributeCode 是 zxygzdn 的属性行
        List<DeviceAttribute> zxygzdnAttributes = newAttributes.stream()
                .filter(attr -> attr != null && attr.getAttributeCode() != null
                        && zxygzdns.contains(attr.getAttributeCode()))
                .collect(Collectors.toList());
        if (zxygzdnAttributes == null || zxygzdnAttributes.isEmpty()) {
            return;
        }
        log.info("正向有功总电能写入data_real: {}", zxygzdnAttributes);
        for (DeviceAttribute attr : zxygzdnAttributes) {
            Long deviceId = attr.getDeviceId();
            BigDecimal value = attr.getValue() != null ? new BigDecimal(attr.getValue()) : null;
            //按定时器存储一次
            realDataService.save(deviceId, time, value);
            //获取当前时间段的数据
            MinuteData minuteDataCurrent = minuteDataService.findByDeviceAndTime(deviceId, time);
            //获取上个时间段的数据
            MinuteData minuteDataLast = minuteDataService.findByDeviceAndTime(deviceId, time.minus(15, ChronoUnit.MINUTES));

            minuteDataLast = Objects.nonNull(minuteDataLast) ? minuteDataLast : new MinuteData();
            BigDecimal minuteDataLastValue = Objects.nonNull(minuteDataLast.getEndValue()) ? minuteDataLast.getEndValue() : BigDecimal.ZERO;
            if (minuteDataCurrent == null) {
                //第一个数据
                minuteDataCurrent = new MinuteData();
                minuteDataCurrent.setStartValue(minuteDataLastValue);
                minuteDataCurrent.setEndValue(value);
                minuteDataCurrent.setTime(time);
                minuteDataCurrent.setDeviceId(deviceId);
                minuteDataCurrent.setValue(minuteDataCurrent.getEndValue().subtract(minuteDataCurrent.getStartValue()));
                minuteDataService.saveOrUpdate(minuteDataCurrent);
            } else {
                //更新数据
                minuteDataCurrent.setStartValue(minuteDataLastValue);
                minuteDataCurrent.setEndValue(value);
                minuteDataCurrent.setTime(time);
                minuteDataCurrent.setValue(value.subtract(minuteDataLastValue));
                minuteDataService.saveOrUpdate(minuteDataCurrent);
            }

            //按小时更新存储一次
            HourData hourDataCurrent = hourDataService.findByDeviceIdAndTime(deviceId, getHourRange(time));
            HourData hourDataLast = hourDataService.findByDeviceIdAndTime(deviceId, getHourRange(time.minus(1, ChronoUnit.HOURS)));
            hourDataLast = Objects.nonNull(hourDataLast) ? hourDataLast : new HourData();
            BigDecimal hourDataLastEndValue = Objects.nonNull(hourDataLast.getEndValue()) ? hourDataLast.getEndValue() : BigDecimal.ZERO;
            if (hourDataCurrent == null) {
                //第一个数据
                hourDataCurrent = new HourData();
                hourDataCurrent.setStartValue(hourDataLastEndValue);
                hourDataCurrent.setEndValue(value);
                hourDataCurrent.setTime(getHourRange(time));
                hourDataCurrent.setDeviceId(deviceId);
                hourDataCurrent.setValue(hourDataCurrent.getEndValue().subtract(hourDataCurrent.getStartValue()));
                hourDataCurrent.setComputeValue(hourDataCurrent.getValue());
                hourDataService.saveOrUpdate(hourDataCurrent);
            } else {
                //更新数据
                hourDataCurrent.setStartValue(hourDataLastEndValue);
                hourDataCurrent.setEndValue(value);
                hourDataCurrent.setTime(getHourRange(time));
                hourDataCurrent.setValue(value.subtract(hourDataLastEndValue));
                hourDataCurrent.setComputeValue(hourDataCurrent.getValue());
                hourDataService.saveOrUpdate(hourDataCurrent);
            }

            //按日更新存储一次
            DayData dayDataCurrent = dayDataService.findByDeviceIdAndTime(deviceId, getDateRange(time));
            DayData dayDataLast = dayDataService.findByDeviceIdAndTime(deviceId, getDateRange(time.minus(1, ChronoUnit.DAYS)));
            dayDataLast = Objects.nonNull(dayDataLast) ? dayDataLast : new DayData();
            BigDecimal dayDataLastEndValue = Objects.nonNull(dayDataLast.getValue()) ? dayDataLast.getValue() : BigDecimal.ZERO;
            if (dayDataCurrent == null) {
                //第一个数据
                dayDataCurrent = new DayData();
                dayDataCurrent.setTime(getDateRange(time));
                dayDataCurrent.setDeviceId(deviceId);
                dayDataCurrent.setValue(value);
                dayDataService.saveOrUpdate(dayDataCurrent);
            } else {
                //更新数据
                dayDataCurrent.setTime(getDateRange(time));
                dayDataCurrent.setValue(value.subtract(dayDataLastEndValue));
                dayDataService.saveOrUpdate(dayDataCurrent);
            }

            //按月更新存储一次
            MonthData monthDataCurrent = monthDataService.findByDeviceIdAndTime(deviceId, getMonthRange(time));
            MonthData monthDataLast = monthDataService.findByDeviceIdAndTime(deviceId, getMonthRange(time.minus(1, ChronoUnit.MONTHS)));
            monthDataLast = Objects.nonNull(monthDataLast) ? monthDataLast : new MonthData();
            BigDecimal monthDataLastEndValue = Objects.nonNull(monthDataLast.getValue()) ? monthDataLast.getValue() : BigDecimal.ZERO;
            if (monthDataCurrent == null) {
                //第一个数据
                monthDataCurrent = new MonthData();
                monthDataCurrent.setTime(getMonthRange(time));
                monthDataCurrent.setDeviceId(deviceId);
                monthDataCurrent.setValue(value);
                monthDataService.saveOrUpdate(monthDataCurrent);
            } else {
                //更新数据
                monthDataCurrent.setTime(getMonthRange(time));
                monthDataCurrent.setValue(value.subtract(monthDataLastEndValue));
                monthDataService.saveOrUpdate(monthDataCurrent);
            }

            //按年更新存储一次
            YearData yearDataCurrent = yearDataService.findByDeviceIdAndTime(deviceId, getYearRange(time));
            YearData yearDataLast = yearDataService.findByDeviceIdAndTime(deviceId, getYearRange(time.minus(1, ChronoUnit.YEARS)));
            yearDataLast = Objects.nonNull(yearDataLast) ? yearDataLast : new YearData();
            BigDecimal yearDataLastEndValue = Objects.nonNull(yearDataLast.getValue()) ? yearDataLast.getValue() : BigDecimal.ZERO;
            if (yearDataCurrent == null) {
                //第一个数据
                yearDataCurrent = new YearData();
                yearDataCurrent.setTime(getYearRange(time));
                yearDataCurrent.setDeviceId(deviceId);
                yearDataCurrent.setValue(value);
                yearDataService.saveOrUpdate(yearDataCurrent);
            } else {
                //更新数据
                yearDataCurrent.setTime(getYearRange(time));
                yearDataCurrent.setValue(value.subtract(yearDataLastEndValue));
                yearDataService.saveOrUpdate(yearDataCurrent);
            }

        }
    }

    public LocalDateTime getYearRange(LocalDateTime dateTime) {
        // 取当年 1 月 1 日 00:00
        LocalDateTime yearStart = dateTime
                .withDayOfYear(1)
                .truncatedTo(ChronoUnit.DAYS);

        return yearStart;
    }

    public LocalDateTime getMonthRange(LocalDateTime dateTime) {
        // 取当月 1 号 00:00
        LocalDateTime monthStart = dateTime
                .withDayOfMonth(1)
                .truncatedTo(ChronoUnit.DAYS);
        return monthStart;
    }

    public LocalDateTime getDateRange(LocalDateTime dateTime) {
        LocalDateTime dayStart = dateTime.truncatedTo(ChronoUnit.DAYS);

        return dayStart;
    }

    public LocalDateTime getHourRange(LocalDateTime dateTime) {
        LocalDateTime hourStart = dateTime.truncatedTo(ChronoUnit.HOURS);

        return hourStart;
    }


    /**
     * 将本次刷新到实时值的属性行（value 已写回内存对象）存入设备属性历史表 device_attribute_history：
     * 逐条组装历史记录后循环入库（attribute_id/device_id/value/collection_time）。
     *
     * @param attributes 本次刷新到实时值的设备属性列表（value 已是最新）
     * @param now        本次采集时间，作为 collection_time
     */
    private void saveDeviceAttributeHistory(List<DeviceAttribute> attributes, LocalDateTime now) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        // 1.遍历属性行组装历史记录（value 已由刷新步骤写回内存对象，无需再依赖实时返回集合）
        List<DeviceAttributeHistory> historyList = new ArrayList<>();
        for (DeviceAttribute attr : attributes) {
            if (attr == null || attr.getId() == null || attr.getDeviceId() == null
                    || attr.getValue() == null) {
                continue;
            }
            DeviceAttributeHistory history = new DeviceAttributeHistory();
            history.setDeviceId(attr.getDeviceId());
            history.setAttributeId(attr.getId());
            history.setCollectionTime(now);
            history.setValue(attr.getValue());
            history.setQualityStamp(attr.getQualityStamp());
            historyList.add(history);
        }
        // 2.循环逐条入库（单条失败不影响其余记录，避免同一槽位重复插入时整批回滚）
        int successCount = 0;
        int failCount = 0;
        for (DeviceAttributeHistory history : historyList) {
            try {
                deviceAttributeHistoryService.save(history);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("设备属性历史入库失败: attributeId={}, collectionTime={}, value={}, 原因={}",
                        history.getAttributeId(), history.getCollectionTime(), history.getValue(), e.getMessage());
            }
        }
        log.info("设备属性历史入库完成: 成功={}, 失败={}, 采集时间={}", successCount, failCount, now);
    }

    /**
     * 根据设备属性直接刷新所属设备的运行状态与最后采集时间：
     * 对 attributes 中非空的 deviceId 去重后逐个更新。
     *
     * @param attributes 设备属性列表（取其 deviceId 定位所属设备）
     * @param time       本次采集时间，写入设备 last_gather_time
     * @param online     目标运行状态，取 DeviceConstant.DEVICE_RUN_STATA_ONLINE/OFFLINE
     * @return 更新设备数
     */
    @Override
    public int updateDeviceGatherStatus(List<DeviceAttribute> attributes, LocalDateTime time, String online) {
        if (attributes == null || attributes.isEmpty()) {
            return 0;
        }
        // 1.直接对 attributes 的 deviceId 去重
        Set<Long> deviceIds = new HashSet<>();
        for (DeviceAttribute attr : attributes) {
            if (attr != null && attr.getDeviceId() != null) {
                deviceIds.add(attr.getDeviceId());
            }
        }
        if (deviceIds.isEmpty()) {
            return 0;
        }
        // 2.逐个设备更新运行状态与最后采集时间
        int updateCount = 0;
        for (Device device : deviceService.findByDeviceIds(deviceIds)) {
            if (device == null || device.getDeviceCode() == null) {
                continue;
            }
            // 一条 SQL 同时更新运行状态与最后采集时间
            deviceService.updateStatusAndGatherTime(device.getDeviceCode(), online, time);
            updateCount++;
        }
        log.info("设备采集状态更新完成: 命中设备数={}, 目标状态={}, 采集时间={}", deviceIds.size(), online, time);
        return updateCount;
    }


    public void alertDevice(List<DeviceAttribute> attributes) {
        try {
            for (DeviceAttribute item : attributes) {
                alarmRecordService.alarmDetection(item.getDeviceId(), item.getId(), item.getValue());
            }
        } catch (Exception e) {
            log.error("点位值变化消息发送失败", e);
        }
    }
}
