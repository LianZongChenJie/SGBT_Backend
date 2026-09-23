package org.jeecg.modules.bems.mqtt.handler;

import org.jeecg.modules.bems.mqtt.vo.HandleResult;

/**
 * 设备消息处理器接口
 */
public interface DeviceMessageHandler {

    /** 支持的设备类型标识，如 camera */
    String supportDeviceType();

    /**
     * 处理消息
     *
     * @param topic      原始 topic
     * @param payload    报文
     * @param deviceType 设备类型标识（可能来自报文，也可能来自 topic）
     * @param deviceId   设备标识
     * @param action     订阅主题/动作
     */
    HandleResult handle(String topic, String payload,
                        String deviceType, String deviceId, String action);
}