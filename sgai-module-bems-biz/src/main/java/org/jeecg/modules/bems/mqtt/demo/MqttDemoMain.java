package org.jeecg.modules.bems.mqtt.demo;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * MQTT 链路验证 Demo（独立 main，无需 Spring）
 * <p>
 * 功能：
 * 1. 连接 Broker（模拟相机）
 * 2. 订阅自己的应答主题：camera/{sn}/result_rsp、camera/{sn}/image_result_rsp
 * 3. 场景一：一起上传模式（result 带图片）
 * 4. 场景二：分开上传模式（result + image_result）
 * 5. 打印收到的所有应答
 * <p>
 * 直接右键运行 main 即可。
 */
public class MqttDemoMain {

    // ============ 与 MqttConfig 保持一致 ============
    private static final String BROKER = "tcp://47.95.156.86:61883";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin@123";

    // 模拟的设备信息
    private static final String DEVICE_TYPE = "camera";
    private static final String DEVICE_ID = "CAM-DEMO-001";

    private static MqttClient simClient;

    // 用于等待应答
    private static final CountDownLatch RESULT_LATCH = new CountDownLatch(1);
    private static final CountDownLatch IMAGE_RESULT_LATCH = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        System.out.println("========== MQTT 验证 Demo 启动 ==========");

        // 1. 连接
        connect();

        // 2. 订阅应答主题
        subscribeRsp();

        // 3. 场景一：一起上传模式（result 带图片）
        testInlineImage();

        // 4. 场景二：分开上传模式（result + image_result）
        testSplitImage();

        // 5. 等待应答
        System.out.println("\n等待平台应答...");
        boolean ok1 = RESULT_LATCH.await(1, TimeUnit.SECONDS);
        boolean ok2 = IMAGE_RESULT_LATCH.await(1, TimeUnit.SECONDS);

        System.out.println("\n========== 验证结果 ==========");
        System.out.println("result_rsp       收到: " + ok1);
        System.out.println("image_result_rsp 收到: " + ok2);

