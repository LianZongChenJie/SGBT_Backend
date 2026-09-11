package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 海康获取摄像头回放地址请求参数
 * <p>对应海康 OpenAPI /api/video/v2/cameras/playbackURLs 请求体。</p>
 *
 * @author bems
 */
@Data
@Accessors(chain = true)
public class PlaybackUrlRequest {

    /** 监控点唯一标识 */
    private String cameraIndexCode;

    /** 存储类型：0-中心存储，1-设备存储，默认中心存储 */
    private String recordLocation;

    /** 取流协议：hik/rtsp/rtmp/hls/hlss/ws/wss/httpflv/httpsflv/httpmp4/httpsmp4，默认HIK */
    private String protocol;

    /** 传输协议：0-UDP，1-TCP，默认TCP（protocol为rtsp/rtmp时有效） */
    private Integer transmode;

    /** 开始查询时间（ISO8601：yyyy-MM-dd'T'HH:mm:ss.SSSXXX） */
    private String beginTime;

    /** 结束查询时间（ISO8601：yyyy-MM-dd'T'HH:mm:ss.SSSXXX），与开始时间相差不超过3天 */
    private String endTime;

    /** 分页查询id，上一次查询返回的uuid，设备存储时生效 */
    private String uuid;

    /** 扩展内容，格式：key=value */
    private String expand;

    /** 输出码流转封装格式：ps/rtp，protocol=rtsp时生效 */
    private String streamform;

    /** 查询录像的锁定类型：0-全部，1-未锁定，2-已锁定，默认0 */
    private Integer lockType;
}
