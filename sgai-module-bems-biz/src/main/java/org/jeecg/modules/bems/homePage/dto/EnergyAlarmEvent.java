package org.jeecg.modules.bems.homePage.dto;

import lombok.Data;

/**
 * 能源告警事件
 */
@Data
public class EnergyAlarmEvent {

    /**
     * 事件id
     */
    private String eventId;

    /**
     * 描述
     */
    private String describe;

    /**
     * 区域
     */
    private String area;

}
