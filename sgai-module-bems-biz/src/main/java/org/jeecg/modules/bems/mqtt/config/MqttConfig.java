package org.jeecg.modules.bems.mqtt.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * MQTT 配置类
 * <p>
 * 优先从 application.yml / application.properties 读取，
 * 未配置时使用默认值（方便本地调试）。
 */
@Data
@Configuration
public class MqttConfig {

    /** Broker 地址 */
    @Value("${mqtt.broker:tcp://47.95.156.86:61883}")
    private String broker;

    /** 账号 */
    @Value("${mqtt.username:admin}")
    private String username;

    /** 密码 */
    @Value("${mqtt.password:admin@123}")
    private String password;

    /** 客户端ID前缀 */
    @Value("${mqtt.client-id-prefix:CloudPlatform_}")
    private String clientIdPrefix;

    /**
     * 订阅主题列表，支持通配符，默认全动态：+/+/+
     * 例如：
     *   - +/+/+
     *   - camera/+/+
     *   - camera/+/result
     */
    @Value("${mqtt.sub-topics:/+/+/+}")
    private List<String> subTopics;

    /**
     * 发布主题模板，支持占位符：
     *   {deviceType} 设备类型标识
     *   {deviceId}   设备标识
     *   {action}     订阅主题
     * 也兼容旧写法 %s（等价于 {deviceId}）
     * <p>
     * 若以 /*&#47; 开头，则最终 topic 只有发布主题这一段。
     */
    @Value("${mqtt.pub-topic-rsp:/{deviceType}/{deviceId}/{action}_rsp}")
    private String pubTopicRsp;

    /** 连接超时（秒） */
    @Value("${mqtt.connection-timeout:10}")
    private int connectionTimeout;

    /** 心跳间隔（秒） */
    @Value("${mqtt.keep-alive-interval:20}")
    private int keepAliveInterval;

    /** 是否自动重连 */
    @Value("${mqtt.automatic-reconnect:true}")
    private boolean automaticReconnect;

    /** 是否清除会话 */
    @Value("${mqtt.clean-session:true}")
    private boolean cleanSession;

    /** 订阅 QoS */
    @Value("${mqtt.sub-qos:1}")
    private int subQos;

    /** 发布 QoS */
    @Value("${mqtt.pub-qos:1}")
    private int pubQos;
}