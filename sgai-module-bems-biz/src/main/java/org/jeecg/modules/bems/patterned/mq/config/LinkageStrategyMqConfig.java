package org.jeecg.modules.bems.patterned.mq.config;

import org.jeecg.modules.bems.patterned.mq.constant.LinkageStrategyMqConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class LinkageStrategyMqConfig {

    /**
     * 设备属性数据变更
     */
    @Bean
    public Queue linkageStrategyQueue(){
        return new Queue(LinkageStrategyMqConstant.QUEUE_LINKAGE_STRATEGY_QUEUE,true);
    }

    @Bean
    public Binding bindingExchange(FanoutExchange deviceAttributeExchange){
        return BindingBuilder.bind(linkageStrategyQueue()).to(deviceAttributeExchange);
    }

}
