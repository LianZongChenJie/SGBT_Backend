package org.jeecg.modules.bems.hikvision.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.bems.hikvision.config.HlsProperties;
import org.jeecg.modules.bems.hikvision.dto.CameraCoordinateGroupVO;
import org.jeecg.modules.bems.hikvision.dto.CameraListVO;
import org.jeecg.modules.bems.hikvision.dto.CameraOnlineRequest;
import org.jeecg.modules.bems.hikvision.dto.CameraOnlineResponse;
import org.jeecg.modules.bems.hikvision.dto.CameraPlaybackUrlVO;
import org.jeecg.modules.bems.hikvision.dto.CameraPlayUrlVO;
import org.jeecg.modules.bems.hikvision.dto.CameraResourcePageDto;
import org.jeecg.modules.bems.hikvision.dto.CameraSearchRequest;
import org.jeecg.modules.bems.hikvision.dto.CameraSearchResponse;
import org.jeecg.modules.bems.hikvision.dto.PlaybackUrlRequest;
import org.jeecg.modules.bems.hikvision.dto.PlayUrlRequest;
import org.jeecg.modules.bems.hikvision.dto.RegionCameraTreeVO;
import org.jeecg.modules.bems.hikvision.entity.CameraResource;
import org.jeecg.modules.bems.hikvision.entity.RegionResource;
import org.jeecg.modules.bems.hikvision.mapper.CameraResourceMapper;
import org.jeecg.modules.bems.hikvision.mapper.RegionResourceMapper;
import org.jeecg.modules.bems.hikvision.service.ICameraResourceService;
import org.jeecg.modules.bems.hikvision.util.CameraHlsStream;
import org.jeecg.modules.bems.hikvision.util.HikvisionUtil;
import org.jeecg.modules.bems.hikvision.util.HlsStreamManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 摄像头资源同步服务实现
 * <p>每次同步先清空表，再全量拉取海康数据批量插入。</p>
 *
 * @author bems
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CameraResourceServiceImpl extends ServiceImpl<CameraResourceMapper, CameraResource>
        implements ICameraResourceService {

    /**
     * 海康摄像头查询API路径
     */
    private static final String CAMERA_SEARCH_API = "/api/resource/v1/cameras";

    /**
     * 海康获取摄像头播放地址API路径
     */
    private static final String CAMERA_PREVIEW_URL_API = "/api/video/v2/cameras/previewURLs";

    /**
     * 海康获取监控点回放地址API路径
     */
    private static final String CAMERA_PLAYBACK_URL_API = "/api/video/v2/cameras/playbackURLs";

    /**
     * 回放地址时间格式（ISO8601：yyyy-MM-dd'T'HH:mm:ss.SSSXXX）
     */
    private static final DateTimeFormatter ISO_OFFSET_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    /**
     * 回放最大时间跨度（天）：开始时间与结束时间相差不超过3天
     */
    private static final int PLAYBACK_MAX_SPAN_DAYS = 3;

    /**
     * 海康监控点在线状态查询API路径
     */
    private static final String CAMERA_ONLINE_API = "/api/nms/v1/online/camera/get";

    /**
     * 固定分页大小（最大1000）
     */
    private static final int PAGE_SIZE = 1000;

    /**
     * 日期解析格式（兼容多种）
     */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss"
    };

    private final HikvisionUtil hikvisionUtil;

    /**
     * 区域资源 Mapper（table_region_resource 表）
     */
    private final RegionResourceMapper regionResourceMapper;

    /**
     * HLS流管理器：负责RTSP拉流转码、流复用与无人观看自动停止
     */
    private final HlsStreamManager hlsStreamManager;

    /**
     * HLS转码相关配置
     */
    private final HlsProperties hlsProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncFromHikvision() {
        log.info("开始从海康平台全量同步摄像头数据...");

        // 1. 先逐页从海康拉取全部数据
        List<CameraSearchResponse.CameraItem> allItems = fetchAllFromHikvision();

        // 2. 判断海康返回数据是否为空，为空则不处理
        if (allItems.isEmpty()) {
            log.warn("海康未返回任何摄像头数据，跳过同步，保留现有记录");
            return 0;
        }

        // 3. 清空表全部数据
        int deletedCount = baseMapper.delete(null);
        log.info("已清空摄像头资源表, 删除{}条记录", deletedCount);

        // 4. 批量转换并插入
        Date now = new Date();
        List<CameraResource> entityList = new ArrayList<>(allItems.size());
        for (CameraSearchResponse.CameraItem item : allItems) {
            CameraResource entity = convertToEntity(item);
            entity.setGmtCreate(now);
            entity.setGmtModified(now);
            entityList.add(entity);
        }

        // 达梦驱动对JDBC批量(executeBatch)支持有缺陷，大数据量时会抛index out of range，改为循环单条插入绕开该问题
        for (CameraResource entity : entityList) {
            baseMapper.insert(entity);
        }
        log.info("海康摄像头数据全量同步完成, 共同步{}条", entityList.size());
        return entityList.size();
    }

    /**
     * 逐页从海康拉取全部摄像头数据
     *
     * @return 全部摄像头列表
     */
    private List<CameraSearchResponse.CameraItem> fetchAllFromHikvision() {
        List<CameraSearchResponse.CameraItem> allItems = new ArrayList<>();
        int pageNo = 1;
        boolean hasMore = true;

        while (hasMore) {
            CameraSearchRequest request = buildFixedRequest(pageNo);

            try {
                String requestBody = JSON.toJSONString(request);
                log.info("请求海康摄像头列表, pageNo={}, pageSize={}", pageNo, PAGE_SIZE);

                String responseBody = hikvisionUtil.doPostJson(CAMERA_SEARCH_API, requestBody);

                if (!hikvisionUtil.isSuccess(responseBody)) {
                    log.error("海康摄像头查询失败: {}", responseBody);
                    throw new RuntimeException("海康摄像头查询失败: " + responseBody);
                }

                JSONObject dataJson = hikvisionUtil.getResponseData(responseBody);
                if (dataJson == null) {
                    log.warn("海康返回的data为空");
                    break;
                }

                CameraSearchResponse response = dataJson.toJavaObject(CameraSearchResponse.class);
                List<CameraSearchResponse.CameraItem> cameraList = response.getList();

                if (cameraList == null || cameraList.isEmpty()) {
                    log.info("海康摄像头列表为空，拉取结束");
                    break;
                }

                allItems.addAll(cameraList);
                log.info("第{}页拉取完成, 本页{}条, 累计{}条", pageNo, cameraList.size(), allItems.size());

                // 判断是否还有下一页
                int total = response.getTotal() != null ? response.getTotal() : 0;
                if (pageNo * PAGE_SIZE >= total) {
                    hasMore = false;
                } else {
                    pageNo++;
                }

            } catch (Exception e) {
                log.error("拉取海康摄像头数据异常, pageNo={}", pageNo, e);
                throw new RuntimeException("拉取海康摄像头数据失败: " + e.getMessage(), e);
            }
        }

        log.info("海康数据拉取完成, 共获取{}条摄像头记录", allItems.size());
        return allItems;
    }

    /**
     * 构建固定的查询请求参数
     * <p>只传 pageNo 和 pageSize，拉取全部摄像头。</p>
     */
    private CameraSearchRequest buildFixedRequest(int pageNo) {
        CameraSearchRequest request = new CameraSearchRequest();
        request.setPageNo(pageNo);
        request.setPageSize(PAGE_SIZE);
        return request;
    }

    /**
     * 将海康返回的摄像头数据（v1接口）转换为数据库实体
     */
    private CameraResource convertToEntity(CameraSearchResponse.CameraItem item) {
        CameraResource entity = new CameraResource();
        // 各字段按表列长度截断，避免超出导致达梦"字符串截断"报错
        entity.setIndexCode(truncate(item.getCameraIndexCode(), 64));
        entity.setName(truncate(item.getCameraName(), 128));
        entity.setCameraType(item.getCameraType());
        entity.setCapability(truncate(item.getCapabilitySet(), 512));
        entity.setChannelType(truncate(item.getChannelType(), 16));
        entity.setInstallLocation(truncate(item.getInstallLocation(), 256));
        entity.setRecordLocation(truncate(item.getRecordLocation(), 32));
        entity.setRegionIndexCode(truncate(item.getRegionIndexCode(), 64));
        entity.setTransType(item.getTransType());
        entity.setTreatyType(truncate(item.getTreatyType(), 32));
        entity.setExternalIndexCode(truncate(item.getGbIndexCode(), 64));

        // 通道号转换（String -> Integer）
        String channelNoStr = item.getChannelNo();
        if (channelNoStr != null && !channelNoStr.isEmpty() && !"null".equals(channelNoStr)) {
            try {
                entity.setChanNum(Integer.parseInt(channelNoStr));
            } catch (NumberFormatException e) {
                log.warn("通道号转换失败: {}", channelNoStr);
            }
        }

        // 经纬度转换（过滤"null"字符串）
        entity.setLongitude(parseBigDecimal(item.getLongitude()));
        entity.setLatitude(parseBigDecimal(item.getLatitude()));

        // 海拔（过滤"null"字符串）
        String altitude = item.getAltitude();
        entity.setElevation(altitude != null && !"null".equals(altitude) ? altitude : null);

        // 日期解析
        entity.setCreateTime(parseDate(item.getCreateTime()));
        entity.setUpdateTime(parseDate(item.getUpdateTime()));

        return entity;
    }

    /**
     * 按数据库列长度截断字符串，超长时截断并记录警告（达梦VARCHAR超出列定义会报"字符串截断"）
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("字段值超出列定义长度({}字符)，已截断处理, 原始长度={}", maxLength, value.length());
        return value.substring(0, maxLength);
    }

    /**
     * 安全解析BigDecimal（过滤"null"字符串）
     */
    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isEmpty() || "null".equals(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("BigDecimal转换失败: {}", value);
            return null;
        }
    }

    /**
     * BigDecimal转字符串（去除科学计数法，保留原样）
     */
    private String bigDecimalToStr(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.toPlainString();
    }

    /**
     * 解析日期字符串（兼容多种格式）
     */
    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        for (String pattern : DATE_PATTERNS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                return sdf.parse(dateStr);
            } catch (ParseException ignored) {
                // 尝试下一种格式
            }
        }
        log.warn("日期解析失败: {}", dateStr);
        return null;
    }

    @Override
    public List<CameraPlayUrlVO> getPlayUrls(List<String> cameraIndexCodes) {
        if (cameraIndexCodes == null || cameraIndexCodes.isEmpty()) {
            log.warn("获取播放地址失败: cameraIndexCodes为空");
            return Collections.emptyList();
        }

        log.info("开始从海康平台获取{}个摄像头的HLS播放地址", cameraIndexCodes.size());
        List<CameraPlayUrlVO> result = new ArrayList<>();

        for (String cameraIndexCode : cameraIndexCodes) {
            try {
                // 1. 请求海康SDK直接获取HLS播放地址（无需本地拉流转码）
                PlayUrlRequest request = buildPlayUrlRequest(cameraIndexCode);
                String requestBody = JSON.toJSONString(request);
                log.info("请求海康摄像头HLS播放地址, cameraIndexCode={}", cameraIndexCode);

                String responseBody = hikvisionUtil.doPostJson(CAMERA_PREVIEW_URL_API, requestBody);

                if (!hikvisionUtil.isSuccess(responseBody)) {
                    log.error("获取摄像头[{}]HLS地址失败, 海康响应: {}", cameraIndexCode, responseBody);
                    continue;
                }

                JSONObject dataJson = hikvisionUtil.getResponseData(responseBody);
                if (dataJson == null || StringUtils.isBlank(dataJson.getString("url"))) {
                    log.warn("摄像头[{}] 海康未返回HLS地址", cameraIndexCode);
                    continue;
                }

                // 2. 直接返回海康流媒体服务提供的完整HLS播放地址
                String hlsUrl = dataJson.getString("url");
                result.add(new CameraPlayUrlVO(cameraIndexCode, hlsUrl));
                log.info("摄像头[{}] HLS播放地址: {}", cameraIndexCode, hlsUrl);
            } catch (Exception e) {
                log.error("获取摄像头[{}]HLS播放地址异常", cameraIndexCode, e);
            }
        }

        log.info("HLS播放地址获取完成, 成功{}个/共{}个", result.size(), cameraIndexCodes.size());
        return result;
    }

    /**
     * 构建获取HLS播放地址的请求参数（由海康流媒体服务直接输出HLS，服务端不做本地转码）
     */
    private PlayUrlRequest buildPlayUrlRequest(String cameraIndexCode) {
        PlayUrlRequest request = new PlayUrlRequest();
        request.setCameraIndexCode(cameraIndexCode);
        request.setStreamType(0);
        request.setProtocol("hls");
        request.setTransmode(1);
        return request;
    }

    @Override
    public CameraPlayUrlVO getLocalHlsPlayUrl(String cameraIndexCode) throws Exception {
        if (StringUtils.isBlank(cameraIndexCode)) {
            log.warn("获取本地HLS播放地址失败: cameraIndexCode为空");
            return null;
        }

        log.info("开始获取摄像头[{}]的本地HLS播放地址", cameraIndexCode);

        // 1. 请求海康SDK获取RTSP播放地址
        PlayUrlRequest request = buildRtspPlayUrlRequest(cameraIndexCode);
        String requestBody = JSON.toJSONString(request);
        log.info("请求海康摄像头RTSP播放地址, cameraIndexCode={}", cameraIndexCode);

        String responseBody = hikvisionUtil.doPostJson(CAMERA_PREVIEW_URL_API, requestBody);
        if (!hikvisionUtil.isSuccess(responseBody)) {
            log.error("获取摄像头[{}]RTSP地址失败, 海康响应: {}", cameraIndexCode, responseBody);
            return null;
        }

        JSONObject dataJson = hikvisionUtil.getResponseData(responseBody);
        if (dataJson == null || StringUtils.isBlank(dataJson.getString("url"))) {
            log.warn("摄像头[{}] 海康未返回RTSP地址", cameraIndexCode);
            return null;
        }
        String rtspUrl = dataJson.getString("url");
        log.info("摄像头[{}] RTSP地址获取成功", cameraIndexCode);

        // 2. 通过HLS流管理器获取本地HLS流：同一摄像头正在拉流时直接复用，不重复转码
        CameraHlsStream stream = hlsStreamManager.getOrCreate(cameraIndexCode, rtspUrl);
        if (stream == null) {
            log.error("摄像头[{}] HLS转码任务创建失败", cameraIndexCode);
            return null;
        }

        // 3. 等待HLS流就绪（首个切片已生成），超时仍返回地址由前端自行重试
        boolean ready = stream.awaitReady(hlsProperties.getReadyWaitSeconds());
        if (!ready) {
            if (!stream.isRunning()) {
                log.error("摄像头[{}] HLS转码启动失败: {}", cameraIndexCode, stream.getErrorMessage());
                hlsStreamManager.removeStream(cameraIndexCode);
                return null;
            }
            log.warn("摄像头[{}] HLS流未在{}s内就绪, 仍返回地址由前端重试",
                    cameraIndexCode, hlsProperties.getReadyWaitSeconds());
        }

        // 4. 返回本地HLS相对播放地址（由Controller拼装完整访问地址）
        log.info("摄像头[{}] 本地HLS播放地址: {}", cameraIndexCode, stream.getHlsRelativeUrl());
        return new CameraPlayUrlVO(cameraIndexCode, stream.getHlsRelativeUrl());
    }

    @Override
    public void heartbeat(String streamKey) {
        if (StringUtils.isBlank(streamKey)) {
            return;
        }
        hlsStreamManager.heartbeat(streamKey);
    }

    /**
     * 构建获取RTSP播放地址的固定请求参数（协议为rtsp，由JavaCV本地拉流转码为HLS）
     */
    private PlayUrlRequest buildRtspPlayUrlRequest(String cameraIndexCode) {
        PlayUrlRequest request = new PlayUrlRequest();
        request.setCameraIndexCode(cameraIndexCode);
        request.setStreamType(0);
        request.setProtocol("rtsp");
        request.setTransmode(1);
        request.setExpand("transcode=0");
        return request;
    }

    @Override
    public List<CameraPlaybackUrlVO> getPlaybackUrls(List<String> cameraIndexCodes, String beginTime, String endTime) {
        if (cameraIndexCodes == null || cameraIndexCodes.isEmpty()) {
            log.warn("获取回放地址失败: cameraIndexCodes为空");
            return Collections.emptyList();
        }

        // 解析并校验回放时间段（默认结束时间为当前时间，开始时间为结束时间前3天）
        PlaybackTimeRange range = resolvePlaybackTimeRange(beginTime, endTime);

        log.info("开始从海康平台获取{}个摄像头的HLS回放地址, beginTime={}, endTime={}",
                cameraIndexCodes.size(), range.getBeginTime(), range.getEndTime());
        List<CameraPlaybackUrlVO> result = new ArrayList<>();

        for (String cameraIndexCode : cameraIndexCodes) {
            try {
                // 1. 请求海康SDK直接获取HLS回放地址（无需本地拉流转码）
                PlaybackUrlRequest request = buildPlaybackUrlRequest(cameraIndexCode, "hls", range);
                String requestBody = JSON.toJSONString(request);
                log.info("请求海康摄像头HLS回放地址, cameraIndexCode={}, body={}", cameraIndexCode, requestBody);

                String responseBody = hikvisionUtil.doPostJson(CAMERA_PLAYBACK_URL_API, requestBody);

                if (!hikvisionUtil.isSuccess(responseBody)) {
                    log.error("获取摄像头[{}]HLS回放地址失败, 海康响应: {}", cameraIndexCode, responseBody);
                    continue;
                }

                JSONObject dataJson = hikvisionUtil.getResponseData(responseBody);
                if (dataJson == null || StringUtils.isBlank(dataJson.getString("url"))) {
                    log.warn("摄像头[{}] 海康未返回HLS回放地址", cameraIndexCode);
                    continue;
                }

                // 2. 解析海康返回的回放地址、分页标记与录像片段列表
                CameraPlaybackUrlVO vo = new CameraPlaybackUrlVO();
                vo.setCameraIndexCode(cameraIndexCode);
                vo.setUrl(dataJson.getString("url"));
                vo.setUuid(dataJson.getString("uuid"));
                JSONArray segmentArray = dataJson.getJSONArray("list");
                if (segmentArray != null) {
                    vo.setList(segmentArray.toJavaList(CameraPlaybackUrlVO.PlaybackSegment.class));
                }
                result.add(vo);
                log.info("摄像头[{}] HLS回放地址: {}", cameraIndexCode, vo.getUrl());
            } catch (Exception e) {
                log.error("获取摄像头[{}]HLS回放地址异常", cameraIndexCode, e);
            }
        }

        log.info("HLS回放地址获取完成, 成功{}个/共{}个", result.size(), cameraIndexCodes.size());
        return result;
    }

    @Override
    public CameraPlaybackUrlVO getLocalHlsPlaybackUrl(String cameraIndexCode, String beginTime, String endTime) throws Exception {
        if (StringUtils.isBlank(cameraIndexCode)) {
            log.warn("获取本地HLS回放地址失败: cameraIndexCode为空");
            return null;
        }

        // 解析并校验回放时间段（默认结束时间为当前时间，开始时间为结束时间前3天）
        PlaybackTimeRange range = resolvePlaybackTimeRange(beginTime, endTime);

        log.info("开始获取摄像头[{}]的本地HLS回放地址, beginTime={}, endTime={}",
                cameraIndexCode, range.getBeginTime(), range.getEndTime());

        // 1. 请求海康SDK获取RTSP回放地址
        PlaybackUrlRequest request = buildPlaybackUrlRequest(cameraIndexCode, "rtsp", range);
        String requestBody = JSON.toJSONString(request);
        log.info("请求海康摄像头RTSP回放地址, cameraIndexCode={}, body={}", cameraIndexCode, requestBody);

        String responseBody = hikvisionUtil.doPostJson(CAMERA_PLAYBACK_URL_API, requestBody);
        if (!hikvisionUtil.isSuccess(responseBody)) {
            log.error("获取摄像头[{}]RTSP回放地址失败, 海康响应: {}", cameraIndexCode, responseBody);
            return null;
        }

        JSONObject dataJson = hikvisionUtil.getResponseData(responseBody);
        if (dataJson == null || StringUtils.isBlank(dataJson.getString("url"))) {
            log.warn("摄像头[{}] 海康未返回RTSP回放地址", cameraIndexCode);
            return null;
        }
        String rtspUrl = dataJson.getString("url");
        log.info("摄像头[{}] RTSP回放地址获取成功", cameraIndexCode);

        // 2. 通过HLS流管理器获取本地HLS流：流标识按摄像头+时间段唯一，避免与实时流冲突，同一回放时段直接复用
        String streamKey = "pb_" + cameraIndexCode + "_" + range.getBeginMillis() + "_" + range.getEndMillis();
        CameraHlsStream stream = hlsStreamManager.getOrCreate(streamKey, cameraIndexCode, rtspUrl);
        if (stream == null) {
            log.error("摄像头[{}] HLS回放转码任务创建失败", cameraIndexCode);
            return null;
        }

        // 3. 等待HLS流就绪（首个切片已生成），超时仍返回地址由前端自行重试
        boolean ready = stream.awaitReady(hlsProperties.getReadyWaitSeconds());
        if (!ready) {
            if (!stream.isRunning()) {
                log.error("摄像头[{}] HLS回放转码启动失败: {}", cameraIndexCode, stream.getErrorMessage());
                hlsStreamManager.removeStream(streamKey);
                return null;
            }
            log.warn("摄像头[{}] HLS回放流未在{}s内就绪, 仍返回地址由前端重试",
                    cameraIndexCode, hlsProperties.getReadyWaitSeconds());
        }

        // 4. 返回本地HLS相对回放地址（由Controller拼装完整访问地址）
        log.info("摄像头[{}] 本地HLS回放地址: {}", cameraIndexCode, stream.getHlsRelativeUrl());
        CameraPlaybackUrlVO vo = new CameraPlaybackUrlVO();
        vo.setCameraIndexCode(cameraIndexCode);
        vo.setUrl(stream.getHlsRelativeUrl());
        vo.setUuid(dataJson.getString("uuid"));
        return vo;
    }

    /**
     * 构建获取回放地址的请求参数
     *
     * @param cameraIndexCode 摄像头唯一编码
     * @param protocol        取流协议（hls/rtsp等）
     * @param range           已校验的回放时间段
     */
    private PlaybackUrlRequest buildPlaybackUrlRequest(String cameraIndexCode, String protocol, PlaybackTimeRange range) {
        PlaybackUrlRequest request = new PlaybackUrlRequest();
        request.setCameraIndexCode(cameraIndexCode);
        request.setRecordLocation("0");
        request.setProtocol(protocol);
        request.setTransmode(1);
        request.setBeginTime(range.getBeginTime());
        request.setEndTime(range.getEndTime());
        return request;
    }

    /**
     * 解析并校验回放时间段
     * <p>默认结束时间为当前时间、开始时间为结束时间前3天；开始时间不得晚于结束时间，
     * 且两者相差不超过3天（海康接口限制）。</p>
     *
     * @param beginTime 开始时间字符串（可为空，支持 yyyy-MM-dd HH:mm:ss 或 ISO8601）
     * @param endTime   结束时间字符串（可为空，支持 yyyy-MM-dd HH:mm:ss 或 ISO8601）
     * @return 已格式化为 ISO8601 的回放时间段
     */
    private PlaybackTimeRange resolvePlaybackTimeRange(String beginTime, String endTime) {
        ZonedDateTime end = parsePlaybackTime(endTime);
        if (end == null) {
            end = ZonedDateTime.now();
        }
        ZonedDateTime begin = parsePlaybackTime(beginTime);
        if (begin == null) {
            begin = end.minusDays(PLAYBACK_MAX_SPAN_DAYS);
        }

        if (begin.isAfter(end)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        if (end.isAfter(begin.plusDays(PLAYBACK_MAX_SPAN_DAYS))) {
            throw new IllegalArgumentException("开始时间与结束时间相差不能超过3天");
        }

        return new PlaybackTimeRange(
                begin.format(ISO_OFFSET_FORMATTER),
                end.format(ISO_OFFSET_FORMATTER),
                begin.toInstant().toEpochMilli(),
                end.toInstant().toEpochMilli());
    }

    /**
     * 解析时间字符串为带时区的 ZonedDateTime
     * <p>兼容 ISO8601（带时区/带毫秒）与 yyyy-MM-dd HH:mm:ss 等常见格式，无时区按系统默认时区处理。</p>
     *
     * @param value 时间字符串，为空返回null
     * @return ZonedDateTime，解析失败抛出 IllegalArgumentException
     */
    private ZonedDateTime parsePlaybackTime(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String v = value.trim();
        // 带时区的 ISO8601，例如 2017-06-14T00:00:00.000+08:00
        try {
            return ZonedDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
            // 尝试下一种格式
        }
        // 无时区的常见格式，按系统默认时区处理
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                return LocalDateTime.parse(v, DateTimeFormatter.ofPattern(pattern))
                        .atZone(ZoneId.systemDefault());
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        throw new IllegalArgumentException("时间格式不正确(支持 yyyy-MM-dd HH:mm:ss 或 ISO8601): " + value);
    }

    /**
     * 回放时间段：已格式化的 ISO8601 字符串 + 起止毫秒时间戳（用作本地HLS流标识）
     */
    private static class PlaybackTimeRange {
        private final String beginTime;
        private final String endTime;
        private final long beginMillis;
        private final long endMillis;

        PlaybackTimeRange(String beginTime, String endTime, long beginMillis, long endMillis) {
            this.beginTime = beginTime;
            this.endTime = endTime;
            this.beginMillis = beginMillis;
            this.endMillis = endMillis;
        }

        String getBeginTime() {
            return beginTime;
        }

        String getEndTime() {
            return endTime;
        }

        long getBeginMillis() {
            return beginMillis;
        }

        long getEndMillis() {
            return endMillis;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncOnlineStatus() {
        log.info("开始从海康平台同步监控点在线状态...");

        // 1. 逐页从海康拉取全部在线状态数据
        Map<String, Integer> onlineStatusMap = fetchAllOnlineStatus();

        if (onlineStatusMap.isEmpty()) {
            log.warn("海康未返回任何在线状态数据，跳过同步");
            return 0;
        }

        // 2. 查询数据库中全部摄像头
        List<CameraResource> allCameras = list(new LambdaQueryWrapper<CameraResource>()
                .select(CameraResource::getId, CameraResource::getIndexCode, CameraResource::getOnline));

        // 3. 根据海康返回的在线状态更新
        int updatedCount = 0;
        List<CameraResource> toUpdate = new ArrayList<>();
        for (CameraResource camera : allCameras) {
            Integer onlineStatus = onlineStatusMap.get(camera.getIndexCode());
            if (onlineStatus != null) {
                // 只有状态变化时才更新
                if (!onlineStatus.equals(camera.getOnline())) {
                    camera.setOnline(onlineStatus);
                    camera.setGmtModified(new Date());
                    toUpdate.add(camera);
                }
            }
        }

        // 4. 批量更新
        if (!toUpdate.isEmpty()) {
            updateBatchById(toUpdate);
            updatedCount = toUpdate.size();
        }

        log.info("监控点在线状态同步完成, 海康返回{}条, 更新{}条, 库中共{}条",
                onlineStatusMap.size(), updatedCount, allCameras.size());
        return updatedCount;
    }

    /**
     * 逐页从海康拉取全部监控点在线状态
     *
     * @return indexCode -> online 的映射
     */
    private Map<String, Integer> fetchAllOnlineStatus() {
        Map<String, Integer> statusMap = new HashMap<>();
        int pageNo = 1;
        boolean hasMore = true;

        while (hasMore) {
            CameraOnlineRequest request = new CameraOnlineRequest();
            request.setPageNo(pageNo);
            request.setPageSize(PAGE_SIZE);

            try {
                String requestBody = JSON.toJSONString(request);
                log.info("请求海康监控点在线状态, pageNo={}, pageSize={}", pageNo, PAGE_SIZE);

                String responseBody = hikvisionUtil.doPostJson(CAMERA_ONLINE_API, requestBody);

                if (!hikvisionUtil.isSuccess(responseBody)) {
                    log.error("海康在线状态查询失败: {}", responseBody);
                    throw new RuntimeException("海康在线状态查询失败: " + responseBody);
                }

                JSONObject dataJson = hikvisionUtil.getResponseData(responseBody);
                if (dataJson == null) {
                    log.warn("海康返回的data为空");
                    break;
                }

                CameraOnlineResponse response = dataJson.toJavaObject(CameraOnlineResponse.class);
                List<CameraOnlineResponse.OnlineItem> onlineList = response.getList();

                if (onlineList == null || onlineList.isEmpty()) {
                    log.info("海康在线状态列表为空，拉取结束");
                    break;
                }

                for (CameraOnlineResponse.OnlineItem item : onlineList) {
                    if (item.getIndexCode() != null && item.getOnline() != null) {
                        statusMap.put(item.getIndexCode(), item.getOnline());
                    }
                }

                log.info("第{}页在线状态拉取完成, 本页{}条, 累计{}条",
                        pageNo, onlineList.size(), statusMap.size());

                // 判断是否还有下一页
                int total = response.getTotal() != null ? response.getTotal() : 0;
                if (pageNo * PAGE_SIZE >= total) {
                    hasMore = false;
                } else {
                    pageNo++;
                }

            } catch (Exception e) {
                log.error("拉取海康在线状态异常, pageNo={}", pageNo, e);
                throw new RuntimeException("拉取海康在线状态失败: " + e.getMessage(), e);
            }
        }

        log.info("海康在线状态拉取完成, 共获取{}条", statusMap.size());
        return statusMap;
    }

    @Override
    public List<CameraListVO> getCameraList() {
        log.info("查询table_camera_resource表中全部摄像头列表");
        List<CameraResource> cameraList = list();
        Map<String, String> regionNameMap = resolveRegionNameMap(cameraList);
        List<CameraListVO> result = new ArrayList<>(cameraList.size());
        for (CameraResource camera : cameraList) {
            CameraListVO vo = cameraToVO(camera);
            vo.setRegionName(regionNameMap.get(camera.getRegionIndexCode()));
            result.add(vo);
        }
        log.info("查询摄像头列表完成, 共{}条", result.size());
        return result;
    }

    @Override
    public List<CameraListVO> getCameraListForExport() {
        log.info("查询导出用摄像头列表");
        List<CameraResource> cameraList = list();

        // 区域名称联动table_region_resource表
        Map<String, String> regionNameMap = resolveRegionNameMap(cameraList);

        List<CameraListVO> result = new ArrayList<>(cameraList.size());
        for (CameraResource camera : cameraList) {
            CameraListVO vo = cameraToVO(camera);
            vo.setRegionName(regionNameMap.get(camera.getRegionIndexCode()));
            result.add(vo);
        }
        log.info("查询导出用摄像头列表完成, 共{}条", result.size());
        return result;
    }

    @Override
    public IPage<CameraListVO> getCameraPage(CameraResourcePageDto dto) {
        log.info("分页查询摄像头列表, pageNo={}, pageSize={}, indexCode={}, name={}, regionName={}, treatyType={}, installLocation={}, online={}, cameraType={}",
                dto.getPageNo(), dto.getPageSize(), dto.getIndexCode(), dto.getName(), dto.getRegionName(),
                dto.getTreatyType(), dto.getInstallLocation(), dto.getOnline(), dto.getCameraType());

        LambdaQueryWrapper<CameraResource> wrapper = new LambdaQueryWrapper<CameraResource>()
                .eq(StringUtils.isNotBlank(dto.getIndexCode()), CameraResource::getIndexCode, dto.getIndexCode())
                .like(StringUtils.isNotBlank(dto.getName()), CameraResource::getName, dto.getName())
                .like(StringUtils.isNotBlank(dto.getInstallLocation()), CameraResource::getInstallLocation, dto.getInstallLocation())
                .eq(dto.getOnline() != null, CameraResource::getOnline, dto.getOnline())
                .eq(dto.getCameraType() != null, CameraResource::getCameraType, dto.getCameraType())
                .eq(StringUtils.isNotBlank(dto.getTreatyType()), CameraResource::getTreatyType, dto.getTreatyType())
                .orderByAsc(CameraResource::getId);

        // 区域名称过滤 —— 联动table_region_resource表
        if (StringUtils.isNotBlank(dto.getRegionName())) {
            List<String> matchedRegionCodes = regionResourceMapper.selectList(
                            new LambdaQueryWrapper<RegionResource>()
                                    .like(RegionResource::getName, dto.getRegionName())
                                    .select(RegionResource::getIndexCode))
                    .stream()
                    .map(RegionResource::getIndexCode)
                    .collect(Collectors.toList());
            if (matchedRegionCodes.isEmpty()) {
                return emptyPage(dto);
            }
            wrapper.in(CameraResource::getRegionIndexCode, matchedRegionCodes);
        }

        IPage<CameraResource> cameraPage = page(new Page<>(dto.getPageNo(), dto.getPageSize()), wrapper);

        Map<String, String> regionNameMap = resolveRegionNameMap(cameraPage.getRecords());

        List<CameraListVO> voList = new ArrayList<>(cameraPage.getRecords().size());
        for (CameraResource camera : cameraPage.getRecords()) {
            CameraListVO vo = cameraToVO(camera);
            vo.setRegionName(regionNameMap.get(camera.getRegionIndexCode()));
            voList.add(vo);
        }

        IPage<CameraListVO> resultPage = new Page<>(dto.getPageNo(), dto.getPageSize(), cameraPage.getTotal());
        resultPage.setRecords(voList);

        log.info("分页查询摄像头列表完成, 共{}条, 当前页{}条", cameraPage.getTotal(), voList.size());
        return resultPage;
    }

    /**
     * 构建空分页结果
     */
    private IPage<CameraListVO> emptyPage(CameraResourcePageDto dto) {
        IPage<CameraListVO> emptyPage = new Page<>(dto.getPageNo(), dto.getPageSize());
        emptyPage.setRecords(Collections.emptyList());
        emptyPage.setTotal(0);
        log.info("分页查询摄像头列表完成, 未匹配到任何摄像头, 返回空");
        return emptyPage;
    }

    /**
     * 将摄像头实体转换为列表VO
     *
     * @param camera 摄像头实体
     * @return 摄像头列表VO
     */
    private CameraListVO cameraToVO(CameraResource camera) {
        CameraListVO vo = new CameraListVO();
        vo.setIndexCode(camera.getIndexCode());
        vo.setName(camera.getName());
        vo.setCameraType(camera.getCameraType());
        vo.setInstallLocation(camera.getInstallLocation());
        vo.setRegionIndexCode(camera.getRegionIndexCode());
        vo.setRegionName(camera.getRegionName());
        vo.setLongitude(bigDecimalToStr(camera.getLongitude()));
        vo.setLatitude(bigDecimalToStr(camera.getLatitude()));
        vo.setChannelType(camera.getChannelType());
        vo.setOnline(camera.getOnline());
        vo.setExternalIndexCode(camera.getExternalIndexCode());
        vo.setCreateTime(camera.getCreateTime());
        vo.setUpdateTime(camera.getUpdateTime());
        return vo;
    }

    @Override
    public List<CameraListVO> listByRegion(String regionIndexCode) {
        if (regionIndexCode == null || regionIndexCode.trim().isEmpty()) {
            log.warn("查询区域摄像头失败: regionIndexCode为空");
            return Collections.emptyList();
        }
        log.info("查询区域[{}]下直属摄像头列表", regionIndexCode);
        List<CameraResource> cameraList = list(new LambdaQueryWrapper<CameraResource>()
                .eq(CameraResource::getRegionIndexCode, regionIndexCode)
                .orderByAsc(CameraResource::getName));
        List<CameraListVO> result = new ArrayList<>(cameraList.size());
        for (CameraResource camera : cameraList) {
            result.add(cameraToVO(camera));
        }
        log.info("查询区域[{}]摄像头列表完成, 共{}条", regionIndexCode, result.size());
        return result;
    }

    @Override
    public List<CameraCoordinateGroupVO> getCameraCoordinateGroup() {
        log.info("开始构建摄像头坐标分组分布");
        // 查询全部摄像头，并按坐标（经度+纬度）聚合
        Map<String, List<CameraListVO>> coordinateMap = new LinkedHashMap<>();
        list().forEach(camera -> {
                    CameraListVO vo = cameraToVO(camera);
                    String longitude = formatCoordinate(vo.getLongitude());
                    String latitude = formatCoordinate(vo.getLatitude());
                    if (longitude == null || latitude == null) {
                        return;
                    }
                    vo.setLongitude(longitude);
                    vo.setLatitude(latitude);
                    String key = longitude + "," + latitude;
                    coordinateMap.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(vo);
                });

        // 3. 组装返回VO并按经度、纬度排序
        List<CameraCoordinateGroupVO> result = new ArrayList<>(coordinateMap.size());
        for (Map.Entry<String, List<CameraListVO>> entry : coordinateMap.entrySet()) {
            String[] lonLat = entry.getKey().split(",", 2);
            CameraCoordinateGroupVO vo = new CameraCoordinateGroupVO();
            vo.setLongitude(lonLat[0]);
            vo.setLatitude(lonLat[1]);
            vo.setCameraList(entry.getValue());
            vo.setCameraCount(entry.getValue().size());
            result.add(vo);
        }
        result.sort((o1, o2) -> {
            int cmp = compareCoordinate(o1.getLongitude(), o2.getLongitude());
            if (cmp != 0) {
                return cmp;
            }
            return compareCoordinate(o1.getLatitude(), o2.getLatitude());
        });
        log.info("摄像头坐标分组构建完成, 共{}组", result.size());
        return result;
    }

    /**
     * 比较两个坐标字符串（按数值大小，解析失败时视为0）
     *
     * @param c1 坐标1
     * @param c2 坐标2
     * @return 比较结果
     */
    private int compareCoordinate(String c1, String c2) {
        return parseCoordinate(c1).compareTo(parseCoordinate(c2));
    }

    /**
     * 将坐标字符串解析为BigDecimal，解析失败时返回0
     *
     * @param coordinate 坐标字符串
     * @return 解析结果
     */
    private BigDecimal parseCoordinate(String coordinate) {
        try {
            return new BigDecimal(StringUtils.trimToEmpty(coordinate));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 格式化坐标字符串
     * <p>数据库中的坐标可能以科学计数法存储（如"1.16155425E2"），
     * 这里统一转为普通十进制字符串（如"116.155425"），避免返回给前端时出现科学计数法。</p>
     *
     * @param coordinate 坐标字符串
     * @return 格式化后的普通十进制字符串；空值返回null，无法解析时原样返回
     */
    private String formatCoordinate(String coordinate) {
        if (StringUtils.isBlank(coordinate)) {
            return null;
        }
        try {
            return new BigDecimal(coordinate.trim()).toPlainString();
        } catch (NumberFormatException e) {
            return coordinate.trim();
        }
    }

    @Override
    public List<RegionCameraTreeVO> getRegionCameraGroup() {
        log.info("开始构建区域摄像头分组信息");
        // 1. 查询区域资源表
        List<RegionResource> allRegions = regionResourceMapper.selectList(null);
        if (allRegions == null || allRegions.isEmpty()) {
            log.warn("区域资源表为空, 返回空分组");
            return Collections.emptyList();
        }

        // 2. 查询全部摄像头并按区域编码聚合（regionIndexCode -> 摄像头列表）
        Map<String, List<CameraListVO>> regionCameraMap = list().stream()
                .filter(camera -> StringUtils.isNotBlank(camera.getRegionIndexCode()))
                .collect(Collectors.groupingBy(CameraResource::getRegionIndexCode,
                        Collectors.mapping(this::cameraToVO, Collectors.toList())));

        // 3. 递归构建区域树
        // 根节点判断：parentIndexCode 为空，或 parentIndexCode 不在任何区域的 indexCode 集合中
        // （兼容海康根节点标识 root000000、-1、空，避免写死导致树丢失）
        Set<String> allIndexCodes = allRegions.stream()
                .map(RegionResource::getIndexCode)
                .collect(Collectors.toSet());

        List<RegionCameraTreeVO> result = new ArrayList<>();
        for (RegionResource region : allRegions) {
            boolean isRoot = StringUtils.isBlank(region.getParentIndexCode())
                    || !allIndexCodes.contains(region.getParentIndexCode());
            if (isRoot) {
                result.add(convertRegionTree(region, allRegions, regionCameraMap));
            }
        }
        log.info("区域摄像头分组构建完成, 根节点{}个", result.size());
        return result;
    }

    /**
     * 根据摄像头列表解析区域编码 -> 区域名称映射（联动table_region_resource表）
     */
    private Map<String, String> resolveRegionNameMap(List<CameraResource> cameraList) {
        Set<String> regionCodes = cameraList.stream()
                .map(CameraResource::getRegionIndexCode)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (regionCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return regionResourceMapper.selectList(new LambdaQueryWrapper<RegionResource>()
                        .in(RegionResource::getIndexCode, regionCodes))
                .stream()
                .collect(Collectors.toMap(RegionResource::getIndexCode, RegionResource::getName, (a, b) -> a));
    }

    /**
     * 将区域实体转换为区域摄像头分组节点，并递归填充子区域及videoList
     *
     * @param region           区域实体
     * @param allRegions       全部区域列表
     * @param regionCameraMap  区域编码 -> 摄像头列表映射
     * @return 区域摄像头分组节点
     */
    private RegionCameraTreeVO convertRegionTree(RegionResource region, List<RegionResource> allRegions,
                                                 Map<String, List<CameraListVO>> regionCameraMap) {
        RegionCameraTreeVO vo = regionToTreeVO(region);
        vo.setVideoList(regionCameraMap.getOrDefault(region.getIndexCode(), Collections.emptyList()));
        // 递归子区域
        for (RegionResource child : allRegions) {
            if (region.getIndexCode().equals(child.getParentIndexCode())) {
                vo.getChildren().add(convertRegionTree(child, allRegions, regionCameraMap));
            }
        }
        return vo;
    }

    /**
     * 将区域实体转换为区域摄像头分组节点VO（不含videoList）
     */
    private RegionCameraTreeVO regionToTreeVO(RegionResource region) {
        RegionCameraTreeVO vo = new RegionCameraTreeVO();
        vo.setIndexCode(region.getIndexCode());
        vo.setName(region.getName());
        vo.setRegionPath(region.getRegionPath());
        vo.setParentIndexCode(region.getParentIndexCode());
        vo.setAvailable(region.getAvailable());
        vo.setLeaf(region.getLeaf());
        vo.setCascadeCode(region.getCascadeCode());
        vo.setCascadeType(region.getCascadeType());
        vo.setCatalogType(region.getCatalogType());
        vo.setExternalIndexCode(region.getExternalIndexCode());
        vo.setSort(region.getSort());
        vo.setLocalQuantity(region.getLocalQuantity());
        vo.setTotalQuantity(region.getTotalQuantity());
        return vo;
    }
}
