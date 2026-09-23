package org.jeecg.modules.bems.mqtt.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CmdEnum {
    RESULT("result", "结果上报"),
    IMAGE_RESULT("image_result", "图片结果上报"),
    RESULT_RSP("result_rsp", "结果上报应答");

    private final String code;
    private final String desc;

    public static CmdEnum of(String code) {
        for (CmdEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}