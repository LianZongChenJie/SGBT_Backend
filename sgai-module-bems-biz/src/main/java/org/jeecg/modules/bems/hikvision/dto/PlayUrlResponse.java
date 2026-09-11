package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;

/**
 * 海康获取摄像头播放地址响应
 *
 * @author bems
 */
@Data
public class PlayUrlResponse {

    /** 播放地址 */
    private String url;
}
