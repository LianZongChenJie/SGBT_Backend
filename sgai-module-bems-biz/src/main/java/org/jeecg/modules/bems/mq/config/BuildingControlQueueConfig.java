package org.jeecg.modules.bems.mq.config;

import org.jeecg.modules.bems.mq.constant.BuildingControlConstant;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mq交换机、队列配置
 */
@Configuration
public class BuildingControlQueueConfig {

    /**
     * 楼控点位数据采集
     */
    @Bean
    public Queue bcData(){
        return new Queue(BuildingControlConstant.QUEUE_GATHER,true);
    }

    /**
     * 楼控点位数据控制
     */
    @Bean
    public Queue bcControl(){
    	return new Queue(BuildingControlConstant.QUEUE_CONTROL,true);
    }

}
