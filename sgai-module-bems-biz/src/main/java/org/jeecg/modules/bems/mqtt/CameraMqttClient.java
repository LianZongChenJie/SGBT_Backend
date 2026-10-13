package org.jeecg.modules.bems.mqtt;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jeecg.modules.bems.mqtt.config.MqttConfig;
import org.jeecg.modules.bems.mqtt.entity.CameraMessageLogEntity;
import org.jeecg.modules.bems.mqtt.handler.DeviceMessageRouter;
import org.jeecg.modules.bems.mqtt.service.CameraMessageLogService;
import org.jeecg.modules.bems.mqtt.vo.HandleResult;
import org.jeecg.modules.bems.mqtt.vo.ResultRspVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class CameraMqttClient {

    private final MqttConfig mqttConfig;
    private final DeviceMessageRouter router;
    private final CameraMessageLogService cameraMessageLogService;

    private MqttClient client;

    @Autowired
    public CameraMqttClient(MqttConfig mqttConfig,
                            DeviceMessageRouter router,
                            CameraMessageLogService cameraMessageLogService) {
        this.mqttConfig = mqttConfig;
        this.router = router;
        this.cameraMessageLogService = cameraMessageLogService;
    }

    @PostConstruct
    public void start() throws MqttException {
        log.info("正在启动 MQTT 客户端...");
        String clientId = mqttConfig.getClientIdPrefix() + System.currentTimeMillis();
        client = new MqttClient(mqttConfig.getBroker(), clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(mqttConfig.getUsername());
        options.setPassword(mqttConfig.getPassword().toCharArray());
        options.setCleanSession(mqttConfig.isCleanSession());
        options.setConnectionTimeout(mqttConfig.getConnectionTimeout());
        options.setKeepAliveInterval(mqttConfig.getKeepAliveInterval());
        options.setAutomaticReconnect(mqttConfig.isAutomaticReconnect());

        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                log.info("连接完成 reconnect={}, server={}", reconnect, serverURI);
                // 订阅所有配置的 topic
                subscribeAll();
            }

            @Override
            public void connectionLost(Throwable cause) {
                log.warn("连接丢失: {}", cause == null ? "unknown" : cause.getMessage());
            }

            // 发布
            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                // 过滤应答类 topic，避免自订阅循环
                if (topic != null && topic.endsWith("_rsp")) {
                    log.debug("忽略应答消息 topic={}", topic);
                    return;
                }

                // 过滤 payload 为 null 的情况
                if (payload == null || payload.trim().isEmpty() || "null".equals(payload.trim())) {
                    log.warn("忽略空报文 topic={}", topic);
                    return;
                }

                log.info("收到消息 topic={}, payload={}", topic, payload);
                try {
                    HandleResult result = router.route(topic, payload);

                    // 无应答内容（如应答类消息、无需回应）直接跳过
                    if (result == null || result.getRsp() == null) {
                        log.debug("无需应答，跳过 topic={}", topic);
                        return;
                    }
                    // 设定应答报文
                    ResultRspVO rsp = result.getRsp();
                    String rspTopic = buildRspTopic(result);
                    if (rspTopic == null) {
                        log.warn("无法确定应答主题，跳过发布。msgId={}, status={}",
                                rsp.getMsgId(), rsp.getStatus());
                        return;
                    }

                    String rspJson = JSON.toJSONString(rsp);
                    MqttMessage rspMsg = new MqttMessage(rspJson.getBytes(StandardCharsets.UTF_8));
                    rspMsg.setQos(mqttConfig.getPubQos());
                    client.publish(rspTopic, rspMsg);
                    log.info("应答已发送 topic={}, rsp={}", rspTopic, rspJson);

                    saveRspLog(rspTopic, rspJson, rsp.getMsgId(), rsp.getStatus());
                } catch (Exception e) {
                    log.error("处理消息失败 topic={}", topic, e);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // 应答投递完成
            }
        });

        log.info("正在连接 {} ...", mqttConfig.getBroker());
        client.connect(options);
        log.info("连接成功, clientId={}", client.getClientId());
    }

    /**
     * 订阅所有配置的 topic
     */
    private void subscribeAll() {
        try {
            List<String> topics = mqttConfig.buildSubTopicFilters();
            if (topics == null || topics.isEmpty()) {
                log.warn("未配置订阅主题，跳过订阅");
                return;
            }
            String[] topicArr = topics.toArray(new String[0]);
            int[] qosArr = new int[topicArr.length];
            Arrays.fill(qosArr, mqttConfig.getSubQos());
            client.subscribe(topicArr, qosArr);
            log.info("订阅成功: {}", topics);
        } catch (MqttException e) {
            log.error("订阅失败", e);
        }
    }

    /**
     * 构建应答发布 topic
     * <p>
     * 规则：/设备类型标识/设备标识/发布主题
     * 其中设备类型标识、设备标识来自路由信息（动态），发布主题根据订阅主题
     * 从固定发布主题列表中解析（如 result → result_rsp）。
     * 路由信息缺失或订阅主题无对应发布主题时返回 null，不发布。
     */
    private String buildRspTopic(HandleResult result) {
        if (!result.hasRouteInfo()) {
            log.warn("发布主题需要设备信息（设备类型标识/设备标识），但路由信息缺失");
            return null;
        }

        String subAction = result.getAction();
        if (isBlank(subAction)) {
            log.warn("缺少订阅主题（动作），无法确定发布主题");
            return null;
        }

        String pubTopic = mqttConfig.resolvePubTopic(subAction);
        if (pubTopic == null) {
            log.warn("未配置订阅主题 [{}] 对应的发布主题", subAction);
            return null;
        }

        return "/" + result.getDeviceType() + "/" + result.getDeviceId() + "/" + pubTopic;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * 保存应答日志
     */
    private void saveRspLog(String topic, String payload, String msgId, String status) {
        try {
            CameraMessageLogEntity logEntity = new CameraMessageLogEntity();
            logEntity.setTopic(topic);
            logEntity.setMsgId(msgId);
            logEntity.setPayload(payload);
            logEntity.setStatus(status);
            logEntity.setDirection("down");
            cameraMessageLogService.save(logEntity);
        } catch (Exception e) {
            log.error("保存应答日志失败", e);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                client.close();
                log.info("已断开连接");
            }
        } catch (MqttException e) {
            log.error("关闭异常", e);
        }
    }
}