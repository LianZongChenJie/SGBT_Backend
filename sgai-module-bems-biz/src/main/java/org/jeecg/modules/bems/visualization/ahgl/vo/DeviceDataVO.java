package org.jeecg.modules.bems.visualization.ahgl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 设备数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("设备数据")
public class DeviceDataVO {

    @ApiModelProperty("设备名称")
    private String deviceName;

    @ApiModelProperty("时间-数值数据列表")
    private List<TimeValueVO> data;
}
