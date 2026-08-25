package org.jeecg.modules.bems.integration.dto;

import lombok.Data;

@Data
public class DevicePushItem {
    private String id;
    private String name;
    private String categoryId;
    private String spaceId;
    private String remark;
    private String deviceCode;   // 设备编码（信任落库；insert 空时由 name 兜底）
}
