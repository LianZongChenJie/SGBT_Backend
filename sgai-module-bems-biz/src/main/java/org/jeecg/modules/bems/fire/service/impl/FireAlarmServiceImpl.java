package org.jeecg.modules.bems.fire.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.bems.alarm.entity.AlarmCategory;
import org.jeecg.modules.bems.alarm.entity.AlarmLevel;
import org.jeecg.modules.bems.alarm.entity.AlarmRecord;
import org.jeecg.modules.bems.alarm.service.IAlarmCategoryService;
import org.jeecg.modules.bems.alarm.service.IAlarmLevelService;
import org.jeecg.modules.bems.alarm.service.IAlarmRecordService;
import org.jeecg.modules.bems.dataRead.util.PspaceUtils;
import org.jeecg.modules.bems.entity.BusinessConfig;
import org.jeecg.modules.bems.fire.service.IFireAlarmService;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.jeecg.modules.bems.service.IBusinessConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 消防报警采集服务实现
 * <p>
 * 逻辑：
 * 1. 每 2 分钟 POST pSpace /HistData，区间 [水位线 - 重叠窗口, now]，tagids=5282-5292，charset=utf-8；
 *    水位线持久化在 business_config(fire:alarm:collect:last_time)，仅当本次区间完整处理成功后才推进，
 *    失败/重启/停机后下轮自动扩大窗口补齐（受 maxSpan 限制），避免固定 now-2min 窗口丢增量；
 * 2. 只保留质量戳 qy=192(Good) 的历史项，构建 pid -> (tm -> pv) 映射；
 * 3. 从 5292(二次码) 取事件：pv 长度=10 且 != "0000000000" 才有效；
 * 4. 以该 10 位二次码匹配 device_attribute.attribute_code（消防点位），
 *    再按同一时间戳取 5282(事件)/5283(数据源)/5284(设备类型)；
 * 5. 组装告警内容写入 alarm_record（告警类别=属性异常报警，告警等级=紧急）。
 */
@Slf4j
@Service
public class FireAlarmServiceImpl implements IFireAlarmService {

    /** 查询的历史点：5282-5292 */
    private static final String HIS_TAGIDS = "5282-5292";
    /** 事件 */
    private static final long PID_SJ = 5282L;
    /** 数据源 */
    private static final long PID_SJY = 5283L;
    /** 设备类型 */
    private static final long PID_SBLX = 5284L;
    /** 二次码 */
    private static final long PID_ECM = 5292L;
    /** 好数据质量戳 */
    private static final int QY_GOOD = 192;
    /** 无效二次码 */
    private static final String EMPTY_ECM = "0000000000";
    /** 二次码长度 */
    private static final int ECM_LENGTH = 10;
    /** 告警类别名称（对应 alarm_category.alarm_category_name） */
    private static final String ALARM_CATEGORY_NAME = "属性异常报警";
    /** 告警等级名称（对应 alarm_level.alarm_level_name） */
    private static final String ALARM_LEVEL_NAME = "紧急";

    private static final DateTimeFormatter TM_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 增量采集水位线（上次成功采集的截止时间）在 business_config 中的 config_key，值格式 yyyy-MM-dd HH:mm:ss。
     * 该行由定时任务在首次保存水位线时自动创建。
     */
    private static final String WATERMARK_KEY = "fire:alarm:collect:last_time";

    /**
     * 每轮窗口向前重叠的分钟数：只需覆盖 pSpace 历史「写入到可见」的延迟（一个调度周期即 2 分钟足够），
     * 更长的停机/失败区间由「不推进水位线 + 下轮窗口自动扩大」兜底，不是靠重叠，靠 alarm_record 去重保证幂等。
     */
    @Value("${fire.alarm.overlap-minutes:2}")
    private int overlapMinutes;

    /** 水位线缺失（首次启动/记录被删）时的回补窗口（分钟） */
    @Value("${fire.alarm.lookback-minutes:60}")
    private int lookbackMinutes;

    /** 单次查询区间跨度上限（分钟），防止长时间停机后一次拉取过多数据 */
    @Value("${fire.alarm.max-span-minutes:720}")
    private int maxSpanMinutes;

    private final PspaceUtils pspaceUtils;
    private final IDeviceAttributeService deviceAttributeService;
    private final IDeviceService deviceService;
    private final IAlarmRecordService alarmRecordService;
    private final IAlarmCategoryService alarmCategoryService;
    private final IAlarmLevelService alarmLevelService;
    private final IBusinessConfigService businessConfigService;

