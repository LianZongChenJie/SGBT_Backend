package org.jeecg.modules.bems.visualization.zhjsc.vo;

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
public class AlarmCategoryCountVO {

    @ApiModelProperty("告警分类名称")
    private String name;

    @ApiModelProperty("数量")
    private Long value;
}
