package org.jeecg.modules.bems.homePage.dto;

import lombok.Data;

@Data
public class CockpitEnergyConsumptionTrend {

    /**
     * 名称
     */
    private String name;

    /**
     * 计量规则点位id
     */
    private Long pointId;

    /**
     * 单位
     */
    private String unit;

}
