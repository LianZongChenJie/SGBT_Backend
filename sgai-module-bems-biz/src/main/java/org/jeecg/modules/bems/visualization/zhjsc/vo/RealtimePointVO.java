package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 能源站实时点位
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("能源站实时点位")
public class RealtimePointVO {

    @ApiModelProperty("点位唯一标识，前端绑定用")
    private String key;

    @ApiModelProperty("点位名称")
    private String name;

    @ApiModelProperty("当前值；开关量为 0/1，模拟量按 scale 格式化后的字符串")
    private String value;

    @ApiModelProperty("单位，无单位为空串")
    private String unit;

    @ApiModelProperty("数据类型，如 BOOL/DOUBLE")
    private String dataType;

    @ApiModelProperty("质量描述，如 好的数据")
    private String quality;

    @ApiModelProperty("质量码，如 GOOD/COMM_FAILURE")
    private String qualityCode;

    @ApiModelProperty("该点最后更新时间（毫秒时间戳），无数据时为 null")
    private Long timestamp;

    @ApiModelProperty("该点最后更新时间 yyyy-MM-dd HH:mm:ss，无数据时为 null")
    private String time;
}
