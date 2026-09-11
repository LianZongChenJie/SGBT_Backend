package org.jeecg.modules.bems.hikvision.dto;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.List;

/**
 * 海康事件推送请求体
 * <p>海康平台通过HTTP POST方式推送事件到本接口，JSON格式如下：</p>
 * <pre>
 * {
 *   "method": "OnEventNotify",
 *   "params": {
 *     "ability": "event_rule",
 *     "events": [{
 *       "data": { ... },
 *       "eventId": "BE26E09F-...",
 *       "eventType": 131588,
 *       "happenTime": "2019-01-02T15:17:24.000+08:00",
 *       "srcIndex": "da107dd19...",
 *       "srcName": "浙江杭州",
 *       "srcType": "camera",
 *       "status": 0,
 *       "timeout": 0
 *     }],
 *     "sendTime": "2019-01-02T15:19:59.857+08:00"
 *   }
 * }
 * </pre>
 *
 * @author bems
 */
@Data
public class EventNotifyPushRequest {

    /** 方法名，固定为 "OnEventNotify" */
    private String method;

    /** 事件参数 */
    private EventNotifyParams params;

    /**
     * 事件参数
     */
    @Data
    public static class EventNotifyParams {

        /** 事件类别，如：event_rule */
        private String ability;

        /** 事件列表（可能包含多个事件） */
        private List<EventNotifyEvent> events;

        /** 事件从接收者发出的时间，ISO8601格式 */
        private String sendTime;
    }

    /**
     * 单个事件
     */
    @Data
    public static class EventNotifyEvent {

        /** 事件唯一标识 */
        private String eventId;

        /** 事件类型，数值编码 */
        private Integer eventType;

        /** 事件发生时间（设备时间），ISO8601格式 */
        private String happenTime;

        /** 事件源编号 */
        private String srcIndex;

        /** 事件源名称 */
        private String srcName;

        /** 事件源类型，如：camera */
        private String srcType;

        /** 事件状态：0-瞬时 1-开始 2-停止 */
        private Integer status;

        /** 脉冲超时时间，单位：秒 */
        private Integer timeout;

        /** 事件等级：0-未配置 1-低 2-中 3-高 */
        private Integer eventLvl;

        /** 事件详细数据，结构因事件类型而异，存储为JSON字符串 */
        private JSONObject data;

        /** 事件发生的事件源父设备编码，从data中提取 */
        private String srcParentIndex;
    }
}
