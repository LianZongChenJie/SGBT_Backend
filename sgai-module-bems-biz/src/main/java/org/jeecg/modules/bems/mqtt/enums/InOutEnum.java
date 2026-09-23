package org.jeecg.modules.bems.mqtt.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum InOutEnum {
    IN("in", "入口"),
    OUT("out", "出口");

    private final String code;
    private final String desc;

    public static InOutEnum of(String code) {
        for (InOutEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }
}