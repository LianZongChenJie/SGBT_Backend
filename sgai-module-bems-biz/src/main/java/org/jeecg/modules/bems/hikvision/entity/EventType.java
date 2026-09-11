package org.jeecg.modules.bems.hikvision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 海康事件类型字典表
 *
 * @author bems
 */
@Data
@TableName("table_event_type")
public class EventType implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件类型名称，如：区域入侵、越界侦测等 */
    @TableField("event_type")
    private String eventType;

    /** 事件编码，对应事件通知表event_type字段 */
    @TableField("event_code")
    private Integer eventCode;
}
