package org.jeecg.modules.bems.mqtt.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VehicleTypeEnum {
    UNKNOWN("未知大小"),
    LARGE("大型车"),
    MEDIUM("中型车"),
    SMALL("小型车"),
    MOTORCYCLE("摩托车"),
    PEDESTRIAN("行人");

    private final String desc;

    public static VehicleTypeEnum of(String desc) {
        for (VehicleTypeEnum e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return UNKNOWN;
    }
}