    public FireAlarmServiceImpl(PspaceUtils pspaceUtils,
                                IDeviceAttributeService deviceAttributeService,
                                IDeviceService deviceService,
                                IAlarmRecordService alarmRecordService,
                                IAlarmCategoryService alarmCategoryService,
                                IAlarmLevelService alarmLevelService,
                                IBusinessConfigService businessConfigService) {
        this.pspaceUtils = pspaceUtils;
        this.deviceAttributeService = deviceAttributeService;
        this.deviceService = deviceService;
        this.alarmRecordService = alarmRecordService;
        this.alarmCategoryService = alarmCategoryService;
        this.alarmLevelService = alarmLevelService;
        this.businessConfigService = businessConfigService;
    }

    @Override
    public int collectFireAlarm() {
        LocalDateTime now = LocalDateTime.now();
        // 起始时间 = 水位线 - 重叠窗口（水位线缺失则按 lookback 回补）
        LocalDateTime btime = resolveBtime(now);
        // 结束时间 = 自起始时间起最多推进 maxSpan，且不超过 now
        LocalDateTime etime = resolveEtime(btime, now);

        // 1. 拉取历史数据；请求/解析失败时不推进水位线，下个周期窗口自动扩大重试
        JSONObject data = fetchHistData(btime, etime);
        if (data == null) {
            log.error("消防历史数据获取失败，水位线保持不动: 区间=[{}, {}]",
                    btime.format(TM_FMT), etime.format(TM_FMT));
            return 0;
        }

        // 2. 解析并落库（内部按 点位+时间 去重，窗口重叠不会产生重复数据）
        int saved = processData(data, btime, etime);

        // 3. 本次区间已完整处理，水位线推进到「实际覆盖到的终点」。
        //    注意是 etime 而不是 now：落后时若直接跳到 now，未处理的那段会被永久跳过。
        saveWatermark(etime);
        return saved;
    }

    /**
     * 计算本次查询起始时间：水位线 - overlap；水位线缺失（首次启动/记录被删）时回补 lookback 分钟。
     * <p>
     * 用持久化水位线代替固定 now-2min，可覆盖：上一次执行期间晚到的数据、单次执行失败漏掉的区间、
     * 以及服务重启/停机期间的增量。重复查询由 alarm_record 的「点位 + 时间」去重兜住，不会产生重复告警。
     */
    private LocalDateTime resolveBtime(LocalDateTime now) {
        LocalDateTime watermark = readWatermark();
        LocalDateTime btime;
        if (watermark == null) {
            btime = now.minusMinutes(Math.max(lookbackMinutes, 1));
            log.info("消防报警采集: 未取到水位线，按回补窗口 {} 分钟采集", lookbackMinutes);
        } else {
            btime = watermark.minusMinutes(Math.max(overlapMinutes, 0));
        }
        // 时钟回拨或水位线超前于当前时间时，退化为一个重叠窗口
        if (btime.isAfter(now)) {
            log.warn("消防报警采集: 起始时间({})晚于当前时间({})，按重叠窗口处理", btime, now);
            btime = now.minusMinutes(Math.max(overlapMinutes, 1));
        }
        return btime;
    }

    /**
     * 计算本次查询结束时间：自 btime 起最多处理 maxSpan 分钟，且不超过当前时间 now。
     * <p>
     * maxSpan 是「单轮处理上限」而不是「截断起点」：落后超过 maxSpan 时本轮只处理一段，
     * 水位线推进到该段终点，下轮从那里接着追，因此长时间停机也能逐轮补齐、不会留下空洞。
     * 步长至少比 overlap 多 1 分钟，保证水位线每轮都有净推进、不会卡死。
     */
    private LocalDateTime resolveEtime(LocalDateTime btime, LocalDateTime now) {
        long span = Math.max(maxSpanMinutes, overlapMinutes + 1L);
        LocalDateTime etime = btime.plusMinutes(span);
        if (etime.isAfter(now)) {
            return now;
        }
        log.warn("消防报警采集: 待补区间较长，本轮只处理到 {}（单轮上限 {} 分钟），后续轮次继续追赶",
                etime.format(TM_FMT), maxSpanMinutes);
        return etime;
    }

