package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 重点设备运行状态汇总
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("重点设备运行状态汇总")
public class OperationStatusKeyEquipmentVO {

    @ApiModelProperty("设备总数")
    private Integer total;

    @ApiModelProperty("设备运行状态列表")
    private List<DeviceStatusVO> devices;
}
