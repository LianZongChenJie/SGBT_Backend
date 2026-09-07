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
 * 监测采集点（第三方实时库采集点静态信息 + 最近一次采集值）
 * <p>
 * 参照 fwbz device_attribute 结构：pid 即第三方点ID(tagid)，category 为设备分类，
 * value/gather_time 由定时采集任务每 15 分钟覆盖更新。
 */
@Data
@TableName("monitor_point")
@ApiModel(value = "monitor_point对象", description = "监测采集点表")
public class MonitorPoint {

    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    @ApiModelProperty(value = "第三方点ID(tagid)")
    private Long pid;

    @ApiModelProperty(value = "设备类别")
    private String category;

    @ApiModelProperty(value = "点类型(psDigital/psAnalog/psNode)")
    private String tagType;

    @ApiModelProperty(value = "点位长名")
    private String longName;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "数据类型(psDataType_Bool/psDataType_Double等)")
    private String dataType;

    @ApiModelProperty(value = "单位")
    private String unit;

    @ApiModelProperty(value = "最新采集值")
    private String value;

    @ApiModelProperty(value = "最新采集时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime gatherTime;

    @ApiModelProperty(value = "在线状态 1在线 0离线")
    private Integer online;

}
