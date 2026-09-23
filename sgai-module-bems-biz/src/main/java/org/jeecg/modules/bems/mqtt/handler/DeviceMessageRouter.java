package org.jeecg.modules.bems.mqtt.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.mqtt.vo.HandleResult;
import org.jeecg.modules.bems.mqtt.vo.ResultRspVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 设备消息路由器
 * <p>
 * 路由信息来源优先级：
 * 1. 报文里的 device_type / sn / cmd
 * 2. topic 的 /{deviceType}/{deviceId}/{action} 三段
 */
@Slf4j
@Component
public class DeviceMessageRouter {

    private final Map<String, DeviceMessageHandler> handlerMap;

    public DeviceMessageRouter(List<DeviceMessageHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(DeviceMessageHandler::supportDeviceType, Function.identity()));
        log.info("设备处理器注册完成: {}", handlerMap.keySet());
    }

    public HandleResult route(String topic, String payload) {
        String deviceType = null;
        String deviceId = null;
        String action = null;
        String msgId = null;

        // 1. 优先从报文解析
        try {
            JSONObject json = JSON.parseObject(payload);
            msgId = json.getString("msg_id");
            deviceType = json.getString("device_type");
            deviceId = json.getString("sn");
            action = json.getString("cmd");
        } catch (Exception e) {
            log.error("解析报文失败 topic={}", topic, e);
            return HandleResult.of(ResultRspVO.fail("", "json_parse_error"), null, null, null);
        }

        // 2. 缺省从 topic 兜底：/ {deviceType} / {deviceId} / {action}
        List<String> segs = new ArrayList<>();
        if (topic != null) {
            for (String p : topic.split("/")) {
                if (p != null && !p.isEmpty()) {
                    segs.add(p);
                }
            }
        }
        if (segs.size() >= 3) {
            if (isBlank(deviceType)) deviceType = segs.get(0);
            if (isBlank(deviceId))   deviceId   = segs.get(1);
            if (isBlank(action))     action     = segs.get(2);
        }

        if (isBlank(deviceType) || isBlank(deviceId) || isBlank(action)) {
            log.warn("无法确定路由信息 topic={}, deviceType={}, deviceId={}, action={}",
                    topic, deviceType, deviceId, action);
            return HandleResult.of(ResultRspVO.fail(msgId, "invalid_route"),
                    deviceType, deviceId, action);
        }

        DeviceMessageHandler handler = handlerMap.get(deviceType);
        if (handler == null) {
            log.warn("未注册的设备类型: {}", deviceType);
            return HandleResult.of(ResultRspVO.fail(msgId, "unsupported_device_type"),
                    deviceType, deviceId, action);
        }

        return handler.handle(topic, payload, deviceType, deviceId, action);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}