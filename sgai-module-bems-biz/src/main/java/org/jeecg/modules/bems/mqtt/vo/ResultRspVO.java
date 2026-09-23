package org.jeecg.modules.bems.mqtt.vo;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jeecg.modules.bems.mqtt.enums.RspStatusEnum;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultRspVO {

    private String cmd = "result_rsp";

    private String status;      // ok / 错误信息

    @JSONField(name = "msg_id")
    private String msgId;

    public static ResultRspVO ok(String msgId) {
        return new ResultRspVO("result_rsp", RspStatusEnum.OK.getCode(), msgId);
    }

    public static ResultRspVO fail(String msgId, String reason) {
        return new ResultRspVO("result_rsp", reason, msgId);
    }
}