package org.jeecg.modules.bems.mqtt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AssObtTypeEnum {
    UNKNOWN(0, "未知"),
    CAR_HEAD(1, "车头"),
    PEDESTRIAN(2, "行人"),
    MOTORCYCLE(3, "摩托车"),
    TRICYCLE(4, "三轮车"),
    CAR_TAIL(5, "车尾");

    private final Integer code;
    private final String desc;

    public static AssObtTypeEnum of(Integer code) {
        if (code == null) return UNKNOWN;
        for (AssObtTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return UNKNOWN;
    }
}