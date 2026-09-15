package org.jeecg.modules.bems.fire.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.alarm.entity.AlarmRecord;
import org.jeecg.modules.bems.alarm.service.IAlarmRecordService;
import org.jeecg.modules.bems.dataRead.util.PspaceUtils;
import org.jeecg.modules.bems.fire.service.IFireAlarmService;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
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
 * 1. 每 2 分钟 POST pSpace /HistData，区间 [now-2min, now]，tagids=5282-5292，charset=utf-8；
 * 2. 只保留质量戳 qy=192(Good) 的历史项，构建 pid -> (tm -> pv) 映射；
 * 3. 从 5292(二次码) 取事件：pv 长度=10 且 != "0000000000" 才有效；
 * 4. 以该 10 位二次码匹配 device_attribute.attribute_code（消防点位），
 *    再按同一时间戳取 5282(事件)/5283(数据源)/5284(设备类型)；
 * 5. 组装告警内容写入 alarm_record（告警类别/级别留空）。
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

    private static final DateTimeFormatter TM_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PspaceUtils pspaceUtils;
    private final IDeviceAttributeService deviceAttributeService;
    private final IDeviceService deviceService;
    private final IAlarmRecordService alarmRecordService;

    public FireAlarmServiceImpl(PspaceUtils pspaceUtils,
                                IDeviceAttributeService deviceAttributeService,
                                IDeviceService deviceService,
                                IAlarmRecordService alarmRecordService) {
        this.pspaceUtils = pspaceUtils;
        this.deviceAttributeService = deviceAttributeService;
        this.deviceService = deviceService;
        this.alarmRecordService = alarmRecordService;
    }

    @Override
    public int collectFireAlarm() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime btime = now.minusMinutes(2);
        String url = pspaceUtils.getWebBaseUrl() + "/HistData";
        String body = String.format(
                "{\"btime\":\"%s\",\"etime\":\"%s\",\"tagids\":\"%s\",\"charset\":\"utf-8\"}",
                btime.format(TM_FMT), now.format(TM_FMT), HIS_TAGIDS);

        String resp = postJson(url, body);
        if (resp == null) {
            return 0;
        }
        JSONObject root;
        try {
            root = JSONObject.parseObject(resp);
        } catch (Exception e) {
            log.error("消防历史数据解析失败: {}", resp, e);
            return 0;
        }
        JSONObject data = root == null ? null : root.getJSONObject("data");
        JSONArray values = data == null ? null : data.getJSONArray("values");
        if (values == null || values.isEmpty()) {
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
            alarmRecordService.save(record);
            saved++;
        }
        log.info("消防历史数据采集完成: 区间=[{}, {}], 5292事件={}, 保存告警={}, 过滤={}",
                btime.format(TM_FMT), now.format(TM_FMT), ecmMap.size(), saved, skipped);
        return saved;
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
