package org.jeecg.modules.bems.patterned.mq.listener;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.patterned.mq.constant.LinkageStrategyMqConstant;
import org.jeecg.modules.bems.patterned.service.ILinkageStrategyService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 联动策略执行监听器
 */
@Component
@Slf4j
@AllArgsConstructor
public class LinkageStrategyListener {

    private final ILinkageStrategyService linkageStrategyService;

    @RabbitListener(queues = LinkageStrategyMqConstant.QUEUE_LINKAGE_STRATEGY_QUEUE)
    public void listener(Message message){
        String body = new String(message.getBody());
        try {
            log.info("收到联动策略执行消息：{}", body);
            JSONObject msg = JSONObject.parseObject(body);
            Long deviceId = msg.getLong("deviceId");
            Long pointId = msg.getLong("pointId");
            String value = msg.getString("value");
            linkageStrategyService.detection(deviceId,pointId,value);
        }catch (Exception e){
            log.error("联动策略执行监听器异常：{}",e.getMessage());
        }

    }
}
