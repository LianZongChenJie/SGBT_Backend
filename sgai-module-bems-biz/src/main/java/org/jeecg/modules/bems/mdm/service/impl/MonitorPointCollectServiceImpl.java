package org.jeecg.modules.bems.mdm.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.bems.integration.config.IntegrationProperties;
import org.jeecg.modules.bems.mdm.entity.MonitorPoint;
import org.jeecg.modules.bems.mdm.entity.MonitorPointHistory;
import org.jeecg.modules.bems.mdm.mapper.MonitorPointHistoryMapper;
import org.jeecg.modules.bems.mdm.mapper.MonitorPointMapper;
import org.jeecg.modules.bems.mdm.service.IMonitorPointCollectService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 监测点实时数据采集服务实现
 * <p>
 * 流程（每 15 分钟）：
 * 1. 从 monitor_point 读取全部采集点(pid/category/dataType)，按 category 分组；
 * 2. 每个类别按批次(500)调第三方实时接口(/RealData)拉取当前值，仅收录本类 pid 的结果；
 * 3. 读到值的点：值按类型归一化(Boolean->1/0、整数去小数等)，覆盖更新 monitor_point.value/gather_time；
 * 4. 按 15 分钟对齐槽位 upsert 到 monitor_point_history（同一 pid 同一槽位重复仅更新）。
 */
@Slf4j
@Service
@AllArgsConstructor
public class MonitorPointCollectServiceImpl implements IMonitorPointCollectService {

    /** 单次第三方请求的点数上限（分批，避免单次响应过大） */
    private static final int HTTP_CHUNK = 500;
    /** 批量落库每批行数 */
    private static final int DB_BATCH = 500;

    private final MonitorPointMapper monitorPointMapper;
    private final MonitorPointHistoryMapper monitorPointHistoryMapper;
    private final IntegrationProperties props;

    @Override
    public void collectOnce() {
        List<MonitorPoint> points = monitorPointMapper.selectList(
                new LambdaQueryWrapper<MonitorPoint>()
                        .select(MonitorPoint::getPid, MonitorPoint::getCategory, MonitorPoint::getDataType));
        if (points == null || points.isEmpty()) {
            log.warn("monitor_point 中没有配置采集点，跳过监测点采集");
            return;
        }
        // 采集时间对齐到当前整十五分钟槽位
        LocalDateTime dataTime = alignTo15MinuteSlot(LocalDateTime.now());
        // 按类别分组，保持类别稳定顺序
        Map<String, List<MonitorPoint>> grouped = new LinkedHashMap<>();
        for (MonitorPoint p : points) {
            grouped.computeIfAbsent(p.getCategory(), k -> new ArrayList<>()).add(p);
        }
        int total = 0;
        for (Map.Entry<String, List<MonitorPoint>> e : grouped.entrySet()) {
            int n;
            try {
                n = collectCategory(e.getKey(), e.getValue(), dataTime);
            } catch (Exception ex) {
                log.error("监测点采集失败 category={}", e.getKey(), ex);
                continue;
            }
            total += n;
        }
        log.info("监测点采集完成: 类别数={}, 读到值点数={}, 槽位={}", grouped.size(), total, dataTime);
    }

    /**
     * 采集单个类别：分批拉取并落库
     *
     * @return 本类别读到值的点数
     */
    private int collectCategory(String category, List<MonitorPoint> points, LocalDateTime dataTime) {
        List<MonitorPoint> updates = new ArrayList<>();
        List<MonitorPointHistory> histories = new ArrayList<>();
        int read = 0;
        for (int i = 0; i < points.size(); i += HTTP_CHUNK) {
            List<MonitorPoint> chunk = points.subList(i, Math.min(i + HTTP_CHUNK, points.size()));
            Map<Long, Object> real = fetchRealData(chunk);
            for (MonitorPoint p : chunk) {
                Object raw = real.get(p.getPid());
                if (raw == null) {
                    continue;
                }
                String value = convertValue(raw);
                if (value == null) {
                    continue;
                }
                read++;
                MonitorPoint up = new MonitorPoint();
                up.setPid(p.getPid());
                up.setValue(value);
                up.setGatherTime(dataTime);
                updates.add(up);

                MonitorPointHistory h = new MonitorPointHistory();
                h.setPid(p.getPid());
                h.setCategory(p.getCategory());
                h.setValue(value);
                h.setCollectionTime(dataTime);
                histories.add(h);
            }
        }
        flushUpdate(updates);
        flushHistory(histories);
        log.info("监测点采集完成 category={}, 期望点数={}, 读到值={}, 槽位={}", category, points.size(), read, dataTime);
        return read;
    }

