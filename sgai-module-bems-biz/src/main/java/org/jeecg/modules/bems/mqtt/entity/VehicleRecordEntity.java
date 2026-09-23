package org.jeecg.modules.bems.mqtt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.aspect.annotation.Dict;
import org.jeecg.modules.bems.mqtt.dto.ResultReportDTO;
import org.jeecgframework.poi.excel.annotation.Excel;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: 车辆识别记录
 * @Author: jeecg-boot
 * @Date: 2024
 * @Version: V1.0
 */
@Data
@TableName("mqtt_camera_vehicle_record")
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "camera_vehicle_record对象", description = "车辆识别记录")
public class VehicleRecordEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 命令：result / image_result */
    @Excel(name = "命令", width = 15)
    @ApiModelProperty(value = "命令：result=结果上报，image_result=图片结果上报")
    private String cmd;

    /** 结果上报消息ID */
    @Excel(name = "结果上报消息ID", width = 15)
    @ApiModelProperty(value = "结果上报消息ID")
    private String msgId;

    /** 图片消息ID */
    @Excel(name = "图片消息ID", width = 15)
    @ApiModelProperty(value = "image_result 的消息ID")
    private String imageMsgId;

    /** 结果类型：online/offline */
    @Excel(name = "结果类型", width = 15, dicCode = "result_type")
    @Dict(dicCode = "result_type")
    @ApiModelProperty(value = "结果类型：online=正常在线传输，offline=断网续传")
    private String type;

    /** 车牌号 */
    @Excel(name = "车牌号", width = 15)
    @ApiModelProperty(value = "车牌号码，无牌车为 null 字符串")
    private String plateNum;

    /** 车牌底色 */
    @Excel(name = "车牌底色", width = 15)
    @ApiModelProperty(value = "车牌底色：未知色/蓝色/黄色/白色/黑色/绿色/黄绿色")
    private String plateColor;

    /** 是否真牌 */
    @Excel(name = "是否真牌", width = 15)
    @ApiModelProperty(value = "是否真牌：true=真牌，false=虚假车牌")
    private Boolean plateVal;

    /** 置信度 */
    @Excel(name = "置信度", width = 15)
    @ApiModelProperty(value = "置信度，范围：0-28")
    private Integer confidence;

    /** 车辆品牌 */
    @Excel(name = "车辆品牌", width = 15)
    @ApiModelProperty(value = "车辆品牌，如：丰田、大众等")
    private String carLogo;

    /** 车辆颜色 */
    @Excel(name = "车辆颜色", width = 15)
    @ApiModelProperty(value = "车辆颜色，如：黑色、白色等")
    private String carColor;

    /** 车辆类型 */
    @Excel(name = "车辆类型", width = 15)
    @ApiModelProperty(value = "车辆类型：未知大小/大型车/中型车/小型车/摩托车/行人")
    private String vehicleType;

    /** UTC 时间戳 */
    @Excel(name = "UTC时间戳", width = 15)
    @ApiModelProperty(value = "识别上传时的 UTC 时间戳")
    private Long utcTs;

    /** 本地时间 */
    @Excel(name = "本地时间", width = 20)
    @ApiModelProperty(value = "识别上传时的本地时间")
    private String localTime;

    /** 出入口类型 */
    @Excel(name = "出入口类型", width = 15, dicCode = "in_out")
    @Dict(dicCode = "in_out")
    @TableField(value = "`inout`")
    @ApiModelProperty(value = "出入口类型：in=入口，out=出口")
    private String inout;

    @ApiModelProperty(value = "设备类型标识，如 camera / gate / sensor")
    private String deviceType;

    /** 是否白名单 */
    @Excel(name = "是否白名单", width = 15)
    @ApiModelProperty(value = "是否白名单：true=白名单，false=非白名单")
    private Boolean isWhitelist;

    /** 触发方式 */
    @Excel(name = "触发方式", width = 15)
    @ApiModelProperty(value = "触发方式：video=视频触发，hwtrigger=地感触发，swtrigger=软触发")
    private String triggerType;

    /** 关联目标类型 */
    @Excel(name = "关联目标类型", width = 15)
    @ApiModelProperty(value = "关联目标类型：0=未知 1=车头 2=行人 3=摩托车 4=三轮车 5=车尾")
    private Integer assObtType;

    /** 全景图路径 */
    @Excel(name = "全景图路径", width = 30)
    @ApiModelProperty(value = "全景图路径")
    private String fullPicPath;

    /** 车牌特写图路径 */
    @Excel(name = "车牌图路径", width = 30)
    @ApiModelProperty(value = "车牌特写图路径")
    private String platePicPath;

    /** 全景图 base64 */
    @ApiModelProperty(value = "全景图 base64 数据")
    private String fullPic;

    /** 车牌图 base64 */
    @ApiModelProperty(value = "车牌特写图 base64 数据")
    private String platePic;

    /** 全景图数据长度 */
    @Excel(name = "全景图长度", width = 15)
    @ApiModelProperty(value = "全景图数据长度")
    private Integer fullPicLen;

    /** 车牌图数据长度 */
    @Excel(name = "车牌图长度", width = 15)
    @ApiModelProperty(value = "车牌特写图数据长度")
    private Integer platePicLen;

    /** 图片是否已上传 */
    @Excel(name = "图片是否已上传", width = 15)
    @ApiModelProperty(value = "图片是否已上传：一起上传=true，分开上传时初始为 false")
    private Boolean imageUploaded;

    /** 车牌序号 */
    @Excel(name = "车牌序号", width = 15)
    @ApiModelProperty(value = "车牌序号，相机启动后从 1 开始计数")
    private Integer plateNumber;

    /** 算法识别速度 */
    @Excel(name = "算法识别速度", width = 15)
    @ApiModelProperty(value = "算法识别速度，单位 ms")
    private Integer speed;

    /** 雷达测试速度 */
    @Excel(name = "雷达测试速度", width = 15)
    @ApiModelProperty(value = "雷达测试速度，单位 km/h")
    private Integer perHour;

    /** 相机 SN */
    @Excel(name = "相机SN", width = 20)
    @ApiModelProperty(value = "相机 SN 码")
    private String sn;

    /** 车位编号 */
    @Excel(name = "车位编号", width = 15)
    @ApiModelProperty(value = "车位编号，取值[1-2]（充电桩相机使用）")
    private Integer parkingSpaceNum;

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

    /**
     * DTO -> Entity 转换（完整版）
     * <p>
     * 说明：
     * 1. 基础字段、图片路径、图片 base64、长度等全部拷贝；
     * 2. 根据是否携带内联图片自动判定 imageUploaded；
     * 3. cmd 由调用方（handler）显式设置，此处不覆盖，保留 DTO 中若有则使用。
     */
    public static VehicleRecordEntity fromDTO(ResultReportDTO dto) {
        VehicleRecordEntity e = new VehicleRecordEntity();

        // 基础标识
        e.setCmd(dto.getCmd());
        e.setMsgId(dto.getMsgId());

        // 结果类型与车辆信息
        e.setType(dto.getType());
        e.setPlateNum(dto.getPlateNum());
        e.setPlateColor(dto.getPlateColor());
        e.setPlateVal(dto.getPlateVal());
        e.setConfidence(dto.getConfidence());
        e.setCarLogo(dto.getCarLogo());
        e.setCarColor(dto.getCarColor());
        e.setVehicleType(dto.getVehicleType());

        // 时间与出入口
        e.setUtcTs(dto.getUtcTs());
        e.setLocalTime(dto.getLocalTime());
        e.setInout(dto.getInout());

        // 白名单与触发
        e.setIsWhitelist(dto.getIsWhitelist());
        e.setTriggerType(dto.getTriggerType());
        e.setAssObtType(dto.getAssObtType());

        // 图片路径
        e.setFullPicPath(dto.getFullPicPath());
        e.setPlatePicPath(dto.getPlatePicPath());

        // 图片 base64 与长度（一起上传模式）
        e.setFullPic(dto.getFullPic());
        e.setPlatePic(dto.getPlatePic());
        e.setFullPicLen(dto.getFullPicLen());
        e.setPlatePicLen(dto.getPlatePicLen());

        // 其它业务字段
        e.setPlateNumber(dto.getPlateNumber());
        e.setSpeed(dto.getSpeed());
        e.setPerHour(dto.getPerHour());
        e.setSn(dto.getSn());
        e.setParkingSpaceNum(dto.getParkingSpaceNum());

        // 图片是否已上传：只要携带任一内联图片即视为已上传
        boolean inlineImage = (dto.getFullPic() != null && !dto.getFullPic().isEmpty())
                || (dto.getPlatePic() != null && !dto.getPlatePic().isEmpty());
        e.setImageUploaded(inlineImage);

        e.setCreateTime(new Date());
        e.setUpdateTime(new Date());
        return e;
    }
}