package org.jeecg.modules.bems.hikvision.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 摄像头播放地址返回给前端的VO
 *
 * @author bems
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CameraPlayUrlVO {

    /** 摄像头唯一编码 */
    private String cameraIndexCode;

    /** 播放地址 */
    private String url;
}
