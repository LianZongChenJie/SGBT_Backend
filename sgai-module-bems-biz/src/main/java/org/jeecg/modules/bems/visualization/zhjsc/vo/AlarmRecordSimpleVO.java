package org.jeecg.modules.bems.visualization.zhjsc.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警记录（精简版）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("告警记录（精简版）")
public class AlarmRecordSimpleVO {

    @ApiModelProperty("设备名称")
    private String deviceName;

    @ApiModelProperty("告警时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime alarmTime;

    @ApiModelProperty("告警类别名称")
    private String alarmCategoryName;
}
