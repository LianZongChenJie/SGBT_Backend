package org.jeecg.modules.bems.monitorSource.vo;

import lombok.Data;

/**
 * 监控源树第二层：设备节点
 */
@Data
public class MonitorSourceDeviceVo {

    private Long deviceId;
    private String deviceCode;
    private String deviceName;

    public MonitorSourceDeviceVo() {
    }

    public MonitorSourceDeviceVo(Long deviceId, String deviceCode, String deviceName) {
        this.deviceId = deviceId;
        this.deviceCode = deviceCode;
        this.deviceName = deviceName;
    }
}
