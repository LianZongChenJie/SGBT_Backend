package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 告警列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("告警列表")
public class AlarmListVO {

    @ApiModelProperty("告警记录列表")
    private List<AlarmRecordSimpleVO> records;

    @ApiModelProperty("告警总数")
    private Integer total;

    @ApiModelProperty("告警分类饼图数据")
    private List<AlarmCategoryCountVO> pieData;
}
