package org.jeecg.modules.bems.mqtt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TriggerTypeEnum {
    VIDEO("video", "视频触发"),
    HW_TRIGGER("hwtrigger", "地感触发"),
    SW_TRIGGER("swtrigger", "软触发");

    private final String code;
    private final String desc;

    public static TriggerTypeEnum of(String code) {
        for (TriggerTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}