package org.jeecg.modules.bems.hikvision.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.bems.hikvision.entity.CameraResource;
import org.jeecg.modules.bems.hikvision.dto.CameraCoordinateGroupVO;
import org.jeecg.modules.bems.hikvision.dto.CameraListVO;
import org.jeecg.modules.bems.hikvision.dto.CameraPlayUrlVO;
import org.jeecg.modules.bems.hikvision.dto.CameraResourcePageDto;
import org.jeecg.modules.bems.hikvision.dto.RegionCameraTreeVO;

import java.util.List;

/**
 * 摄像头资源同步服务接口
 *
 * @author bems
 */
public interface ICameraResourceService extends IService<CameraResource> {

    /**
     * 从海康平台全量拉取摄像头数据并同步到数据库
     * <p>同步逻辑：先清空表内全部数据，再逐页拉取海康数据批量插入。</p>
     *
     * @return 同步成功的记录数
     */
    int syncFromHikvision();

    /**
     * 根据摄像头唯一编码列表，直接从海康平台获取HLS播放地址
     * <p>调用海康OpenAPI（previewURLs，protocol=hls）获取播放地址，由海康流媒体服务直接输出HLS，
     * 服务端不再做本地拉流转码。</p>
     *
     * @param cameraIndexCodes 摄像头唯一编码列表（1个或多个）
     * @return 每个摄像头的HLS播放地址列表
     */
    List<CameraPlayUrlVO> getPlayUrls(List<String> cameraIndexCodes);

    /**
     * 根据单个摄像头唯一编码，获取本地HLS播放地址
     * <p>流程：海康SDK获取RTMP地址 -> JavaCV本地转码为HLS -> 返回 /hls/{编码}/index.m3u8 相对地址。
     * 同一摄像头正在拉流时直接复用已生成的HLS流，不做重复转码。</p>
     *
     * @param cameraIndexCode 摄像头唯一编码
     * @return 本地HLS播放地址（含摄像头编码与相对地址），失败返回null
     */
    CameraPlayUrlVO getLocalHlsPlayUrl(String cameraIndexCode) throws Exception;

    /**
     * 从海康平台查询监控点在线状态并更新到数据库
     * <p>逐页拉取海康在线状态数据，根据 indexCode 匹配更新 table_camera_resource 的 online 字段。</p>
     *
     * @return 更新的记录数
     */
    int syncOnlineStatus();

    /**
     * 查询本地数据库中全部摄像头列表
     *
     * @return 摄像头列表
     */
    List<CameraListVO> getCameraList();

    /**
     * 查询导出用摄像头列表：全部摄像头，区域名称联动 table_region_resource 表
     *
     * @return 摄像头列表
     */
    List<CameraListVO> getCameraListForExport();

    /**
     * 分页查询摄像头列表
     * <p>支持按名称、唯一编码、区域名称、接入协议、安装位置、在线状态、监控点类型检索，条件为空查全部。</p>
     *
     * @param dto 分页查询参数
     * @return 分页摄像头列表
     */
    IPage<CameraListVO> getCameraPage(CameraResourcePageDto dto);

    /**
     * 获取区域摄像头分组信息
     * <p>先构建区域树，再在每个区域节点下挂载该区域直属的摄像头列表（videoList），
     * 返回结构与海康区域树一致，多出videoList项。</p>
     *
     * @return 区域摄像头分组树根节点列表
     */
    List<RegionCameraTreeVO> getRegionCameraGroup();

    /**
     * 获取摄像头坐标分组分布
     * <p>统计全部摄像头，按经度、纬度聚合，返回每个坐标下的摄像头数量与摄像头列表。</p>
     *
     * @return 坐标分组列表
     */
    List<CameraCoordinateGroupVO> getCameraCoordinateGroup();

    /**
     * 根据摄像头所属区域编码查询摄像头列表
     *
     * @param regionIndexCode 区域编码
     * @return 该区域下直属摄像头列表
     */
    List<CameraListVO> listByRegion(String regionIndexCode);
}
