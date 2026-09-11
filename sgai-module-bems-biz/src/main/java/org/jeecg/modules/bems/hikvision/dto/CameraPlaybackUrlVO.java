package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;

import java.util.List;

/**
 * 摄像头回放地址返回给前端的VO
 *
 * @author bems
 */
@Data
public class CameraPlaybackUrlVO {

    /** 摄像头唯一编码 */
    private String cameraIndexCode;

    /** 回放地址 */
    private String url;

    /** 分页标记：标记本次查询的全部标识符，用于分片多次查询 */
    private String uuid;

    /** 录像片段信息列表 */
    private List<PlaybackSegment> list;

    /**
     * 录像片段信息
     */
    @Data
    public static class PlaybackSegment {

        /** 录像锁定类型：0-全部，1-未锁定，2-已锁定 */
        private Integer lockType;

        /** 片段开始时间（ISO8601） */
        private String beginTime;

        /** 片段结束时间（ISO8601） */
        private String endTime;

        /** 片段大小（单位：Byte） */
        private Long size;
    }
}
