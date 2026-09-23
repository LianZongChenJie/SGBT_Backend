package org.jeecg.modules.bems.mqtt.dto;


import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

@Data
public class BaseCameraDTO implements Serializable {
    private String cmd;
    @JSONField(name = "msg_id")
    private String msgId;
    @JSONField(name = "device_type")
    private String deviceType;
}