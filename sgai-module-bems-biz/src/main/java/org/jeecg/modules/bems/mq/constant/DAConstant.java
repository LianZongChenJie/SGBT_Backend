package org.jeecg.modules.bems.mq.constant;

/**
 * mq交换机、队列配置——设备属性
 */
public class DAConstant {

    /**
     * 设备属性变更-通知
     */
    public static final String EXCHANGE = "device_attribute_exchange";

    /**
     * 设备属性信息-告警
     */
    public static final String ALARM_QUEUE = "alarm.instantaneous";

    /**
     * 设备属性信息-联动控制
     */
    public static final String LINKAGE_STRATEGY_QUEUE = "linkage_strategy";

}
