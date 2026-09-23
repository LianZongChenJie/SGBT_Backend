package org.jeecg.modules.bems.mqtt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PlateColorEnum {
    UNKNOWN("未知色"),
    BLUE("蓝色"),
    YELLOW("黄色"),
    WHITE("白色"),
    BLACK("黑色"),
    GREEN("绿色"),
    YELLOW_GREEN("黄绿色");

    private final String desc;

    public static PlateColorEnum of(String desc) {
        for (PlateColorEnum e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return UNKNOWN;
    }
}