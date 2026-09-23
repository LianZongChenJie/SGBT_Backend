package org.jeecg.modules.bems.visualization.ahgl.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警分类计数
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("告警分类计数")
public class AlarmCountVO {

    @ApiModelProperty("告警类别 ID")
    private Long alarmCategoryId;

    @ApiModelProperty("告警类别名称")
    private String alarmCategoryName;

    @ApiModelProperty("数量")
    private Long count;
}
