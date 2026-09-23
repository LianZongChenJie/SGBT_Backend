package org.jeecg.modules.bems.visualization.ahgl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 按类型统计的告警次数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("按类型统计的告警次数")
public class AlarmsByTypeNumberVO {

    @ApiModelProperty("周期：本周/本月/本年")
    private String type;

    @ApiModelProperty("各告警类别计数")
    private List<AlarmCountVO> data;
}
