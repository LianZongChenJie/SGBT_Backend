package org.jeecg.modules.bems.mqtt.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
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
     * 固定订阅主题（动作）后缀列表，与发布主题一一对应。
     * <p>
     * 主题规则：/设备类型标识/设备标识/订阅主题
     * 其中前两段（设备类型标识、设备标识）动态，最后一段订阅主题固定。
     * 实际订阅时动态生成 /+/+/result 这类通配主题。
     * 例如：result,image_result
     */
    @Value("${mqtt.sub-topics:result,image_result}")
    private List<String> subTopics;

    /**
     * 固定发布主题（应答）后缀列表，与订阅主题一一对应。
     * <p>
     * 主题规则：/设备类型标识/设备标识/发布主题
     * 其中前两段（设备类型标识、设备标识）动态，最后一段发布主题固定。
     * 例如：result_rsp,image_result_rsp
     */
    @Value("${mqtt.pub-topics:result_rsp,image_result_rsp}")
    private List<String> pubTopics;

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

    /**
     * 根据固定订阅主题生成实际订阅过滤器。
     * <p>
     * 前两段（设备类型标识、设备标识）动态，用通配符 + 表示，
     * 最后一段为固定订阅主题。例如固定订阅主题 result 生成 /+/+/result。
     */
    public List<String> buildSubTopicFilters() {
        List<String> filters = new ArrayList<>();
        if (subTopics == null) {
            return filters;
        }
        for (String sub : subTopics) {
            String s = trimToNull(sub);
            if (s != null) {
                filters.add("/+/+/" + s);
            }
        }
        return filters;
    }

    /**
     * 根据订阅主题解析对应的固定发布主题。
     * <p>
     * 订阅主题与发布主题按下标一一对应，找不到时返回 null。
     *
     * @param subAction 订阅主题（动作），如 result、image_result
     * @return 对应的发布主题，如 result_rsp、image_result_rsp；无对应返回 null
     */
    public String resolvePubTopic(String subAction) {
        if (subAction == null || subTopics == null) {
            return null;
        }
        for (int i = 0; i < subTopics.size(); i++) {
            if (subAction.equals(trimToNull(subTopics.get(i)))) {
                if (pubTopics != null && i < pubTopics.size()) {
                    return trimToNull(pubTopics.get(i));
                }
                return null;
            }
        }
        return null;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}