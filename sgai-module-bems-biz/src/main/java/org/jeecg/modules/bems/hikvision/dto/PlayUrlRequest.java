package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 海康获取摄像头播放地址请求参数
 *
 * @author bems
 */
@Data
@Accessors(chain = true)
public class PlayUrlRequest {

    /** 摄像头唯一编码 */
    private String cameraIndexCode;

    /** 码流类型：0-主码流，1-子码流 */
    private Integer streamType;

    /** 协议：hls/rtsp/rtmp */
    private String protocol;

    /** 传输模式：0-UDP，1-TCP */
    private Integer transmode;

    /** 扩展参数 */
    private String expand;

    /** 流格式：ps/mp4 */
    private String streamform;
}
