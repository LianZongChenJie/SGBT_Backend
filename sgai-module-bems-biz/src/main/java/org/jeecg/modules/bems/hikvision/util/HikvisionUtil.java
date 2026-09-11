package org.jeecg.modules.bems.hikvision.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hikvision.artemis.sdk.ArtemisHttpUtil;
import com.hikvision.artemis.sdk.Response;
import com.hikvision.artemis.sdk.config.ArtemisConfig;
import com.hikvision.artemis.sdk.constant.Constants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 海康威视OpenAPI接口请求工具类
 * 基于海康Artemis HTTP Client SDK，封装常用的API调用方式
 *
 * <p>使用前请在配置中心（Nacos）或application.yml中配置以下参数：</p>
 * <pre>
 * hikvision:
 *   host: 127.0.0.1:443        # 平台nginx所在ip及https端口（或artemis服务ip:9016）
 *   app-key: 20469790           # 合作方Key
 *   app-secret: your-secret     # 合作方Secret
 *   connect-timeout: 10000      # 连接超时时间(ms)，默认10000
 *   socket-timeout: 60000       # 读取超时时间(ms)，默认60000
 * </pre>
 *
 * @author bems
 */
@Slf4j
@Data
@Component
public class HikvisionUtil {

    /**
     * API网关的后端服务上下文
     */
    private static final String ARTEMIS_PATH = "/artemis";

    /**
     * 平台地址，格式为 IP:Port
     * 使用https协议时填nginx的IP:Port，使用http协议时填artemis服务的IP:Port（默认9016）
     */

    private String host="192.168.1.5:443";

    /**
     * 合作方Key
     */

    private String appKey="25004761";

    /**
     * 合作方Secret
     */

    private String appSecret="WDu3QiTAeYiOPgATDkDn";

    /**
     * 连接超时时间(ms)
     */

    private int connectTimeout=10000;

    /**
     * 读取超时时间(ms)
     */

    private int socketTimeout=60000;

    /**
     * 初始化超时配置
     */
    public void init() {
        Constants.DEFAULT_TIMEOUT = connectTimeout;
        Constants.SOCKET_TIMEOUT = socketTimeout;
        log.info("海康SDK初始化完成: host={}, connectTimeout={}, socketTimeout={}", host, connectTimeout, socketTimeout);
    }

    // ==================== 核心请求方法 ====================

    /**
     * 构建ArtemisConfig
     */
    public ArtemisConfig buildConfig() {
        ArtemisConfig config = new ArtemisConfig();
        config.setHost(host);
        config.setAppKey(appKey);
        config.setAppSecret(appSecret);
        return config;
    }

    /**
     * 构建请求路径Map
     *
     * @param apiPath API路径（不含/artemis前缀会自动补充）
     * @param useHttps 是否使用https协议
     */
    private Map<String, String> buildPath(String apiPath, boolean useHttps) {
        String protocol = useHttps ? "https://" : "http://";
        String fullPath = apiPath.startsWith(ARTEMIS_PATH) ? apiPath : ARTEMIS_PATH + apiPath;
        Map<String, String> path = new HashMap<>(2);
        path.put(protocol, fullPath);
        return path;
    }

    /**
     * 构建请求路径Map（默认https）
     *
     * @param apiPath API路径
     */
    private Map<String, String> buildPath(String apiPath) {
        return buildPath(apiPath, true);
    }

    // ==================== POST JSON 请求 ====================

    /**
     * POST JSON请求（默认https + application/json）
     *
     * @param apiPath API路径，如 /api/resource/v1/org/orgList
     * @param body    JSON请求体字符串
     * @return 响应JSON字符串
     */
    public String doPostJson(String apiPath, String body) throws Exception {
        return doPostJson(apiPath, body, true, null, null);
    }

    /**
     * POST JSON请求
     *
     * @param apiPath       API路径
     * @param body          JSON请求体字符串
     * @param useHttps      是否使用https
     * @param query         查询参数（可为null）
     * @param customHeaders 自定义请求头（可为null）
     * @return 响应JSON字符串
     */
    public String doPostJson(String apiPath, String body, boolean useHttps,
                              Map<String, String> query, Map<String, String> customHeaders) throws Exception {
        Map<String, String> path = buildPath(apiPath, useHttps);
        return ArtemisHttpUtil.doPostStringArtemis(buildConfig(), path, body, query, null, "application/json", customHeaders);
    }

