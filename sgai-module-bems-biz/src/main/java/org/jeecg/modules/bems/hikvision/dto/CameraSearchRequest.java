package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 海康摄像头查询请求参数（/api/resource/v1/cameras）
 *
 * @author bems
 */
@Data
@Accessors(chain = true)
public class CameraSearchRequest {

    /** 页码，从1开始，范围 (0, ~) */
    private Integer pageNo;

    /** 每页条数，范围 (0, 1000] */
    private Integer pageSize;
}