    /**
     * 请求 /HistData 并返回 data 节点；请求失败或解析失败返回 null（调用方据此不推进水位线）。
     */
    private JSONObject fetchHistData(LocalDateTime btime, LocalDateTime etime) {
        String url = pspaceUtils.getWebBaseUrl() + "/HistData";
        String body = String.format(
                "{\"btime\":\"%s\",\"etime\":\"%s\",\"tagids\":\"%s\",\"charset\":\"utf-8\"}",
                btime.format(TM_FMT), etime.format(TM_FMT), HIS_TAGIDS);
        String resp = postJson(url, body);
        if (resp == null) {
            return null;
        }
        try {
            JSONObject root = JSONObject.parseObject(resp);
            return root == null ? null : root.getJSONObject("data");
        } catch (Exception e) {
            log.error("消防历史数据解析失败: {}", resp, e);
            return null;
        }
    }

    /**
     * 解析 data.values 并写入 alarm_record，返回新增告警条数。
     */
    private int processData(JSONObject data, LocalDateTime btime, LocalDateTime etime) {
        JSONArray values = data == null ? null : data.getJSONArray("values");
        if (values == null || values.isEmpty()) {
            log.info("消防历史数据采集: 区间=[{}, {}] 无数据",
                    btime.format(TM_FMT), etime.format(TM_FMT));
            return 0;
        }

        // pid -> (tm -> pv)，仅保留 qy=192
        Map<Long, Map<String, String>> pidTmPv = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            JSONObject v = values.getJSONObject(i);
            Long pid = v.getLong("pid");
            JSONArray items = v.getJSONArray("items");
            if (pid == null || items == null || items.isEmpty()) {
                continue;
            }
            Map<String, String> tmPv = new HashMap<>();
            for (int j = 0; j < items.size(); j++) {
                JSONObject it = items.getJSONObject(j);
                Integer qy = it.getInteger("qy");
                Object pv = it.get("pv");
                if (qy == null || qy != QY_GOOD || pv == null) {
                    continue;
                }
                tmPv.put(String.valueOf(it.get("tm")), String.valueOf(pv));
            }
            pidTmPv.put(pid, tmPv);
        }

        Map<String, String> ecmMap = pidTmPv.get(PID_ECM);
        if (ecmMap == null || ecmMap.isEmpty()) {
            log.info("消防历史数据采集: 本次无 5292(二次码) 好数据");
            return 0;
        }
        Map<String, String> sjMap = pidTmPv.getOrDefault(PID_SJ, Collections.emptyMap());
        Map<String, String> sjyMap = pidTmPv.getOrDefault(PID_SJY, Collections.emptyMap());
        Map<String, String> sblxMap = pidTmPv.getOrDefault(PID_SBLX, Collections.emptyMap());

        // 告警类别/等级：按名称取字典表（页面展示用）
        AlarmCategory alarmCategory = alarmCategoryService.getOne(
                new LambdaQueryWrapper<AlarmCategory>()
                        .eq(AlarmCategory::getAlarmCategoryName, ALARM_CATEGORY_NAME)
                        .last("limit 1"), false);
        AlarmLevel alarmLevel = alarmLevelService.getOne(
                new LambdaQueryWrapper<AlarmLevel>()
                        .eq(AlarmLevel::getAlarmLevelName, ALARM_LEVEL_NAME)
                        .last("limit 1"), false);

