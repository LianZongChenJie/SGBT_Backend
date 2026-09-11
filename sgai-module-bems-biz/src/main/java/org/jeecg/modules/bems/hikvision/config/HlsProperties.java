package org.jeecg.modules.bems.hikvision.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 摄像头HLS转码配置（对应 application.yml / Nacos 中 bems.hikvision.hls.* 配置项）
 * <p>统一管理RTMP拉流转码、无人观看自动停止、心跳超时等参数。</p>
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

    /** 无人观看后延迟多少秒自动停止拉流 */
    private int idleStopSeconds = 60;

    /** 前端心跳超时时间（秒），超过强制停止拉流（兜底页面异常关闭） */
    private int heartbeatTimeoutSeconds = 120;

    /** 获取播放地址时等待HLS流就绪的最长时间（秒） */
    private int readyWaitSeconds = 15;

    /** 可选：前端可访问的后端基础地址，不配置则取请求Host */
    private String publicBaseUrl = "";
}