    /**
     * 拉取第三方实时数据：POST /RealData，body {"tagids":"pid1,pid2,..."}，
     * 返回 pid -> 原始值(pv) 的映射（仅保留本批点）。
     */
    private Map<Long, Object> fetchRealData(List<MonitorPoint> chunk) {
        Map<Long, Object> result = new HashMap<>();
        if (chunk.isEmpty()) {
            return result;
        }
        String url = props.getGas().getUrl();
        if (StringUtils.isBlank(url)) {
            log.warn("监测点采集: 第三方 RealData url 未配置");
            return result;
        }
        StringBuilder tagids = new StringBuilder();
        for (MonitorPoint p : chunk) {
            if (tagids.length() > 0) {
                tagids.append(",");
            }
            tagids.append(p.getPid());
        }
        String requestBody = "{\"tagids\":\"" + tagids + "\"}";
        HttpURLConnection con = null;
        BufferedReader br = null;
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
                out.write(requestBody);
                out.flush();
            }
            br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line.trim());
            }
            JSONObject json = JSONObject.parseObject(sb.toString());
            JSONObject data = json == null ? null : json.getJSONObject("data");
            if (data == null) {
                return result;
            }
            JSONArray values = data.getJSONArray("values");
            if (values == null) {
                return result;
            }
            for (int i = 0; i < values.size(); i++) {
                JSONObject o = values.getJSONObject(i);
                Long pid = o.getLong("pid");
                if (pid != null) {
                    result.put(pid, o.get("pv"));
                }
            }
        } catch (Exception e) {
            log.warn("监测点采集第三方拉取失败 url={}, 点数={}", url, chunk.size(), e);
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (con != null) {
                    con.disconnect();
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    /**
     * 批量覆盖更新 monitor_point 实时值
     */
    private void flushUpdate(List<MonitorPoint> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }
        try {
            for (int i = 0; i < updates.size(); i += DB_BATCH) {
                monitorPointMapper.updateValues(updates.subList(i,
                        Math.min(i + DB_BATCH, updates.size())));
            }
        } catch (Exception e) {
            log.error("监测点实时值批量更新失败", e);
        }
    }

    /**
     * 批量写入 monitor_point_history（槽位 upsert）
     */
    private void flushHistory(List<MonitorPointHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return;
        }
        try {
            for (int i = 0; i < histories.size(); i += DB_BATCH) {
                monitorPointHistoryMapper.upsertHistory(histories.subList(i,
                        Math.min(i + DB_BATCH, histories.size())));
            }
        } catch (Exception e) {
            log.error("监测点历史保存失败", e);
        }
    }

    /**
     * 将时间对齐到当前整十五分钟槽位：分钟向下取整到 15 的倍数，秒与纳秒清零
     * 如 08:00:05 -> 08:00:00，08:16:59 -> 08:15:00
     */
    public static LocalDateTime alignTo15MinuteSlot(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        int slotMinute = (time.getMinute() / 15) * 15;
        return time.withMinute(slotMinute).withSecond(0).withNano(0);
    }

    /**
     * 值转换：Boolean -> "1"/"0"，整数不带小数，BigDecimal 去尾零，其余 toString
     */
    public static String convertValue(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean) {
            return (Boolean) v ? "1" : "0";
        }
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).stripTrailingZeros().toPlainString();
        }
        if (v instanceof Long || v instanceof Integer || v instanceof Short || v instanceof Byte) {
            return v.toString();
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return v.toString();
    }
}
