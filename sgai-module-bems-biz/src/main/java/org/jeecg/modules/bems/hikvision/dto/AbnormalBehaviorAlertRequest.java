package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 异常行为预警请求参数
 *
 * @author bems
 */
@Data
@Accessors(chain = true)
public class AbnormalBehaviorAlertRequest {

    /** 查询开始时间（ISO8601标准，如 2024-05-03T00:00:00.000+08:00） */
    private String startTime;

    /** 查询结束时间（ISO8601标准） */
    private String endTime;

    /** 页码，从1开始 */
    private Integer pageNo;

    /** 每页条数 */
    private Integer pageSize;

    /** 事件类型码列表（可选），如：区域入侵、绊线入侵、徘徊检测等 */
    private Integer[] eventTypes;
}
