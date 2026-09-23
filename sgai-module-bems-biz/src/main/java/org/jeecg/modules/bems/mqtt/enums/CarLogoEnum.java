package org.jeecg.modules.bems.mqtt.enums;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CarLogoEnum {
    TOYOTA("丰田"),
    VOLKSWAGEN("大众"),
    HONDA("本田"),
    PEUGEOT("标志"),
    HYUNDAI("现代"),
    BUICK("别克"),
    AUDI("奥迪"),
    KIA("起亚"),
    JEEP("吉普"),
    FORD("福特"),
    BENZ("奔驰"),
    BMW("宝马"),
    MAZDA("马自达"),
    SUZUKI("铃木"),
    CITROEN("铁雪龙"),
    NISSAN("尼桑"),
    MITSUBISHI("三菱"),
    LEXUS("雷克萨斯"),
    CHEVROLET("雪佛兰"),
    VOLVO("沃尔沃"),
    FIAT("菲亚特"),
    BYD("比亚迪"),
    CHERY("奇瑞"),
    UNKNOWN("未知车标");

    private final String desc;

    public static CarLogoEnum of(String desc) {
        for (CarLogoEnum e : values()) {
            if (e.desc.equals(desc)) return e;
        }
        return UNKNOWN;
    }
}