package org.jeecg.modules.bems.mqtt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
@ConfigurationProperties(prefix = "bems.mqtt")
public class MqttConfig {

    /** Broker 地址 */
    private String broker = "tcp://47.95.156.86:61883";

    private String username = "admin";

    private String password = "admin@123";

    /** 客户端ID前缀 */
    private String clientIdPrefix = "CloudPlatform_";

    /** 连接超时（秒） */
    private int connectionTimeout = 10;

    /** 心跳间隔（秒） */
    private int keepAliveInterval = 20;

    /** 是否自动重连 */
    private boolean automaticReconnect = true;

    /** 是否清除会话 */
    private boolean cleanSession = true;

    /**
     * 订阅主题列表，支持通配符，默认全动态：+/+/+
     * 例如：
     *   - +/+/+
     *   - camera/+/+
     *   - camera/+/result
     */
    private List<String> subTopics = Arrays.asList("/+/+/+");

    /**
     * 发布主题模板，支持占位符：
     *   {deviceType} 设备类型标识
     *   {deviceId}   设备标识
     *   {action}     订阅主题
     * 也兼容旧写法 %s（等价于 {deviceId}）
     * <p>
     * 若以 /*&#47; 开头，则最终 topic 只有发布主题这一段。
     */
    private String pubTopicRsp = "/{deviceType}/{deviceId}/{action}_rsp";

    /** 订阅 QoS */
    private int subQos = 1;

    /** 发布 QoS */
    private int pubQos = 1;
}