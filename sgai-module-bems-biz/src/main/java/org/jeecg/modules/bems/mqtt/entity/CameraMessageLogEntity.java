package org.jeecg.modules.bems.mqtt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: MQTT 消息日志（请求 + 应答）
 * @Author: jeecg-boot
 * @Date: 2024
 * @Version: V1.0
 */
@Data
@TableName("mqtt_camera_message_log")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "camera_message_log对象", description = "MQTT消息日志")
public class CameraMessageLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 主题 */
    @Excel(name = "主题", width = 30)
    @ApiModelProperty(value = "MQTT 主题")
    private String topic;

    /** 消息ID */
    @Excel(name = "消息ID", width = 20)
    @ApiModelProperty(value = "消息ID")
    private String msgId;

    /** 报文体 */
    @ApiModelProperty(value = "原始报文体（JSON）")
    private String payload;

    /** 关联的车辆记录ID */
    @Excel(name = "关联车辆记录ID", width = 15)
    @ApiModelProperty(value = "关联的车辆记录ID")
    private Long recordId;

    /** 状态 */
    @Excel(name = "状态", width = 15)
    @ApiModelProperty(value = "状态：ok / param_error / server_error 等")
    private String status;

    /** 方向 */
    @Excel(name = "方向", width = 10)
    @ApiModelProperty(value = "方向：up=相机上行，down=平台下行应答")
    private String direction;

    /** 创建人 */
    @ApiModelProperty(value = "创建人")
    private String createBy;

    /** 创建日期 */
    @ApiModelProperty(value = "创建日期")
    private Date createTime;

    /** 更新人 */
    @ApiModelProperty(value = "更新人")
    private String updateBy;

    /** 更新日期 */
    @ApiModelProperty(value = "更新日期")
    private Date updateTime;

    /** 所属部门 */
    @ApiModelProperty(value = "所属部门")
    private String sysOrgCode;

    public CameraMessageLogEntity() {
        this.createTime = new Date();
        this.updateTime = new Date();
    }
}