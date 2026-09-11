package org.jeecg.modules.bems.hikvision.util;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单个摄像头 RTSP -> HLS 转码流任务
 * <p>
 * 由 {@link HlsStreamManager} 统一管理：
 * 同一摄像头编码只存在一个转码任务（多路观看复用同一路HLS），
 * 无人观看/心跳超时后由管理器调用 {@link #stop()} 停止拉流释放摄像头通道。
 * </p>
 *
 * @author bems
 */
@Slf4j
public class CameraHlsStream {

    /** 断线后最大重连次数 */
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    private final String cameraIndexCode;
    /** 取流地址：海康返回的RTSP播放地址 */
    private final String sourceUrl;
    private final File outputDir;
    private final String hlsRelativeUrl;

    /** 观看人数（引用计数）：进入观看+1，主动释放-1，下限为0 */
    private final AtomicInteger viewerCount = new AtomicInteger(0);
    /** 最后活跃时间戳（获取/心跳/释放时刷新），用于无人观看自动停止判断 */
    private volatile long lastActiveTime = System.currentTimeMillis();
    /** 是否正在拉流转码 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    /** 请求停止标记 */
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    /** 首个HLS切片写入成功信号，用于接口等待流就绪 */
    private final CountDownLatch readyLatch = new CountDownLatch(1);
    /** 任务停止信号，用于等待工作线程彻底退出后再清理文件 */
    private final CountDownLatch stoppedLatch = new CountDownLatch(1);
    /** 拉流转码工作线程 */
    private volatile Thread workerThread;
    /** 启动失败/运行错误信息 */
    private volatile String errorMessage;

    /** 转码会话序号：断线重连后HLS切片编号递增，避免与旧切片编号冲突 */
    private int sessionIndex = 0;

    private final int hlsSegmentSeconds;
    private final int hlsListSize;

    public CameraHlsStream(String cameraIndexCode, String sourceUrl, File outputDir,
                           String hlsRelativeUrl, int hlsSegmentSeconds, int hlsListSize) {
        this.cameraIndexCode = cameraIndexCode;
        this.sourceUrl = sourceUrl;
        this.outputDir = outputDir;
        this.hlsRelativeUrl = hlsRelativeUrl;
        this.hlsSegmentSeconds = hlsSegmentSeconds;
        this.hlsListSize = hlsListSize;
    }

    public String getCameraIndexCode() {
        return cameraIndexCode;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public File getOutputDir() {
        return outputDir;
    }

    public String getHlsRelativeUrl() {
        return hlsRelativeUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /** 进入观看（引用计数+1） */
    public int acquire() {
        int count = viewerCount.incrementAndGet();
        lastActiveTime = System.currentTimeMillis();
        return count;
    }

    /** 退出观看（引用计数-1，下限0） */
    public int release() {
        int count = viewerCount.updateAndGet(v -> Math.max(0, v - 1));
        lastActiveTime = System.currentTimeMillis();
        return count;
    }

    /** 前端心跳续期 */
    public void heartbeat() {
        lastActiveTime = System.currentTimeMillis();
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getViewerCount() {
        return viewerCount.get();
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    /** 是否已就绪（首个HLS切片已生成，前端可直接播放） */
    public boolean isReady() {
        return readyLatch.getCount() == 0;
    }

    /** 等待流就绪，最多等待 maxWaitSeconds 秒；返回是否就绪（超时也会返回false） */
    public boolean awaitReady(long maxWaitSeconds) {
        try {
            return readyLatch.await(maxWaitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return readyLatch.getCount() == 0;
        }
    }

    /** 启动拉流转码任务（幂等） */
    public synchronized boolean start() {
        if (running.get()) {
            return true;
        }
        stopRequested.set(false);
        running.set(true);
        workerThread = new Thread(this::runTranscode, "hls-stream-" + cameraIndexCode);
        workerThread.setDaemon(true);
        workerThread.start();
        return true;
    }

    /** 停止拉流转码任务（幂等），并等待工作线程退出 */
    public synchronized void stop() {
        if (stopRequested.compareAndSet(false, true)) {
            running.set(false);
            if (workerThread != null) {
                workerThread.interrupt();
            }
        }
        // 等待工作线程彻底退出，确保文件句柄释放后再做清理（Windows下删除文件必须等待）
        try {
            stoppedLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 工作线程主循环：负责连接RTSP、转码写HLS切片，断线自动重连 */
    private void runTranscode() {
        log.info("摄像头[{}] 开始拉流转码: {}", cameraIndexCode, sourceUrl);
        int reconnectAttempt = 0;
        try {
            while (!stopRequested.get() && !Thread.currentThread().isInterrupted()) {
                reconnectAttempt++;
                if (reconnectAttempt > MAX_RECONNECT_ATTEMPTS) {
                    this.errorMessage = "RTSP拉流多次中断，已放弃重连";
                    log.error("摄像头[{}] {}", cameraIndexCode, errorMessage);
                    break;
                }
                try {
                    transcodeOnce();
                    // 正常退出（收到停止指令）
                    break;
                } catch (Exception e) {
                    if (stopRequested.get()) {
                        break;
                    }
                    log.warn("摄像头[{}] 拉流转码第{}次中断: {}", cameraIndexCode, reconnectAttempt, e.getMessage());
                    // 指数退避后重连
                    try {
                        Thread.sleep(Math.min(1000L * reconnectAttempt, 5000L));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            running.set(false);
            stoppedLatch.countDown();
            log.info("摄像头[{}] HLS流任务已停止", cameraIndexCode);
        }
    }

    /**
     * 单次拉流转码：连接RTSP -> 转码 -> 写HLS切片，直到流中断或收到停止指令
     */
    private void transcodeOnce() throws Exception {
        FFmpegFrameGrabber grabber = null;
        FFmpegFrameRecorder recorder = null;
        try {
            grabber = new FFmpegFrameGrabber(sourceUrl);
            // 海康取流地址为RTSP，显式指定TCP传输，避免UDP丢包导致花屏、卡顿甚至断流
            grabber.setOption("rtsp_transport", "tcp");
            grabber.setOption("stimeout", "5000000");
            grabber.setOption("rw_timeout", "5000000");
            grabber.setOption("buffer_size", "1024000");
            grabber.start();

            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            double frameRate = grabber.getFrameRate();
            if (width <= 0 || height <= 0 || Double.isNaN(frameRate)) {
                throw new IllegalStateException("无法获取视频分辨率/帧率: " + sourceUrl);
            }
            if (frameRate <= 0) {
                frameRate = 25;
            }

            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IllegalStateException("创建HLS输出目录失败: " + outputDir.getAbsolutePath());
            }

            recorder = new FFmpegFrameRecorder(
                    new File(outputDir, "index.m3u8").getAbsolutePath(), width, height);
            recorder.setFormat("hls");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setFrameRate(frameRate);
            recorder.setGopSize((int) Math.round(frameRate * 2));
            recorder.setVideoBitrate(1024 * 1024);
            recorder.setOption("hls_time", String.valueOf(hlsSegmentSeconds));
            recorder.setOption("hls_list_size", String.valueOf(hlsListSize));
            recorder.setOption("hls_flags", "delete_segments");
            recorder.setOption("hls_segment_filename",
                    new File(outputDir, "segment-%d.ts").getAbsolutePath());
            // 每次会话的切片编号递增，断线重连后不覆盖旧切片，避免播放器解析错乱
            recorder.setOption("start_number", String.valueOf((long) sessionIndex * 10000 + 1));
            sessionIndex++;

            // 音频可选转码为AAC，无音频则忽略
            int audioChannels = grabber.getAudioChannels();
            int sampleRate = grabber.getSampleRate();
            if (audioChannels > 0 && sampleRate > 0) {
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                recorder.setAudioChannels(audioChannels);
                recorder.setSampleRate(sampleRate);
                recorder.setAudioBitrate(64 * 1024);
            }
            boolean withAudio = audioChannels > 0;

            recorder.start();
            log.info("摄像头[{}] 转码器启动, 分辨率{}x{}, 帧率{}", cameraIndexCode, width, height, frameRate);

            boolean firstFrameWritten = false;
            while (!stopRequested.get() && !Thread.currentThread().isInterrupted()) {
                Frame frame = grabber.grab();
                if (frame == null) {
                    // 拉流返回空，视为流中断，退出本次会话交由外层重连
                    throw new IllegalStateException("RTSP流中断（grab返回空帧）");
                }
                if (frame.image != null || (frame.samples != null && withAudio)) {
                    recorder.record(frame);
                    if (!firstFrameWritten) {
                        firstFrameWritten = true;
                        readyLatch.countDown();
                        log.info("摄像头[{}] HLS流已就绪: {}", cameraIndexCode, hlsRelativeUrl);
                    }
                }
            }
        } finally {
            if (recorder != null) {
                try {
                    recorder.close();
                } catch (Exception ignored) {
                }
            }
            if (grabber != null) {
                try {
                    grabber.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
