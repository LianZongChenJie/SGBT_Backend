package org.jeecg.modules.bems.mdm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 监测点历史值（15 分钟对齐槽位，每点每槽一条，upsert）
 */
@Data
@TableName("monitor_point_history")
@ApiModel(value = "monitor_point_history对象", description = "监测点历史表")
public class MonitorPointHistory {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    @ApiModelProperty(value = "第三方点ID")
    private Long pid;

    @ApiModelProperty(value = "设备类别")
    private String category;

    @ApiModelProperty(value = "采集值")
    private String value;

    @ApiModelProperty(value = "采集时间(15分钟对齐槽位)")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime collectionTime;

}