        int saved = 0;
        int skipped = 0;
        for (Map.Entry<String, String> entry : ecmMap.entrySet()) {
            String tm = entry.getKey();
            String ecm = entry.getValue();
            // 过滤：非 "0000000000" 且长度为 10
            if (ecm == null || ecm.length() != ECM_LENGTH || EMPTY_ECM.equals(ecm)) {
                skipped++;
                continue;
            }
            // 二次码匹配消防设备属性
            DeviceAttribute attr = deviceAttributeService.getOne(
                    new LambdaQueryWrapper<DeviceAttribute>()
                            .eq(DeviceAttribute::getAttributeCode, ecm)
                            .last("limit 1"), false);
            if (attr == null) {
                log.warn("消防历史数据: 二次码未匹配到设备属性 ecm={}", ecm);
                continue;
            }
            LocalDateTime alarmTime = parseTm(tm);
            // 去重：同一属性点位 + 同一时间 只存一条
            if (alarmTime != null && alarmRecordService.count(
                    new LambdaQueryWrapper<AlarmRecord>()
                            .eq(AlarmRecord::getPointId, attr.getId())
                            .eq(AlarmRecord::getAlarmTime, alarmTime)) > 0) {
                continue;
            }
            Device device = attr.getDeviceId() == null ? null : deviceService.getById(attr.getDeviceId());
            AlarmRecord record = new AlarmRecord();
            record.setDeviceId(attr.getDeviceId());
            record.setDeviceName(device == null ? null : device.getDeviceName());
            record.setDeviceCategoryId(device == null ? null : device.getCategoryId());
            record.setPointId(attr.getId());
            record.setPointName(attr.getAttributeName());
            record.setValue(ecm);
            record.setAlarmTime(alarmTime);
            record.setAlarmStatus(AlarmRecord.ALARM_STATUS_UNTREATED);
            record.setAlarmContent(buildAlarmContent(attr.getAttributeName(), ecm,
                    sjMap.get(tm), sjyMap.get(tm), sblxMap.get(tm)));
            // 告警类别 / 等级（属性异常报警 / 紧急）
            if (alarmCategory != null) {
                record.setAlarmCategoryId(alarmCategory.getId());
                record.setAlarmCategoryName(alarmCategory.getAlarmCategoryName());
            }
            if (alarmLevel != null) {
                record.setAlarmLevelId(alarmLevel.getId());
                record.setAlarmLevelName(alarmLevel.getAlarmLevelName());
                record.setAlarmLevelColor(alarmLevel.getAlarmLevelColor());
            }
            alarmRecordService.save(record);
            saved++;
        }
        log.info("消防历史数据采集完成: 区间=[{}, {}], 5292事件={}, 保存告警={}, 过滤={}",
                btime.format(TM_FMT), etime.format(TM_FMT), ecmMap.size(), saved, skipped);
        return saved;
    }

    /**
     * 读取水位线；未配置或格式非法时返回 null（调用方按回补窗口处理）。
     */
    private LocalDateTime readWatermark() {
        try {
            String value = businessConfigService.getValueByKey(WATERMARK_KEY);
            if (StringUtils.isBlank(value)) {
                return null;
            }
            return LocalDateTime.parse(value.trim(), TM_FMT);
        } catch (Exception e) {
            log.warn("消防报警采集: 读取水位线失败，按缺失处理", e);
            return null;
        }
    }

    /**
     * 保存水位线（不存在则创建）。保存失败只记录日志，不回滚已落库的告警，
     * 下轮会从旧水位线重新查询，由去重保证不会重复。
     */
    private void saveWatermark(LocalDateTime etime) {
        String value = etime.format(TM_FMT);
        try {
            BusinessConfig exist = businessConfigService.getOne(
                    new LambdaQueryWrapper<BusinessConfig>()
                            .eq(BusinessConfig::getConfigKey, WATERMARK_KEY)
                            .last("limit 1"), false);
            if (exist == null) {
                BusinessConfig config = new BusinessConfig();
                config.setName("消防报警采集-上次采集截止时间");
                config.setConfigKey(WATERMARK_KEY);
                config.setConfigValue(value);
                config.setRemark("FireAlarmJob 增量采集水位线，由定时任务自动维护，请勿手工修改");
                businessConfigService.save(config);
            } else {
                businessConfigService.updateByKey(WATERMARK_KEY, value);
            }
        } catch (Exception e) {
            log.error("消防报警采集: 保存水位线失败 value={}", value, e);
        }
    }

    /**
     * 组装告警内容：消防系统,<点名称>,二次码:X,事件:X,数据源:X,设备类型:X
     */
    private String buildAlarmContent(String pointName, String ecm, String sj, String sjy, String sblx) {
        return "消防系统," + safe(pointName) + ",二次码:" + safe(ecm)
                + ",事件:" + safe(sj) + ",数据源:" + safe(sjy) + ",设备类型:" + safe(sblx);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * 解析时间：tm 形如 2026-09-15 10:01:19.510，取到秒
     */
    private LocalDateTime parseTm(String tm) {
        if (tm == null || tm.length() < 19) {
            return null;
        }
        try {
            return LocalDateTime.parse(tm.substring(0, 19), TM_FMT);
        } catch (Exception e) {
            log.warn("消防历史数据: 时间解析失败 tm={}", tm);
            return null;
        }
    }

    /**
     * POST JSON（UTF-8）
     */
    private String postJson(String url, String body) {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoInput(true);
            con.setDoOutput(true);
            con.setConnectTimeout(8000);
            con.setReadTimeout(30000);
            try (BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(con.getOutputStream(), StandardCharsets.UTF_8))) {
                out.write(body);
                out.flush();
            }
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.error("消防历史数据请求失败 url={}, body={}", url, body, e);
            return null;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }
}
