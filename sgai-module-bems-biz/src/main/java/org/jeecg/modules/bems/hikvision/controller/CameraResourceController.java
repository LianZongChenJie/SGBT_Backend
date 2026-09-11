package org.jeecg.modules.bems.hikvision.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.hikvision.dto.CameraCoordinateGroupVO;
import org.jeecg.modules.bems.hikvision.dto.CameraListVO;
import org.jeecg.modules.bems.hikvision.dto.CameraPlayUrlVO;
import org.jeecg.modules.bems.hikvision.dto.CameraResourcePageDto;
import org.jeecg.modules.bems.hikvision.dto.RegionCameraTreeVO;
import org.jeecg.modules.bems.hikvision.service.ICameraResourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.jeecgframework.poi.excel.ExcelExportUtil;
import org.jeecgframework.poi.excel.entity.ExportParams;
import org.jeecgframework.poi.excel.entity.enmus.ExcelType;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

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
     * <p>流程：前端传入1个或多个摄像头唯一编码 -> 调用海康OpenAPI直接获取HLS播放地址 ->
     * 返回海康流媒体服务提供的完整播放地址（服务端不做本地拉流转码）。</p>
     *
     * @param body 请求体，其中 cameraIndexCode 为摄像头唯一编码列表
     * @return 播放地址列表（每项包含 cameraIndexCode 和 url）
     */
    @PostMapping("/playUrls")
    @ApiOperation(value = "获取摄像头HLS播放地址", notes = "传入摄像头唯一编码列表，返回海康平台直接提供的HLS播放地址")
    public Result<List<CameraPlayUrlVO>> getPlayUrls(@RequestBody Map<String, List<String>> body) {
        try {
            List<String> cameraIndexCodes = body.get("cameraIndexCode");
            List<CameraPlayUrlVO> playUrls = cameraResourceService.getPlayUrls(cameraIndexCodes);
            return Result.ok(playUrls);
        } catch (Exception e) {
            log.error("获取摄像头播放地址失败", e);
            return Result.error("获取摄像头播放地址失败: " + e.getMessage());
        }
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
