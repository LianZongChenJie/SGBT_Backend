package org.jeecg.modules.bems.hikvision.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.jeecg.modules.bems.hikvision.config.HlsProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HLS流管理器：管理所有摄像头的RTSP拉流转码任务
 * <p>
 * 核心机制：
 * <ul>
 *   <li><b>流复用</b>：同一摄像头编码的拉流转码任务全局唯一，多路观看共享同一路HLS输出，
 *       已存在的流直接返回HLS地址，避免重复占用摄像头RTSP通道；</li>
 *   <li><b>引用计数</b>：获取播放地址时 +1，前端主动调用释放接口时 -1；</li>
 *   <li><b>无人观看自动停止</b>：观看人数为0且空闲超过阈值，或前端心跳超时（页面异常关闭兜底），
 *       自动停止拉流转码并清理HLS临时文件，释放摄像头通道；</li>
 *   <li><b>心跳续期</b>：前端播放过程中周期性调用心跳接口刷新最后活跃时间。</li>
 * </ul>
 * </p>
 *
 * @author bems
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HlsStreamManager {

    static {
        // FFmpeg原生日志默认INFO级别：swscaler像素格式提示、hls muxer写切片("Opening ... for writing")
        // 每路拉流每秒刷十几行，会淹没业务日志。这里接管日志输出并只保留ERROR及以上。
        // 排查转码问题时把级别改成 avutil.AV_LOG_INFO / AV_LOG_DEBUG 即可恢复完整日志。
        try {
            FFmpegLogCallback.setLevel(avutil.AV_LOG_ERROR);
            FFmpegLogCallback.set();
        } catch (Throwable t) {
            // 原生库缺失时（如本地Windows开发，pom只引了linux-x86_64原生库）不能阻断启动
            log.warn("FFmpeg日志级别设置失败，原生日志将直接输出到控制台: {}", t.getMessage());
        }
    }

    /** 摄像头唯一编码 -> HLS流任务 */
    private final Map<String, CameraHlsStream> streams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hls-idle-checker");
        t.setDaemon(true);
        return t;
    });

    /** HLS转码相关配置 */
    private final HlsProperties hlsProperties;

    @PostConstruct
    public void init() {
        scheduler.scheduleWithFixedDelay(this::checkIdleStreams, 15, 15, TimeUnit.SECONDS);
        log.info("HLS流管理器启动, 输出目录={}, 切片时长={}s, 列表长度={}, 空闲停止={}s, 心跳超时={}s",
                hlsProperties.getOutputDir(), hlsProperties.getSegmentSeconds(),
                hlsProperties.getListSize(), hlsProperties.getIdleStopSeconds(),
                hlsProperties.getHeartbeatTimeoutSeconds());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        int size = streams.size();
        for (CameraHlsStream stream : streams.values()) {
            try {
                stream.stop();
            } catch (Exception ignored) {
            }
        }
        streams.clear();
        log.info("HLS流管理器已关闭, 共清理{}路流", size);
    }

    /**
     * 获取摄像头HLS流（已存在且正在拉流则直接复用，否则新建并启动转码任务），并增加观看计数
     *
     * @param cameraIndexCode 摄像头唯一编码
     * @param rtspUrl         RTSP播放地址（首次创建时使用）
     * @return 流任务，创建/启动失败返回 null
     */
    public CameraHlsStream getOrCreate(String cameraIndexCode, String rtspUrl) {
        return getOrCreate(cameraIndexCode, cameraIndexCode, rtspUrl);
    }

    /**
     * 获取HLS流（已存在且正在拉流则直接复用，否则新建并启动转码任务），并增加观看计数
     * <p>用于回放等场景：streamKey 与 cameraIndexCode 分离，避免同一摄像头的实时流与回放流
     * 因共用同一路HLS输出而互相冲突。</p>
     *
     * @param streamKey       流唯一标识（同时作为HLS输出目录与访问路径）
     * @param cameraIndexCode 摄像头唯一编码（仅用于日志标识）
     * @param rtspUrl         RTSP播放地址（首次创建时使用）
     * @return 流任务，创建/启动失败返回 null
     */
    public CameraHlsStream getOrCreate(String streamKey, String cameraIndexCode, String rtspUrl) {
        CameraHlsStream stream = streams.get(streamKey);
        if (stream == null || !stream.isRunning()) {
            synchronized (this) {
                stream = streams.get(streamKey);
                if (stream == null || !stream.isRunning()) {
                    stream = createStream(streamKey, cameraIndexCode, rtspUrl);
                    if (stream == null) {
                        return null;
                    }
                    streams.put(streamKey, stream);
                }
            }
        }
        stream.acquire();
        return stream;
    }

    private CameraHlsStream createStream(String streamKey, String cameraIndexCode, String rtspUrl) {
        File outputDir = new File(hlsProperties.getOutputDir(), streamKey);
        CameraHlsStream stream = new CameraHlsStream(
                cameraIndexCode, rtspUrl, outputDir,
                "/hls/" + streamKey + "/index.m3u8",
                hlsProperties.getSegmentSeconds(), hlsProperties.getListSize(),
                hlsProperties.getFrameRate());
        stream.start();
        return stream;
    }

    /**
     * 退出观看（引用计数-1），计数降到0后延迟 idleStopSeconds 秒自动停止拉流
     */
    public void release(String cameraIndexCode) {
        CameraHlsStream stream = streams.get(cameraIndexCode);
        if (stream == null) {
            return;
        }
        int count = stream.release();
        log.info("摄像头[{}] 释放观看, 剩余观看人数={}", cameraIndexCode, count);
        if (count <= 0) {
            scheduleDelayedStop(cameraIndexCode, stream);
        }
    }

    /**
     * 前端心跳续期（播放过程中周期调用）
     * <p>入参为流标识：实时流为摄像头唯一编码 cameraIndexCode，回放流为 streamKey。</p>
     */
    public void heartbeat(String streamKey) {
        CameraHlsStream stream = streams.get(streamKey);
        if (stream != null) {
            stream.heartbeat();
        }
    }

    /**
     * 移除指定摄像头的流任务并停止拉流、清理输出目录
     */
    public void removeStream(String cameraIndexCode) {
        CameraHlsStream stream = streams.remove(cameraIndexCode);
        if (stream != null) {
            stopAndClean(stream, "主动移除");
        }
    }

    private void scheduleDelayedStop(String cameraIndexCode, CameraHlsStream stream) {
        scheduler.schedule(() -> {
            CameraHlsStream current = streams.get(cameraIndexCode);
            if (current == stream && stream.getViewerCount() <= 0) {
                removeStream(cameraIndexCode);
                log.info("摄像头[{}] 无人观看, 空闲{}s后已自动停止拉流",
                        cameraIndexCode, hlsProperties.getIdleStopSeconds());
            }
        }, hlsProperties.getIdleStopSeconds(), TimeUnit.SECONDS);
    }

    /**
     * 定时检查：无人观看/心跳超时的流自动停止拉流，异常退出（启动失败）的任务清理出Map
     */
    private void checkIdleStreams() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, CameraHlsStream> entry : streams.entrySet()) {
            String code = entry.getKey();
            CameraHlsStream stream = entry.getValue();
            if (!stream.isRunning()) {
                // 拉流失败或已停止的任务，清理资源
                removeStream(code);
                continue;
            }
            long idleMillis = now - stream.getLastActiveTime();
            boolean noViewerIdle = stream.getViewerCount() <= 0
                    && idleMillis >= hlsProperties.getIdleStopSeconds() * 1000L;
            boolean heartbeatTimeout = idleMillis >= hlsProperties.getHeartbeatTimeoutSeconds() * 1000L;
            if (noViewerIdle || heartbeatTimeout) {
                log.info("摄像头[{}] 自动停止拉流: 观看人数={}, 空闲={}s, 心跳超时={}",
                        code, stream.getViewerCount(), idleMillis / 1000, heartbeatTimeout);
                removeStream(code);
            }
        }
    }

    private void stopAndClean(CameraHlsStream stream, String reason) {
        stream.stop();
        deleteDir(stream.getOutputDir());
        log.info("摄像头[{}] HLS流已停止({}): {}", stream.getCameraIndexCode(), reason, stream.getHlsRelativeUrl());
    }

    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    boolean deleted = file.delete();
                    if (!deleted) {
                        log.debug("HLS临时文件删除失败(可能被占用): {}", file.getAbsolutePath());
                    }
                }
            }
        }
        dir.delete();
    }
}
