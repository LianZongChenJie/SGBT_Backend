package org.jeecg.modules.bems.homePage.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单位面积碳排量配置
 */
@Data
public class CockpitCarbonEmissionsPerUnitArea {

    /**
     * 单位面积
     */
    private BigDecimal area;

    /**
     * 计量规则id
     */
    private Long pointId;

}
