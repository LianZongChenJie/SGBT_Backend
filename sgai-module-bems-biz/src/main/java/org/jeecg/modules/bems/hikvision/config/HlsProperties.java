package org.jeecg.modules.bems.hikvision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 摄像头HLS转码配置（对应 application.yml / Nacos 中 bems.hikvision.hls.* 配置项）
 * <p>统一管理RTSP拉流转码、无人观看自动停止、心跳超时等参数。</p>
 *
 * @author bems
 */
@Data
@Component
@ConfigurationProperties(prefix = "bems.hikvision.hls")
public class HlsProperties {

    /** HLS切片输出目录（可通过 /hls/** 访问） */
    private String outputDir = "./hls-output";

    /** 单个切片时长（秒），越小延迟越低 */
    private int segmentSeconds = 2;

    /** m3u8列表保留的切片数量 */
    private int listSize = 5;

    /**
     * 转码帧率覆盖（帧/秒），0 表示自动识别。
     * <p>RTSP的SDP里帧率常缺失或不准（如变帧率摄像头报25实际20），
     * 会导致GOP长度与切片时长换算错位、切片起点不再是IDR而花屏；
     * 排查时可通过日志中的“帧率”字段确认，必要时用该配置强制指定。</p>
     */
    private int frameRate = 0;

    /** 无人观看后延迟多少秒自动停止拉流 */
    private int idleStopSeconds = 60;

    /** 前端心跳超时时间（秒），超过强制停止拉流（兜底页面异常关闭） */
    private int heartbeatTimeoutSeconds = 120;

    /** 获取播放地址时等待HLS流就绪的最长时间（秒） */
    private int readyWaitSeconds = 15;

    /** 可选：前端可访问的后端基础地址，配置后优先级最高（如 http://47.95.156.86:59999/sgai-bems） */
    private String publicBaseUrl = "";

    /**
     * HLS访问地址的服务路由前缀（微服务经网关访问时必配，如 /sgai-bems）
     * <p>网关转发时请求Host只含网关地址，不含服务前缀，缺少该前缀会导致 /hls/** 访问不到。
     * 若网关已透传 X-Forwarded-Prefix 请求头，则优先使用请求头值。</p>
     */
    private String urlPrefix = "";
}
