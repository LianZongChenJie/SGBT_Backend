package org.jeecg.modules.bems.hikvision.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.hikvision.dto.RegionNodesRequest;
import org.jeecg.modules.bems.hikvision.dto.RegionNodesResponse;
import org.jeecg.modules.bems.hikvision.dto.RegionTreeVO;
import org.jeecg.modules.bems.hikvision.entity.CameraResource;
import org.jeecg.modules.bems.hikvision.entity.RegionResource;
import org.jeecg.modules.bems.hikvision.mapper.CameraResourceMapper;
import org.jeecg.modules.bems.hikvision.mapper.RegionResourceMapper;
import org.jeecg.modules.bems.hikvision.service.IRegionResourceService;
import org.jeecg.modules.bems.hikvision.util.HikvisionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 区域资源同步服务实现
 * <p>每次同步先清空表，再全量拉取海康数据批量插入。</p>
 *
 * @author bems
 */
@Slf4j
@Service
@AllArgsConstructor
public class RegionResourceServiceImpl extends ServiceImpl<RegionResourceMapper, RegionResource>
        implements IRegionResourceService {

    /** 海康区域查询API路径 */
    private static final String REGION_SEARCH_API = "/api/irds/v2/region/nodesByParams";

    /** 固定分页大小（最大1000） */
    private static final int PAGE_SIZE = 1000;

    /** 日期解析格式（兼容多种） */
    private static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss"
    };

    private final HikvisionUtil hikvisionUtil;

    private final CameraResourceMapper cameraResourceMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncFromHikvision() {
        log.info("开始从海康平台全量同步区域数据...");

        // 1. 先逐页从海康拉取全部数据
        List<RegionNodesResponse.RegionItem> allItems = fetchAllFromHikvision();

        // 2. 判断海康返回数据是否为空，为空则不处理
        if (allItems.isEmpty()) {
            log.warn("海康未返回任何区域数据，跳过同步，保留现有记录");
            return 0;
        }

        // 3. 清空表全部数据
        int deletedCount = baseMapper.delete(null);
        log.info("已清空区域资源表, 删除{}条记录", deletedCount);

        // 4. 批量转换并插入
        Date now = new Date();
        List<RegionResource> entityList = new ArrayList<>(allItems.size());
        for (RegionNodesResponse.RegionItem item : allItems) {
            RegionResource entity = convertToEntity(item);
            entity.setGmtCreate(now);
            entity.setGmtModified(now);
            entityList.add(entity);
        }

        // 达梦驱动对JDBC批量(executeBatch)支持有缺陷，大数据量时会抛index out of range，改为循环单条插入绕开该问题
        for (RegionResource entity : entityList) {
            baseMapper.insert(entity);
        }
        log.info("海康区域数据全量同步完成, 共同步{}条", entityList.size());
        return entityList.size();
    }

    /**
     * 逐页从海康拉取全部区域数据
     *
     * @return 全部区域列表
     */
    private List<RegionNodesResponse.RegionItem> fetchAllFromHikvision() {
        List<RegionNodesResponse.RegionItem> allItems = new ArrayList<>();
        int pageNo = 1;
        boolean hasMore = true;

        while (hasMore) {
            RegionNodesRequest request = buildFixedRequest(pageNo);

            try {
                String requestBody = JSON.toJSONString(request);
                log.info("请求海康区域列表, pageNo={}, pageSize={}", pageNo, PAGE_SIZE);

                String responseBody = hikvisionUtil.doPostJson(REGION_SEARCH_API, requestBody);

                if (!hikvisionUtil.isSuccess(responseBody)) {
                    log.error("海康区域查询失败: {}", responseBody);
                    throw new RuntimeException("海康区域查询失败: " + responseBody);
                }

                JSONObject dataJson = hikvisionUtil.getResponseData(responseBody);
                if (dataJson == null) {
                    log.warn("海康返回的data为空");
                    break;
                }

                RegionNodesResponse response = dataJson.toJavaObject(RegionNodesResponse.class);
                List<RegionNodesResponse.RegionItem> regionList = response.getList();

                if (regionList == null || regionList.isEmpty()) {
                    log.info("海康区域列表为空，拉取结束");
                    break;
                }

                allItems.addAll(regionList);
                log.info("第{}页拉取完成, 本页{}条, 累计{}条", pageNo, regionList.size(), allItems.size());

                // 判断是否还有下一页
                int total = response.getTotal() != null ? response.getTotal() : 0;
                if (pageNo * PAGE_SIZE >= total) {
                    hasMore = false;
                } else {
                    pageNo++;
                }

            } catch (Exception e) {
                log.error("拉取海康区域数据异常, pageNo={}", pageNo, e);
                throw new RuntimeException("拉取海康区域数据失败: " + e.getMessage(), e);
            }
        }

        log.info("海康数据拉取完成, 共获取{}条区域记录", allItems.size());
        return allItems;
    }

    /**
     * 构建固定的查询请求参数
     * <p>从根节点全量拉取所有区域，包含子区域。</p>
     */
    private RegionNodesRequest buildFixedRequest(int pageNo) {
        RegionNodesRequest request = new RegionNodesRequest();
        request.setResourceType("region");
        request.setParentIndexCodes(Collections.singletonList("root000000"));
        request.setIsSubRegion(true);
        request.setPageNo(pageNo);
        request.setPageSize(PAGE_SIZE);
        request.setAuthCodes(Collections.singletonList("view"));
        request.setCascadeFlag(0);
        return request;
    }

    /**
     * 将海康返回的区域数据转换为数据库实体
     */
    private RegionResource convertToEntity(RegionNodesResponse.RegionItem item) {
        RegionResource entity = new RegionResource();
        entity.setIndexCode(item.getIndexCode());
        entity.setName(item.getName());
        entity.setRegionPath(item.getRegionPath());
        entity.setParentIndexCode(item.getParentIndexCode());

        // boolean转Integer：true->1, false->0
        entity.setAvailable(item.getAvailable() != null && item.getAvailable() ? 1 : 0);
        entity.setLeaf(item.getLeaf() != null && item.getLeaf() ? 1 : 0);

        entity.setCascadeCode(item.getCascadeCode());
        entity.setCascadeType(item.getCascadeType());
        entity.setCatalogType(item.getCatalogType());
        entity.setExternalIndexCode(item.getExternalIndexCode());
        entity.setParentExternalIndexCode(item.getParentExternalIndexCode());
        entity.setSort(item.getSort());
        entity.setLocalQuantity(item.getLocalQuantity());
        entity.setTotalQuantity(item.getTotalQuantity());

        // 日期解析
        entity.setCreateTime(parseDate(item.getCreateTime()));
        entity.setUpdateTime(parseDate(item.getUpdateTime()));

        return entity;
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
    public List<RegionTreeVO> buildRegionTree() {
        // 1. 查询全部区域
        List<RegionResource> allRegions = list(new LambdaQueryWrapper<RegionResource>()
                .orderByAsc(RegionResource::getSort));

        if (allRegions.isEmpty()) {
            log.warn("区域资源表为空，返回空树");
            return Collections.emptyList();
        }

        // 2. 转换为VO并建立 parentIndexCode -> children 映射
        List<RegionTreeVO> allNodes = allRegions.stream()
                .map(this::convertToTreeVO)
                .collect(Collectors.toList());

        // 3. 按 parentIndexCode 分组
        Map<String, List<RegionTreeVO>> parentChildrenMap = allNodes.stream()
                .collect(Collectors.groupingBy(
                        vo -> vo.getParentIndexCode() != null ? vo.getParentIndexCode() : "",
                        Collectors.toList()
                ));

        // 4. 递归设置children
        for (RegionTreeVO node : allNodes) {
            List<RegionTreeVO> children = parentChildrenMap.getOrDefault(node.getIndexCode(), Collections.emptyList());
            // 按sort排序
            children.sort(Comparator.comparing(RegionTreeVO::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
            node.setChildren(children);
        }

        // 5. 根节点 = parentIndexCode 不存在于任何节点indexCode集合中的节点（最顶层区域）
        //    兼容海康不同版本的根节点标识（如 root000000、-1、空），避免写死导致树丢失
        Set<String> allIndexCodes = allNodes.stream()
                .map(RegionTreeVO::getIndexCode)
                .collect(Collectors.toSet());
        List<RegionTreeVO> rootNodes = allNodes.stream()
                .filter(vo -> vo.getParentIndexCode() == null
                        || vo.getParentIndexCode().isEmpty()
                        || !allIndexCodes.contains(vo.getParentIndexCode()))
                .collect(Collectors.toList());

        // 根节点按sort排序
        rootNodes.sort(Comparator.comparing(RegionTreeVO::getSort, Comparator.nullsLast(Comparator.naturalOrder())));

        log.info("区域树构建完成, 共{}个区域节点, 根节点{}个", allNodes.size(), rootNodes.size());
        return rootNodes;
    }

    /**
     * 将数据库实体转为树节点VO
     */
    private RegionTreeVO convertToTreeVO(RegionResource entity) {
        RegionTreeVO vo = new RegionTreeVO();
        vo.setIndexCode(entity.getIndexCode());
        vo.setName(entity.getName());
        vo.setRegionPath(entity.getRegionPath());
        vo.setParentIndexCode(entity.getParentIndexCode());
        vo.setAvailable(entity.getAvailable());
        vo.setLeaf(entity.getLeaf());
        vo.setCascadeCode(entity.getCascadeCode());
        vo.setCascadeType(entity.getCascadeType());
        vo.setCatalogType(entity.getCatalogType());
        vo.setExternalIndexCode(entity.getExternalIndexCode());
        vo.setSort(entity.getSort());
        vo.setLocalQuantity(entity.getLocalQuantity());
        vo.setTotalQuantity(entity.getTotalQuantity());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncCameraQuantity() {
        log.info("开始根据摄像头资源表同步区域资源数量...");

        // 1. 查询全部区域
        List<RegionResource> allRegions = list();
        if (allRegions.isEmpty()) {
            log.warn("区域资源表为空，跳过资源数量同步");
            return 0;
        }

        // 2. 统计各区域直接挂载的摄像头数量：
        //    SELECT region_index_code, COUNT(*) AS cnt FROM table_camera_resource WHERE region_index_code IS NOT NULL GROUP BY region_index_code
        Map<String, Long> cameraCountByRegion = new HashMap<>();
        for (Map<String, Object> row : cameraResourceMapper.selectMaps(new QueryWrapper<CameraResource>()
                .select("region_index_code", "COUNT(*) AS cnt")
                .isNotNull("region_index_code")
                .groupBy("region_index_code"))) {
            Object regionCode = getColumnValue(row, "region_index_code");
            if (regionCode == null) {
                continue;
            }
            Object cnt = getColumnValue(row, "cnt");
            cameraCountByRegion.put(String.valueOf(regionCode),
                    cnt == null ? 0L : ((Number) cnt).longValue());
        }

        // 3. 构建 parentIndexCode -> 子区域列表 映射，用于递归汇总总资源数
        Map<String, List<RegionResource>> parentChildrenMap = allRegions.stream()
                .collect(Collectors.groupingBy(r -> r.getParentIndexCode() != null ? r.getParentIndexCode() : ""));

        // 4. 计算每个区域的 localQuantity 与 totalQuantity
        Map<String, Integer> totalQuantityMap = new HashMap<>();
        for (RegionResource region : allRegions) {
            computeTotalQuantity(region.getIndexCode(), parentChildrenMap, cameraCountByRegion, totalQuantityMap);
        }

        // 5. 逐条更新（达梦驱动对JDBC批量支持有缺陷，沿用循环单条更新的方式）
        Date now = new Date();
        int updated = 0;
        for (RegionResource region : allRegions) {
            int local = cameraCountByRegion.getOrDefault(region.getIndexCode(), 0L).intValue();
            int total = totalQuantityMap.getOrDefault(region.getIndexCode(), 0);
            // 值未变化则跳过，避免无效写库
            if (Objects.equals(region.getLocalQuantity(), local)
                    && Objects.equals(region.getTotalQuantity(), total)) {
                continue;
            }
            RegionResource update = new RegionResource();
            update.setId(region.getId());
            update.setLocalQuantity(local);
            update.setTotalQuantity(total);
            update.setGmtModified(now);
            baseMapper.updateById(update);
            updated++;
        }

        log.info("区域摄像头资源数量同步完成, 共更新{}个区域", updated);
        return updated;
    }

    /**
     * 递归计算某区域及其全部下级区域的摄像头总数，结果缓存到 totalQuantityMap
     */
    private int computeTotalQuantity(String indexCode,
                                     Map<String, List<RegionResource>> parentChildrenMap,
                                     Map<String, Long> cameraCountByRegion,
                                     Map<String, Integer> totalQuantityMap) {
        Integer cached = totalQuantityMap.get(indexCode);
        if (cached != null) {
            return cached;
        }
        int total = cameraCountByRegion.getOrDefault(indexCode, 0L).intValue();
        for (RegionResource child : parentChildrenMap.getOrDefault(indexCode, Collections.emptyList())) {
            total += computeTotalQuantity(child.getIndexCode(), parentChildrenMap, cameraCountByRegion, totalQuantityMap);
        }
        totalQuantityMap.put(indexCode, total);
        return total;
    }

    /**
     * 从结果集中按列名（不区分大小写）取值，兼容达梦返回大写列名的情况
     */
    private Object getColumnValue(Map<String, Object> row, String columnName) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
