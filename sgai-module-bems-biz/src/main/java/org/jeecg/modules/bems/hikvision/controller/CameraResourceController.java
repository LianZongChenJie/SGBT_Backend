package org.jeecg.modules.bems.hikvision.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.hikvision.config.HlsProperties;
import org.jeecg.modules.bems.hikvision.dto.CameraCoordinateGroupVO;
import org.jeecg.modules.bems.hikvision.dto.CameraListVO;
import org.jeecg.modules.bems.hikvision.dto.CameraPlaybackUrlVO;
import org.jeecg.modules.bems.hikvision.dto.CameraPlayUrlVO;
import org.jeecg.modules.bems.hikvision.dto.CameraResourcePageDto;
import org.jeecg.modules.bems.hikvision.dto.RegionCameraTreeVO;
import org.jeecg.modules.bems.hikvision.service.ICameraResourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.jeecgframework.poi.excel.ExcelExportUtil;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;

/**
 * 摄像头资源管理控制器
 * <p>触发从海康平台全量拉取摄像头数据并同步到本地数据库。
 * 同步策略：先清空表，再全量导入。</p>
 *
 * @author bems
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/bems/hikvision/camera")
@Api(tags = "海康摄像头资源管理")
public class CameraResourceController {

    private final ICameraResourceService cameraResourceService;

    /**
     * HLS转码相关配置（含 publicBaseUrl：前端可访问的后端基础地址）
     */
    private final HlsProperties hlsProperties;

    /**
     * 触发全量同步海康摄像头数据
     * <p>请求无需参数，内部使用固定参数逐页拉取海康全部摄像头。
     * 先清空 table_camera_resource 表，再批量插入新数据。</p>
     *
     * @return 同步结果（包含同步条数）
     */
    @PostMapping("/sync")
    @ApiOperation(value = "全量同步海康摄像头数据", notes = "先清空本地表，再从海康平台全量拉取摄像头数据导入")
    public Result<Integer> syncCameras() {
        try {
            int count = cameraResourceService.syncFromHikvision();
            return Result.ok(count);
        } catch (Exception e) {
            log.error("同步摄像头数据失败", e);
            return Result.error("同步摄像头数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取摄像头HLS播放地址
     * <p>流程：前端传入1个摄像头唯一编码 -> 调用海康OpenAPI直接获取HLS播放地址 ->
     * 返回海康流媒体服务提供的完整播放地址（服务端不做本地拉流转码）。</p>
     *
     * @param cameraIndexCode 摄像头唯一编码
     * @return 播放地址（包含 cameraIndexCode 和 url）
     */
    @GetMapping("/playUrls")
    @ApiOperation(value = "获取摄像头HLS播放地址", notes = "传入单个摄像头唯一编码，返回海康平台直接提供的HLS播放地址")
    public Result<CameraPlayUrlVO> getPlayUrls(String cameraIndexCode) {
        try {
            if (StringUtils.isBlank(cameraIndexCode)) {
                return Result.error("摄像头唯一编码不能为空");
            }
            List<CameraPlayUrlVO> playUrls = cameraResourceService.getPlayUrls(Collections.singletonList(cameraIndexCode));
            return Result.ok(playUrls.isEmpty() ? null : playUrls.get(0));
        } catch (Exception e) {
            log.error("获取摄像头播放地址失败", e);
            return Result.error("获取摄像头播放地址失败: " + e.getMessage());
        }
    }

    /**
     * 获取摄像头本地HLS播放地址
     * <p>流程：前端传入1个摄像头唯一编码 -> 海康SDK获取RTSP地址 -> JavaCV本地转码为HLS ->
     * 返回 /hls/{编码}/index.m3u8 完整访问地址。同一摄像头正在拉流时直接复用已生成的HLS流，不做重复转码。</p>
     *
     * @param cameraIndexCode 摄像头唯一编码
     * @param request         当前请求，用于拼接HLS访问地址
     * @return 播放地址（包含 cameraIndexCode 和 url）
     */
    @GetMapping("/localPlayUrl")
    @ApiOperation(value = "获取摄像头本地HLS播放地址", notes = "传入单个摄像头唯一编码，海康RTSP经本地转码为HLS后返回完整播放地址")
    public Result<CameraPlayUrlVO> getLocalPlayUrl(String cameraIndexCode, HttpServletRequest request) {
        try {
            if (StringUtils.isBlank(cameraIndexCode)) {
                return Result.error("摄像头唯一编码不能为空");
            }
            CameraPlayUrlVO vo = cameraResourceService.getLocalHlsPlayUrl(cameraIndexCode);
            if (vo != null && vo.getUrl() != null && vo.getUrl().startsWith("/")) {
                vo.setUrl(buildBaseUrl(request) + vo.getUrl());
            }
            return Result.ok(vo);
        } catch (Exception e) {
            log.error("获取摄像头本地HLS播放地址失败", e);
            return Result.error("获取摄像头本地HLS播放地址失败: " + e.getMessage());
        }
    }

    /**
     * 获取摄像头海康HLS回放地址
     * <p>流程：前端传入1个摄像头唯一编码与回放时间段 -> 调用海康OpenAPI（playbackURLs，protocol=hls）
     * 直接获取回放地址 -> 返回海康流媒体服务提供的完整回放地址（服务端不做本地拉流转码）。</p>
     * <p>开始/结束时间为空时默认结束时间为当前时间、开始时间为结束时间前3天；两者相差不超过3天。</p>
     *
     * @param cameraIndexCode 摄像头唯一编码
     * @param beginTime       开始时间（可选，支持 yyyy-MM-dd HH:mm:ss 或 ISO8601）
     * @param endTime         结束时间（可选，支持 yyyy-MM-dd HH:mm:ss 或 ISO8601）
     * @return 回放地址（包含 cameraIndexCode、url、uuid 及录像片段列表）
     */
    @GetMapping("/playbackUrls")
    @ApiOperation(value = "获取摄像头海康HLS回放地址", notes = "传入单个摄像头唯一编码与回放时间段（默认最近三天），返回海康平台直接提供的HLS回放地址")
    public Result<CameraPlaybackUrlVO> getPlaybackUrls(String cameraIndexCode, String beginTime, String endTime) {
        try {
            if (StringUtils.isBlank(cameraIndexCode)) {
                return Result.error("摄像头唯一编码不能为空");
            }
            List<CameraPlaybackUrlVO> playbackUrls =
                    cameraResourceService.getPlaybackUrls(Collections.singletonList(cameraIndexCode), beginTime, endTime);
            return Result.ok(playbackUrls.isEmpty() ? null : playbackUrls.get(0));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取摄像头回放地址失败", e);
            return Result.error("获取摄像头回放地址失败: " + e.getMessage());
        }
    }

    /**
     * 获取摄像头本地HLS回放地址
     * <p>流程：前端传入1个摄像头唯一编码与回放时间段 -> 海康SDK获取RTSP回放地址 -> JavaCV本地转码为HLS ->
     * 返回 /hls/{流标识}/index.m3u8 完整访问地址。同一摄像头同一时段正在拉流时直接复用已生成的HLS流，不做重复转码。</p>
     * <p>开始/结束时间为空时默认结束时间为当前时间、开始时间为结束时间前3天；两者相差不超过3天。</p>
     *
     * @param cameraIndexCode 摄像头唯一编码
     * @param beginTime       开始时间（可选，支持 yyyy-MM-dd HH:mm:ss 或 ISO8601）
     * @param endTime         结束时间（可选，支持 yyyy-MM-dd HH:mm:ss 或 ISO8601）
     * @param request         当前请求，用于拼接HLS访问地址
     * @return 回放地址（包含 cameraIndexCode 和 url）
     */
    @GetMapping("/localPlaybackUrl")
    @ApiOperation(value = "获取摄像头本地HLS回放地址", notes = "传入单个摄像头唯一编码与回放时间段（默认最近三天），海康RTSP回放流经本地转码为HLS后返回完整回放地址")
    public Result<CameraPlaybackUrlVO> getLocalPlaybackUrl(String cameraIndexCode, String beginTime, String endTime,
                                                           HttpServletRequest request) {
        try {
            if (StringUtils.isBlank(cameraIndexCode)) {
                return Result.error("摄像头唯一编码不能为空");
            }
            CameraPlaybackUrlVO vo = cameraResourceService.getLocalHlsPlaybackUrl(cameraIndexCode, beginTime, endTime);
            if (vo != null && vo.getUrl() != null && vo.getUrl().startsWith("/")) {
                vo.setUrl(buildBaseUrl(request) + vo.getUrl());
            }
            return Result.ok(vo);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取摄像头本地HLS回放地址失败", e);
            return Result.error("获取摄像头本地HLS回放地址失败: " + e.getMessage());
        }
    }

    /**
     * 构建HLS播放地址的完整访问基础地址
     * <p>微服务经网关访问时，请求Host只包含网关地址，不含服务路由前缀（如 /sgai-bems），
     * 必须补上前缀，否则前端按返回地址请求 /hls/** 会404。取值优先级：</p>
     * <ol>
     *   <li>配置 bems.hikvision.hls.public-base-url（完整地址，直接使用）；</li>
     *   <li>请求头 X-Forwarded-Prefix（网关透传的服务路由前缀）；</li>
     *   <li>配置 bems.hikvision.hls.url-prefix（服务路由前缀）；</li>
     * </ol>
     *
     * @param request 当前请求
     * @return 基础地址，如 http://47.95.156.86:59999/sgai-bems
     */
    private String buildBaseUrl(HttpServletRequest request) {
        // 1. 显式配置的完整基础地址优先
        if (StringUtils.isNotBlank(hlsProperties.getPublicBaseUrl())) {
            return StringUtils.removeEnd(hlsProperties.getPublicBaseUrl().trim(), "/");
        }

        // 2. 协议：网关终止TLS时请求协议为http，以 X-Forwarded-Proto 为准
        String scheme = firstValue(request.getHeader("X-Forwarded-Proto"));
        if (StringUtils.isBlank(scheme)) {
            scheme = request.getScheme();
        }

        // 3. 主机：网关转发场景优先取 X-Forwarded-Host，避免取到内网服务地址
        String host = firstValue(request.getHeader("X-Forwarded-Host"));
        if (StringUtils.isBlank(host)) {
            host = firstValue(request.getHeader("Host"));
        }
        if (StringUtils.isBlank(host)) {
            host = request.getServerName()
                    + (request.getServerPort() == 80 || request.getServerPort() == 443
                    ? "" : ":" + request.getServerPort());
        }

        // 4. 服务路由前缀：网关未透传时取配置值
        String prefix = firstValue(request.getHeader("X-Forwarded-Prefix"));
        if (StringUtils.isBlank(prefix)) {
            prefix = hlsProperties.getUrlPrefix();
        }

        String baseUrl = scheme + "://" + host;
        if (StringUtils.isNotBlank(prefix)) {
            baseUrl += prefix.startsWith("/") ? prefix : "/" + prefix;
        }
        return StringUtils.removeEnd(baseUrl, "/");
    }

    /**
     * 取请求头首值：网关透传的 X-Forwarded-* 可能为逗号分隔的多值（如 "a, b"），只取第一段
     */
    private String firstValue(String headerValue) {
        if (StringUtils.isBlank(headerValue)) {
            return null;
        }
        int comma = headerValue.indexOf(',');
        return (comma > -1 ? headerValue.substring(0, comma) : headerValue).trim();
    }

    /**
     * 同步监控点在线状态
     * <p>从海康逐页拉取全部监控点在线状态，根据唯一编码更新表中 online 字段。</p>
     *
     * @return 同步结果（包含更新条数）
     */
    @PostMapping("/syncOnlineStatus")
    @ApiOperation(value = "同步监控点在线状态", notes = "从海康平台拉取在线状态并更新到本地数据库")
    public Result<Integer> syncOnlineStatus() {
        try {
            int count = cameraResourceService.syncOnlineStatus();
            return Result.ok(count);
        } catch (Exception e) {
            log.error("同步监控点在线状态失败", e);
            return Result.error("同步监控点在线状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取全部摄像头列表
     * <p>从本地数据库查询全部摄像头数据并返回列表。</p>
     *
     * @return 摄像头列表
     */
    @GetMapping("/list")
    @ApiOperation(value = "获取全部摄像头列表", notes = "从本地数据库查询全部摄像头数据")
    public Result<List<CameraListVO>> getCameraList() {
        try {
            List<CameraListVO> list = cameraResourceService.getCameraList();
            return Result.ok(list);
        } catch (Exception e) {
            log.error("获取摄像头列表失败", e);
            return Result.error("获取摄像头列表失败: " + e.getMessage());
        }
    }

    /**
     * 导出摄像头信息
     * <p>导出 table_camera_resource 表全部摄像头数据，区域名称联动 table_region_resource 表。</p>
     */
    @GetMapping("/export")
    @ApiOperation(value = "导出摄像头信息", notes = "导出table_camera_resource表全部摄像头数据，区域名称联动table_region_resource表")
    public void exportCameras(HttpServletResponse response) throws Exception {
        List<CameraListVO> list = cameraResourceService.getCameraListForExport();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("content-disposition", "attachment;filename=" + URLEncoder.encode("摄像头信息.xlsx", "UTF-8"));
        try (Workbook workbook = ExcelExportUtil.exportExcel(
                new ExportParams("摄像头信息", "摄像头信息", ExcelType.XSSF),
                CameraListVO.class, list)) {
            workbook.write(response.getOutputStream());
        }
    }

    /**
     * 分页获取摄像头列表
     * <p>从本地数据库 table_camera_resource 表分页查询摄像头数据，支持按名称、唯一编码、区域名称、接入协议、
     * 安装位置、在线状态、监控点类型检索，条件为空查全部。区域名称联动 table_region_resource 表。</p>
     *
     * @param dto 分页查询参数
     * @return 分页摄像头列表
     */
    @GetMapping("/page")
    @ApiOperation(value = "分页获取摄像头列表", notes = "分页查询table_camera_resource表摄像头数据，支持按名称、唯一编码、区域名称、接入协议、安装位置、在线状态、监控点类型检索，区域名称联动table_region_resource表，条件为空查全部")
    public Result<IPage<CameraListVO>> getCameraPage(CameraResourcePageDto dto) {
        try {
            IPage<CameraListVO> page = cameraResourceService.getCameraPage(dto);
            return Result.ok(page);
        } catch (Exception e) {
            log.error("分页获取摄像头列表失败", e);
            return Result.error("分页获取摄像头列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据摄像头所属区域编码查询摄像头列表
     *
     * @param regionIndexCode 区域编码
     * @return 该区域下直属摄像头列表
     */
    @GetMapping("/listByRegion")
    @ApiOperation(value = "按区域编码查询摄像头列表", notes = "传入区域编码 regionIndexCode，返回该区域下直属摄像头列表")
    public Result<List<CameraListVO>> getCameraListByRegion(String regionIndexCode) {
        try {
            List<CameraListVO> list = cameraResourceService.listByRegion(regionIndexCode);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("按区域编码查询摄像头列表失败, regionIndexCode={}", regionIndexCode, e);
            return Result.error("按区域编码查询摄像头列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取区域摄像头分组信息
     * <p>先获取区域树，再在每个区域节点下挂载该区域直属的摄像头列表（videoList），
     * 返回结构与海康区域树一致，节点中额外包含 videoList 项。</p>
     *
     * @return 区域摄像头分组树根节点列表
     */
    @GetMapping("/packageGroup")
    @ApiOperation(value = "获取区域摄像头分组信息", notes = "先获取区域树，每个区域节点下挂载该区域直属的摄像头列表（videoList）")
    public Result<List<RegionCameraTreeVO>> getRegionCameraGroup() {
        try {
            List<RegionCameraTreeVO> list = cameraResourceService.getRegionCameraGroup();
            return Result.ok(list);
        } catch (Exception e) {
            log.error("获取区域摄像头分组信息失败", e);
            return Result.error("获取区域摄像头分组信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取本地摄像头坐标分组分布
     * <p>统计全部摄像头，按经度、纬度聚合本地 table_camera_resource 表数据，
     * 返回每个坐标下的摄像头数量与摄像头列表。</p>
     *
     * @return 摄像头坐标分组列表
     */
    @GetMapping("/coordinateGroupList")
    @ApiOperation(value = "获取本地摄像头坐标分组分布", notes = "按经度纬度聚合table_camera_resource表全部摄像头数据，返回每个坐标下的摄像头数量与列表")
    public Result<List<CameraCoordinateGroupVO>> getCameraCoordinateGroupList() {
        try {
            List<CameraCoordinateGroupVO> list = cameraResourceService.getCameraCoordinateGroup();
            return Result.ok(list);
        } catch (Exception e) {
            log.error("获取摄像头坐标分组分布失败", e);
            return Result.error("获取摄像头坐标分组分布失败: " + e.getMessage());
        }
    }

}
