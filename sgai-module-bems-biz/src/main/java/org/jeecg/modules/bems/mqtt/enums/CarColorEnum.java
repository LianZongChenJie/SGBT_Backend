package org.jeecg.modules.bems.mqtt.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CarColorEnum {
    UNKNOWN("未知色"),
    BLACK("黑色"),
    WHITE("白色"),
    DARK_RED("深红色"),
    RED("红色"),
    DARK_YELLOW("深黄色"),
    YELLOW("黄色"),
    DARK_GRAY("深灰色"),
    GRAY("灰色"),
    DARK_BLUE("深蓝色"),
    BLUE("蓝色"),
    DARK_GREEN("深绿色"),
    GREEN("绿色"),
    DARK_PINK("深粉色"),
    PINK("粉色"),
    DARK_BROWN("深棕色"),
    BROWN("棕色"),
    DARK_PURPLE("深紫色"),
    PURPLE("紫色");

    private final String desc;

    public static CarColorEnum of(String desc) {
        for (CarColorEnum e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return UNKNOWN;
    }
}