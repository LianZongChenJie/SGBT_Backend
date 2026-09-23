package org.jeecg.modules.bems.visualization.ahgl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 环境监测曲线图（6 组折线）单条数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("环境监测曲线项")
public class DividedIntoSixVO {

    @ApiModelProperty("监测项名称（二氧化碳浓度/粉尘浓度）")
    private String itemName;

    @ApiModelProperty("各设备数据")
    private List<DeviceDataVO> data;
}
