package org.jeecg.modules.bems.mdm.dto;

import lombok.Data;

/**
 * 楼控打点汇总 Excel 行映射
 */
@Data
public class BuildingControlImportDto {

    /** 设备类别 */
    private String categoryName;

    /** 设备名称 */
    private String deviceName;

    /** 设备编码 */
    private String deviceCode;

    /** 是否读写（读/写） */
    private String readwriteLevel;

    /** 属性名称 */
    private String attributeName;

    /** 属性编码 */
    private String attributeCode;

    /** 属性单位 */
    private String unit;

    /** 关联点位 */
    private String acquisitionCoding;

    /** 点位翻译 */
    private String valueConfig;
}
