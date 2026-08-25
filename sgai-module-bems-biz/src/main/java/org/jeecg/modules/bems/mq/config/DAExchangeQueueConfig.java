package org.jeecg.modules.bems.mq.config;

import org.jeecg.modules.bems.mq.constant.DAConstant;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * mq交换机、队列配置——设备属性
 */
@Component
public class DAExchangeQueueConfig {

    /**
     * 设备点位数据变更统计交换机
     */
    @Bean
    public FanoutExchange deviceAttributeExchange(){
        return new FanoutExchange(DAConstant.EXCHANGE, true, false);
    }
}
