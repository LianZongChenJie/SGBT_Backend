package org.jeecg.modules.bems.hikvision.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 事件订阅通知表
 *
 * @author bems
 */
@Data
@Accessors(chain = true)
@TableName("table_event_notify")
public class EventNotify implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件从接收者发出的时间，ISO8601格式 */
    @TableField("send_time")
    private String sendTime;

    /** 事件类别，如：视频事件 */
    @TableField("ability")
    private String ability;

    /** 事件唯一标识 */
    @TableField("event_id")
    private String eventId;

    /** 事件源编号 */
    @TableField("src_index")
    private String srcIndex;

    /** 事件源类型 */
    @TableField("src_type")
    private String srcType;

    /** 事件源名称 */
    @TableField("src_name")
    private String srcName;

    /** 事件类型，数值编码 */
    @TableField("event_type")
    private Integer eventType;

    /** 事件状态：0-瞬时 1-开始 2-停止 */
    @TableField("status")
    private Integer status;

    /** 事件等级：0-未配置 1-低 2-中 3-高 */
    @TableField("event_lvl")
    private Integer eventLvl;

    /** 脉冲超时时间，单位：秒 */
    @TableField("timeout")
    private Integer timeout;

    /** 事件发生时间（设备时间），ISO8601格式 */
    @TableField("happen_time")
    private String happenTime;

    /** 事件源父设备编码 */
    @TableField("src_parent_index")
    private String srcParentIndex;

    /** 事件其它扩展信息，JSON格式存储 */
    @TableField("event_data")
    private String eventData;

    /** 记录创建时间 */
    @TableField("gmt_create")
    private LocalDateTime gmtCreate;

    /** 记录更新时间 */
    @TableField("gmt_modified")
    private LocalDateTime gmtModified;

    /** 事件类型名称（联动table_event_type，非数据库字段） */
    @TableField(exist = false)
    private String eventTypeName;
}
