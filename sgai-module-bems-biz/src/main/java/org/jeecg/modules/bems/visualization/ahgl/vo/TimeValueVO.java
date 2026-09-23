package org.jeecg.modules.bems.visualization.ahgl.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 时间-数值数据项
 * <p>
 * value 类型可能为 String（原始值）或 Double（按天平均后的数值），保留为 Object 以兼容两种结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("时间-数值数据项")
public class TimeValueVO {

    @ApiModelProperty("采集时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectionTime;

    @ApiModelProperty("数值")
    private Object value;
}
