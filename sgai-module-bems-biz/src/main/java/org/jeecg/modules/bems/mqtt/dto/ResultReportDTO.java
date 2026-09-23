package org.jeecg.modules.bems.mqtt.dto;


import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ResultReportDTO extends BaseCameraDTO {

    private String type;                 // online / offline
    @JSONField(name = "plate_num")
    private String plateNum;             // 车牌号
    @JSONField(name = "plate_color")
    private String plateColor;           // 车牌底色
    @JSONField(name = "plate_val")
    private Boolean plateVal;            // 是否真牌
    private Integer confidence;          // 置信度 0-28
    @JSONField(name = "car_logo")
    private String carLogo;              // 车辆品牌
    @JSONField(name = "car_color")
    private String carColor;             // 车辆颜色
    @JSONField(name = "vehicle_type")
    private String vehicleType;          // 车辆类型
    @JSONField(name = "utc_ts")
    private Long utcTs;                  // UTC 时间戳
    @JSONField(name = "local_time")
    private String localTime;            // 本地时间
    private String inout;                // in / out
    @JSONField(name = "is_whitelist")
    private Boolean isWhitelist;         // 是否白名单
    @JSONField(name = "trigger_type")
    private String triggerType;          // 触发方式
    @JSONField(name = "full_pic_path")
    private String fullPicPath;
    @JSONField(name = "plate_pic_path")
    private String platePicPath;
    @JSONField(name = "full_pic_len")
    private Integer fullPicLen;
    @JSONField(name = "full_pic")
    private String fullPic;              // base64
    @JSONField(name = "plate_pic_len")
    private Integer platePicLen;
    @JSONField(name = "plate_pic")
    private String platePic;             // base64
    @JSONField(name = "plate_number")
    private Integer plateNumber;
    private Integer speed;
    @JSONField(name = "perHour")
    private Integer perHour;
    @JSONField(name = "assObtType")
    private Integer assObtType;
    private String sn;
    @JSONField(name = "parkingSpaceNum")
    private Integer parkingSpaceNum;
}