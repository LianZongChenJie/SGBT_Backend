package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 蒸汽/电能产量
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("蒸汽/电能产量")
public class SteamElectricityProductionVO {

    @ApiModelProperty("类型：蒸汽产量 / 电能产量")
    private String type;

    @ApiModelProperty("各设备产量数据")
    private List<ProductionItemVO> data;
}
