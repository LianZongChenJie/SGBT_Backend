package org.jeecg.modules.bems.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeviceAttributeEntity {

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 属性编码
     */
    private String attributeCode;
    /**
     * 属性名称
     */
    private String attributeName;

    /**
     * 采集编码
     */
    private String acquisitionCoding;

}
