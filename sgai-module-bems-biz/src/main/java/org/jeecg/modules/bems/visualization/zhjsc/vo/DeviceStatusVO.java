package org.jeecg.modules.bems.visualization.zhjsc.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重点设备运行状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("重点设备运行状态")
public class DeviceStatusVO {

    @ApiModelProperty("设备名称")
    @JsonProperty("device_name")
    private String deviceName;

    @ApiModelProperty("设备编码")
    @JsonProperty("device_code")
    private String deviceCode;

    @ApiModelProperty("设备属性")
    private DeviceAttributeStatusVO attribute;
}
