package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;

import java.util.List;

/**
 * 海康摄像头查询响应（/api/resource/v1/cameras）
 *
 * @author bems
 */
@Data
public class CameraSearchResponse {

    /** 总数 */
    private Integer total;

    /** 当前页码 */
    private Integer pageNo;

    /** 每页条数 */
    private Integer pageSize;

    /** 摄像头列表 */
    private List<CameraItem> list;

    /**
     * 海康返回的摄像头信息（v1接口）
     */
    @Data
    public static class CameraItem {

        /** 海拔 */
        private String altitude;

        /** 监控点唯一标识 */
        private String cameraIndexCode;

        /** 监控点名称 */
        private String cameraName;

        /** 监控点类型 */
        private Integer cameraType;

        /** 监控点类型说明 */
        private String cameraTypeName;

        /** 能力集 */
        private String capabilitySet;

        /** 能力集说明 */
        private String capabilitySetName;

        /** 智能分析能力集 */
        private String intelligentSet;

        /** 智能分析能力集说明 */
        private String intelligentSetName;

        /** 通道编号 */
        private String channelNo;

        /** 通道类型 */
        private String channelType;

        /** 通道子类型说明 */
        private String channelTypeName;

        /** 创建时间（ISO8601） */
        private String createTime;

        /** 所属编码设备唯一标识 */
        private String encodeDevIndexCode;

        /** 所属设备类型 */
        private String encodeDevResourceType;

        /** 所属设备类型说明 */
        private String encodeDevResourceTypeName;

        /** 监控点国标编号 */
        private String gbIndexCode;

        /** 安装位置 */
        private String installLocation;

        /** 键盘控制码 */
        private String keyBoardCode;

        /** 纬度 */
        private String latitude;

        /** 经度 */
        private String longitude;

        /** 摄像机像素 */
        private Integer pixel;

        /** 云镜类型 */
        private Integer ptz;

        /** 云台控制 */
        private Integer ptzController;

        /** 云台控制说明 */
        private String ptzControllerName;

        /** 云镜类型说明 */
        private String ptzName;

        /** 录像存储位置 */
        private String recordLocation;

        /** 录像存储位置说明 */
        private String recordLocationName;

        /** 所属区域唯一标识 */
        private String regionIndexCode;

        /** 在线状态（0-不在线，1-在线） */
        private Integer status;

        /** 状态说明 */
        private String statusName;

        /** 传输协议（0-UDP，1-TCP） */
        private Integer transType;

        /** 传输协议类型说明 */
        private String transTypeName;

        /** 接入协议 */
        private String treatyType;

        /** 接入协议类型说明 */
        private String treatyTypeName;

        /** 可视域相关（JSON格式） */
        private String viewshed;

        /** 更新时间（ISO8601） */
        private String updateTime;
    }
}