    /**
     * POST JSON请求（通过代理场景，自定义x-ca-path头）
     *
     * @param proxyApiPath 代理层API路径
     * @param realApiPath  Artemis真实API路径（设置到x-ca-path头）
     * @param body         JSON请求体字符串
     * @return 响应JSON字符串
     */
    public String doPostJsonByProxy(String proxyApiPath, String realApiPath, String body) throws Exception {
        Map<String, String> path = buildPath(proxyApiPath);
        Map<String, String> headers = new HashMap<>(2);
        headers.put("x-ca-path", ARTEMIS_PATH + realApiPath);
        log.info("海康代理POST请求: proxyPath={}, realPath={}, body={}", proxyApiPath, realApiPath, body);
        return ArtemisHttpUtil.doPostStringArtemis(buildConfig(), path, body, null, null, "application/json", headers);
    }

    /**
     * POST请求，将Map参数自动转为JSON
     *
     * @param apiPath  API路径
     * @param paramMap 参数Map
     * @return 响应JSON字符串
     */
    public String doPostJsonFromMap(String apiPath, Map<String, Object> paramMap) throws Exception {
        String body = JSON.toJSONString(paramMap);
        return doPostJson(apiPath, body);
    }

    // ==================== GET 请求 ====================

    /**
     * GET请求，返回Response对象（适用于下载图片/文件等场景）
     *
     * @param apiPath  API路径
     * @param query    查询参数（可为null）
     * @param headers  请求头（可为null）
     * @param useHttps 是否使用https
     * @return Response对象
     */
    public Response doGetResponse(String apiPath, Map<String, Object> query,
                                   Map<String, String> headers, boolean useHttps) throws Exception {
        Map<String, String> path = buildPath(apiPath, useHttps);
        log.info("海康GET请求: path={}, query={}", path, query);
        return ArtemisHttpUtil.doGetResponse(buildConfig(), path, query,  null, null,headers);
    }

    /**
     * GET请求，返回Response对象（默认https）
     *
     * @param apiPath API路径
     * @return Response对象
     */
    public Response doGetResponse(String apiPath) throws Exception {
        return doGetResponse(apiPath, null, null, true);
    }

    /**
     * GET请求，返回字符串
     *
     * @param apiPath API路径
     * @param query   查询参数（可为null）
     * @return 响应字符串
     */
    public String doGetString(String apiPath, Map<String, Object> query) throws Exception {
        Response response = doGetResponse(apiPath, query, null, true);
        return response.getBody();
    }

    // ==================== 图片下载 ====================

    /**
     * POST请求并返回图片Response（适用于返回302重定向到图片的接口）
     *
     * @param apiPath API路径
     * @param body    JSON请求体字符串
     * @param query   查询参数（可为null）
     * @param headers 自定义请求头（可为null）
     * @return Response对象
     */
    public Response doPostForImage(String apiPath, String body,
                                    Map<String, String> query, Map<String, String> headers) throws Exception {
        Map<String, String> path = buildPath(apiPath);
        log.debug("海康POST图片请求: path={}, body={}", path, body);
        return ArtemisHttpUtil.doPostStringImgArtemis(buildConfig(), path, body, query, null, "application/json", headers);
    }


    // ==================== 通用工具方法 ====================

    /**
     * 将输入流保存到磁盘
     *
     * @param inputStream 输入流
     * @param dirPath     保存目录路径
     * @param fileName    文件名
     * @return 是否保存成功
     */
    public boolean saveToDisk(InputStream inputStream, String dirPath, String fileName) {
        if (inputStream == null) {
            log.error("保存文件失败: 输入流为null");
            return false;
        }
        File dir = new File(dirPath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (!created) {
                log.error("创建目录失败: {}", dirPath);
                return false;
            }
        }
        File file = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(file);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             BufferedInputStream bis = new BufferedInputStream(inputStream)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            bos.flush();
            log.debug("文件保存成功: {}", file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            log.error("文件保存失败: path={}, fileName={}", dirPath, fileName, e);
            return false;
        }
    }

    /**
     * 解析响应为JSONObject
     *
     * @param responseBody 响应字符串
     * @return JSONObject
     */
    public JSONObject parseResponse(String responseBody) {
        return JSON.parseObject(responseBody);
    }

    /**
     * 检查响应是否成功（海康接口通用返回格式: {"code": "0", ...}）
     *
     * @param responseBody 响应字符串
     * @return code为"0"时返回true
     */
    public boolean isSuccess(String responseBody) {
        JSONObject json = parseResponse(responseBody);
        return "0".equals(json.getString("code"));
    }

    /**
     * 获取响应中的data字段
     *
     * @param responseBody 响应字符串
     * @return data字段的JSONObject
     */
    public JSONObject getResponseData(String responseBody) {
        JSONObject json = parseResponse(responseBody);
        return json.getJSONObject("data");
    }

    /**
     * 解析响应为指定类型的Java对象
     *
     * @param responseBody 响应字符串
     * @param clazz        目标类型
     * @param <T>          泛型
     * @return 解析后的对象
     */
    public <T> T parseResponse(String responseBody, Class<T> clazz) {
        return JSON.parseObject(responseBody, clazz);
    }
}
