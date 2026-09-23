package org.jeecg.modules.bems.mqtt.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RspStatusEnum {
    OK("ok", "成功"),
    PARAM_ERROR("param_error", "参数错误"),
    SERVER_ERROR("server_error", "服务端异常"),
    DUPLICATE("duplicate", "重复消息");

    private final String code;
    private final String desc;
}