        // 6. 关闭
        disconnect();
        System.exit((ok1 && ok2) ? 0 : 1);
    }

    // ---------------- 连接 ----------------
    private static void connect() throws MqttException {
        String clientId = "SimCamera_" + System.currentTimeMillis();
        simClient = new MqttClient(BROKER, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(USERNAME);
        options.setPassword(PASSWORD.toCharArray());
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);
        options.setAutomaticReconnect(true);

        simClient.connect(options);
        System.out.println("[连接成功] clientId=" + clientId);
        System.out.println("[Broker] " + BROKER);
    }

    // ---------------- 订阅应答 ----------------
    private static void subscribeRsp() throws MqttException {
        String rspTopic1 = "/" + DEVICE_TYPE + "/" + DEVICE_ID + "/result_rsp";
        String rspTopic2 = "/" + DEVICE_TYPE + "/" + DEVICE_ID + "/image_result_rsp";

        simClient.subscribe(rspTopic1, 1, MqttDemoMain::handleRsp);
        simClient.subscribe(rspTopic2, 1, MqttDemoMain::handleRsp);

        System.out.println("[订阅成功] " + rspTopic1 + " , " + rspTopic2);
    }

    // ---------------- 应答统一处理 ----------------
    private static void handleRsp(String topic, MqttMessage message) {
        String body = new String(message.getPayload(), StandardCharsets.UTF_8);
        System.out.println("\n[收到应答] topic=" + topic);
        System.out.println("           body=" + body);

        JSONObject json = JSON.parseObject(body);
        String status = json.getString("status");
        String msgId = json.getString("msg_id");

        if ("ok".equals(status)) {
            if (msgId != null && msgId.contains("image-split")) {
                IMAGE_RESULT_LATCH.countDown();
            } else {
                RESULT_LATCH.countDown();
            }
        }
    }

    // ---------------- 场景一：一起上传 ----------------
    private static void testInlineImage() throws MqttException {
        System.out.println("\n---------- 场景一：一起上传模式（result 带图片） ----------");
        String msgId = "demo-inline-" + System.currentTimeMillis();

        Map<String, Object> payload = new HashMap<>();
        payload.put("cmd", "result");
        payload.put("msg_id", msgId);
        payload.put("device_type", DEVICE_TYPE);
        payload.put("sn", DEVICE_ID);
        payload.put("type", "online");
        payload.put("plate_num", "京A88888");
        payload.put("plate_color", "蓝色");
        payload.put("plate_val", true);
        payload.put("confidence", 26);
        payload.put("car_logo", "宝马");
        payload.put("car_color", "白色");
        payload.put("vehicle_type", "小型车");
        payload.put("utc_ts", System.currentTimeMillis());
        payload.put("local_time", "2024-06-20 15:30:00");
        payload.put("inout", "in");
        payload.put("is_whitelist", true);
        payload.put("trigger_type", "video");
        payload.put("plate_number", 1);
        payload.put("speed", 100);
        payload.put("perHour", 25);
        payload.put("assObtType", 1);
        // 一起上传模式：协议要求必填
        payload.put("parkingSpaceNum", 1);
        // 一起上传的图片（base64 片段，仅示意）
        payload.put("full_pic_len", 8);
        payload.put("full_pic", "iVBORw0KGgo=");
        payload.put("plate_pic_len", 4);
        payload.put("plate_pic", "AAAA");

        publish("result", payload);
    }

    // ---------------- 场景二：分开上传 ----------------
    private static void testSplitImage() throws Exception {
        System.out.println("\n---------- 场景二：分开上传模式（result + image_result） ----------");
        long utcTs = System.currentTimeMillis();
        String resultMsgId = "demo-result-split-" + utcTs;
        String imageMsgId = "demo-image-split-" + utcTs;

        // 1. 先发 result（不带图片，但必填字段全部补齐）
        Map<String, Object> resultPayload = new HashMap<>();
        resultPayload.put("cmd", "result");
        resultPayload.put("msg_id", resultMsgId);
        resultPayload.put("device_type", DEVICE_TYPE);
        resultPayload.put("sn", DEVICE_ID);
        resultPayload.put("type", "online");
        resultPayload.put("plate_num", "沪B66666");
        resultPayload.put("plate_color", "黄色");
        resultPayload.put("plate_val", true);
        resultPayload.put("confidence", 20);
        resultPayload.put("car_logo", "大众");
        resultPayload.put("car_color", "黑色");
        resultPayload.put("vehicle_type", "小型车");
        resultPayload.put("utc_ts", utcTs);
        // 必填：本地时间
        resultPayload.put("local_time", "2024-06-20 15:31:00");
        resultPayload.put("inout", "out");
        resultPayload.put("is_whitelist", false);
        // 必填：触发方式
        resultPayload.put("trigger_type", "hwtrigger");
        // 必填：车牌序号
        resultPayload.put("plate_number", 2);
        // 必填：车位编号
        resultPayload.put("parkingSpaceNum", 2);
        resultPayload.put("speed", 120);
        resultPayload.put("perHour", 30);
        resultPayload.put("assObtType", 5);
        // 分开上传：先给路径，图片稍后发
        resultPayload.put("full_pic_path", "/img/full/split.jpg");
        resultPayload.put("plate_pic_path", "/img/plate/split.jpg");

        publish("result", resultPayload);

        // 2. 等一会儿，确保平台先入库
        Thread.sleep(1000);

        // 3. 再发 image_result（使用同一个 utc_ts 关联）
        Map<String, Object> imagePayload = new HashMap<>();
        imagePayload.put("cmd", "image_result");
        imagePayload.put("msg_id", imageMsgId);
        imagePayload.put("device_type", DEVICE_TYPE);
        // 必填：sn
        imagePayload.put("sn", DEVICE_ID);
        imagePayload.put("utc_ts", utcTs);
        imagePayload.put("full_pic_len", 8);
        imagePayload.put("full_pic", "iVBORw0KGgo=");
        imagePayload.put("plate_pic_len", 4);
        imagePayload.put("plate_pic", "BBBB");

        publish("image_result", imagePayload);
    }

    // ---------------- 发布 ----------------
    private static void publish(String action, Object payload) throws MqttException {
        String topic = "/" + DEVICE_TYPE + "/" + DEVICE_ID + "/" + action;
        String json = JSON.toJSONString(payload);

        MqttMessage msg = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        msg.setQos(1);
        simClient.publish(topic, msg);

        System.out.println("\n[发送] topic=" + topic);
        System.out.println("       body=" + json);
    }

    // ---------------- 关闭 ----------------
    private static void disconnect() {
        try {
            if (simClient != null && simClient.isConnected()) {
                simClient.disconnect();
                simClient.close();
                System.out.println("\n[断开连接]");
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}