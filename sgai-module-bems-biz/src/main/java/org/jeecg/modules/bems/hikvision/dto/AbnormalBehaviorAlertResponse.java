package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;

import java.util.List;

/**
 * 异常行为预警响应
 *
 * @author bems
 */
@Data
public class AbnormalBehaviorAlertResponse {

    /** 结果总数 */
    private Integer total;

    /** 当前页码 */
    private Integer pageNo;

    /** 每页记录数 */
    private Integer pageSize;

    /** 异常行为预警列表 */
    private List<AbnormalBehaviorAlertItem> list;

    /**
     * 异常行为预警单条记录
     */
    @Data
    public static class AbnormalBehaviorAlertItem {

        /** 事件唯一标识 */
        private String eventId;

        /** 事件发生时间（ISO8601标准） */
        private String eventTime;

        /** 事件类型名称：如"区域入侵"、"绊线入侵"、"徘徊检测"等 */
        private String eventTypeName;

        /** 事件类型码 */
        private Integer eventType;

        /** 事件状态：1-瞬时事件，2-事件开始，3-事件结束 */
        private Integer status;

        /** 摄像头唯一编码 */
        private String cameraIndexCode;

        /** 摄像头名称 */
        private String cameraName;

        /** 事件描述 */
        private String eventDescription;

        /** 抓拍图片URL */
        private String picUrl;

        /** 目标所在区域名称 */
        private String regionName;
    }
}
