package org.jeecg.modules.bems.mq.send;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.mq.constant.BuildingControlConstant;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 楼控设备控制
 */
@Component
@AllArgsConstructor
@Slf4j
public class BuildingControlSendService {
    public static final MessageProperties messageProperties = MessagePropertiesBuilder.newInstance()
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    private final RabbitTemplate rabbitTemplate;
    /**
     * 发送消息
     * @param path 点位地址
     * @param value 点位值
     */
    public void sendMsg(String path,String value){
        Map<String,String> message = new HashMap<>();
        message.put("path",path);
        message.put("value",value);
        rabbitTemplate.send("", BuildingControlConstant.QUEUE_CONTROL,new Message(JSONObject.toJSONString(message).getBytes(),messageProperties));
    }

}
