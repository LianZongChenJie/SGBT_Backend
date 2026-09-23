package org.jeecg.modules.bems.mqtt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultTypeEnum {
    ONLINE("online", "正常在线传输"),
    OFFLINE("offline", "断网续传");

    private final String code;
    private final String desc;

    public static ResultTypeEnum of(String code) {
        for (ResultTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}