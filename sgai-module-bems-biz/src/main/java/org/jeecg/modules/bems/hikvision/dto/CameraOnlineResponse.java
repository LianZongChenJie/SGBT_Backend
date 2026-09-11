package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;

import java.util.List;

/**
 * 海康监控点在线状态查询响应（/api/nms/v1/online/camera/get）
 *
 * @author bems
 */
@Data
public class CameraOnlineResponse {

    /** 页码 */
    private Integer pageNo;

    /** 每页记录数 */
    private Integer pageSize;

    /** 总页数 */
    private Integer totalPage;

    /** 总记录数 */
    private Integer total;

    /** 监控点在线状态列表 */
    private List<OnlineItem> list;

    /**
     * 监控点在线状态信息
     */
    @Data
    public static class OnlineItem {

        /** 设备型号 */
        private String deviceType;

        /** 设备唯一编码 */
        private String deviceIndexCode;

        /** 区域编码 */
        private String regionIndexCode;

        /** 采集时间 */
        private String collectTime;

        /** 区域名字 */
        private String regionName;

        /** 资源唯一编码（摄像头编码） */
        private String indexCode;

        /** 设备名称 */
        private String cn;

        /** 码流传输协议，0：UDP，1：TCP */
        private String treatyType;

        /** 厂商，hikvision-海康，dahua-大华 */
        private String manufacturer;

        /** ip地址，监控点无此值 */
        private String ip;

        /** 端口，监控点无此值 */
        private Integer port;

        /** 在线状态，0-离线，1-在线 */
        private Integer online;
    }
}
