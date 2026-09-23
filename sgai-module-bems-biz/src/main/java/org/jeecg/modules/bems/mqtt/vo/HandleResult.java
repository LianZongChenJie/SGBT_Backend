package org.jeecg.modules.bems.mqtt.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息处理结果：应答 + 路由信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandleResult {

    /** 应答内容 */
    private ResultRspVO rsp;

    /** 设备类型标识（动态），如 camera */
    private String deviceType;

    /** 设备标识（动态），如相机 SN */
    private String deviceId;

    /** 订阅主题/动作（动态），如 result、image_result */
    private String action;

    public static HandleResult of(ResultRspVO rsp, String deviceType, String deviceId, String action) {
        return new HandleResult(rsp, deviceType, deviceId, action);
    }

    /** 路由信息是否齐全（deviceType + deviceId） */
    public boolean hasRouteInfo() {
        return deviceType != null && !deviceType.trim().isEmpty()
                && deviceId != null && !deviceId.trim().isEmpty();
    }
}