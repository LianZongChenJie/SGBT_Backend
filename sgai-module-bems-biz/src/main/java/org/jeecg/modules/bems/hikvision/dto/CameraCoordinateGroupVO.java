package org.jeecg.modules.bems.hikvision.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 摄像头坐标分组VO
 * <p>按经度、纬度对全部摄像头进行聚合，
 * 同一坐标的摄像头归为一组，返回该坐标下的摄像头数量与摄像头列表。</p>
 *
 * @author bems
 */
@Data
public class CameraCoordinateGroupVO {

    @JsonProperty("longitude")
    @ApiModelProperty(value = "longitude")
    private String longitude;

    @JsonProperty("latitude")
    @ApiModelProperty(value = "latitude")
    private String latitude;

    @JsonProperty("cameraCount")
    @ApiModelProperty(value = "cameraCount")
    private Integer cameraCount;

    @JsonProperty("cameraList")
    @ApiModelProperty(value = "cameraList")
    private List<CameraListVO> cameraList = new ArrayList<>();
}
