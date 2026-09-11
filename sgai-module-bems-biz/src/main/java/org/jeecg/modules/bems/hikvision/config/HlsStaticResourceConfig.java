package org.jeecg.modules.bems.hikvision.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * HLS流静态资源映射：将 /hls/** 映射到本地HLS输出目录
 * <p>前端通过 /hls/{cameraIndexCode}/index.m3u8 直接访问转码后的HLS流。</p>
 *
 * @author bems
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HlsStaticResourceConfig implements WebMvcConfigurer {

    private final HlsProperties hlsProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = new File(hlsProperties.getOutputDir());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // Windows路径统一转为正斜杠，避免资源定位失败
        String location = "file:" + dir.getAbsolutePath().replace("\\", "/") + "/";
        // 直播切片禁用缓存：index.m3u8同名反复重写，若走Last-Modified协商缓存(精度仅秒)会返回304，
        // 播放器继续用旧列表去请求已被delete_segments删除的切片，导致404、画面花屏/卡死
        registry.addResourceHandler("/hls/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.noStore().mustRevalidate());
        log.info("HLS静态资源映射: /hls/** -> {}", location);
    }
}
