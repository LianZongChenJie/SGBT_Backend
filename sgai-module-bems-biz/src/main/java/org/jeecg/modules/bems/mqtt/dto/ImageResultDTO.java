package org.jeecg.modules.bems.mqtt.dto;


import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ImageResultDTO extends BaseCameraDTO {

    @JSONField(name = "utc_ts")
    private Long utcTs;
    @JSONField(name = "full_pic_len")
    private Integer fullPicLen;
    @JSONField(name = "full_pic")
    private String fullPic;
    @JSONField(name = "plate_pic_len")
    private Integer platePicLen;
    @JSONField(name = "plate_pic")
    private String platePic;
}