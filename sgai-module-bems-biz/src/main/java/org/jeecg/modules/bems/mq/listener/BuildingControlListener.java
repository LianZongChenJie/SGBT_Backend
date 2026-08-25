package org.jeecg.modules.bems.mq.listener;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.annotation.RabbitComponent;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mq.constant.BuildingControlConstant;
import org.jeecg.modules.bems.mq.constant.MqConstant;
import org.jeecg.modules.bems.mq.dto.BuildingControlDataMsg;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.time.LocalDateTime;

@RabbitComponent(value = "BuildingControlListener")
@Slf4j
@AllArgsConstructor
public class BuildingControlListener {

    private final IDeviceAttributeService deviceAttributeService;

    @RabbitListener(queues = BuildingControlConstant.QUEUE_GATHER, ackMode = "AUTO")
    @RabbitHandler
    public void listener(Message message){
        String body = new String(message.getBody());
        try {
            BuildingControlDataMsg data = JSONObject.parseObject(body, BuildingControlDataMsg.class);
            if(StringUtils.isEmpty(data.getKey()) || StringUtils.isEmpty(data.getValue())){
                log.error("mq消息消费失败。queue:{}，message:{}", MqConstant.QUEUE_DEVICE_RUN_STATUS_CHANGE, body);
                return;
            }
            deviceAttributeService.updateAttributeValue(data.getKey(),data.getValue(),data.getTime() == null ? LocalDateTime.now() : data.getTime());
        }catch (Exception e){
            log.error("mq消息消费失败。queue:{}，message:{}", MqConstant.QUEUE_DEVICE_RUN_STATUS_CHANGE, body, e);
        }
    }
}
