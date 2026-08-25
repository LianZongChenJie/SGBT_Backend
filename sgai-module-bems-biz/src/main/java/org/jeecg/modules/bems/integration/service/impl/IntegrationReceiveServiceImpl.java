package org.jeecg.modules.bems.integration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.integration.dto.*;
import org.jeecg.modules.bems.integration.service.IntegrationReceiveService;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.EquipmentCategory;
import org.jeecg.modules.bems.mdm.entity.Space;
import org.jeecg.modules.bems.mdm.mapper.DeviceMapper;
import org.jeecg.modules.bems.mdm.mapper.EquipmentCategoryMapper;
import org.jeecg.modules.bems.mdm.mapper.SpaceMapper;
import org.jeecg.modules.bems.mdm.service.IEquipmentCategoryService;
import org.jeecg.modules.bems.mdm.service.ISpaceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class IntegrationReceiveServiceImpl implements IntegrationReceiveService {

    private IEquipmentCategoryService categoryService;
    private ISpaceService spaceService;
    private DeviceMapper deviceMapper;
    private EquipmentCategoryMapper categoryMapper;
    private SpaceMapper spaceMapper;

    @Override
    public ReceiveResult receive(IntegrationPayload<?> payload, String deviceType) {
        ReceiveResult result = new ReceiveResult(payload.getBatchId());
        String type = payload.getType();
        String op = payload.getOp();
        // CATEGORY/SPACE 全量或增量推送时，master 可能不按拓扑顺序发（子先于父），
        // 导致子节点因父不存在而孤儿挂根（类别）或被拒（空间）。
        // 这里对非 DELETE 的 CATEGORY/SPACE 单次请求按 pid 拓扑排序后再处理，确保父先于子。
        List<?> data = payload.getData();
        if (needsTopoSort(type, op)) {
            data = topoSortByPid(data);
        }
        for (Object raw : data) {
            String rejectReason = dispatch(type, op, deviceType, raw);
            if (rejectReason == null) {
                result.setAccepted(result.getAccepted() + 1);
            } else {
                String id = extractId(raw);
                result.getRejected().add(new RejectedItem(id, rejectReason));
            }
        }
        return result;
    }

    /** CATEGORY/SPACE 且非 DELETE 时需要拓扑排序（DEVICE 无父子关系，DELETE 按原序即可） */
    private boolean needsTopoSort(String type, String op) {
        return ("CATEGORY".equals(type) || "SPACE".equals(type))
                && !"DELETE".equals(op);
    }

    /**
     * 按 pid 拓扑排序：父先于子。
     * 多趟扫描：每趟挑出 pid="0" 或 pid 不在本次 id 集合 或 pid 已处理 的条目。
     * 若一趟无进展（存在环或依赖完全缺失），剩余条目按原顺序追加，交由孤儿挂根兜底。
     */
    private List<?> topoSortByPid(List<?> data) {
        if (data == null || data.size() <= 1) return data;
        // 收集本次所有 id（只在本次批次内排序，批次外的父已在本地，按"已处理"等同处理）
        java.util.Set<String> batchIds = new java.util.HashSet<>();
        for (Object raw : data) {
            String id = extractId(raw);
            if (id != null) batchIds.add(id);
        }
        // 内部用 raw 类型 List 操作，元素统一按 Object 处理
        List sorted = new java.util.ArrayList<>(data.size());
        java.util.Set<String> processed = new java.util.HashSet<>();
        List remaining = new java.util.ArrayList<>(data);
        while (!remaining.isEmpty()) {
            List ready = new java.util.ArrayList<>();
            for (Object raw : remaining) {
                String pid = extractPid(raw);
                if (pid == null || "0".equals(pid)
                        || !batchIds.contains(pid)       // 父不在本次批次 → 本地应已存在，视为已就绪
                        || processed.contains(pid)) {   // 父在本批次且已处理
                    ready.add(raw);
                }
            }
            if (ready.isEmpty()) {
                // 无进展（环或互相依赖），剩余按原顺序追加，交由下游兜底（类别孤儿挂根/空间 reject）
                sorted.addAll(remaining);
                break;
            }
            for (Object raw : ready) {
                String id = extractId(raw);
                if (id != null) processed.add(id);
                sorted.add(raw);
            }
            remaining.removeAll(ready);
        }
        return sorted;
    }

    /** 从 raw（LinkedHashMap）读取 pid 字段；不存在返回 null */
    private String extractPid(Object raw) {
        if (raw instanceof java.util.Map) {
            Object pid = ((java.util.Map<?, ?>) raw).get("pid");
            return pid == null ? null : pid.toString();
        }
        return null;
    }

    /** @return null 表示成功；非空为 reject 原因 */
    @SuppressWarnings("unchecked")
    private String dispatch(String type, String op, String deviceType, Object raw) {
        try {
            if ("CATEGORY".equals(type)) {
                return handleCategory(op, deviceType, (CategoryPushItem) convert(raw, CategoryPushItem.class));
            } else if ("SPACE".equals(type)) {
                return handleSpace(op, (SpacePushItem) convert(raw, SpacePushItem.class));
            } else if ("DEVICE".equals(type)) {
                return handleDevice(op, deviceType, (DevicePushItem) convert(raw, DevicePushItem.class));
            }
            return "不支持的类型:" + type;
        } catch (Exception e) {
            log.error("接收处理异常: {}", raw, e);
            return "处理异常:" + e.getMessage();
        }
    }

    private String handleCategory(String op, String type, CategoryPushItem item) {
        if ("DELETE".equals(op)) {
            categoryMapper.delete(new QueryWrapper<EquipmentCategory>().eq("master_id", item.getId()));
            return null;
        }
        UpsertResult r = categoryService.upsertByMasterId(item.getId(), item.getName(), item.getPid(), type, item.getSort());
        return r.isOk() ? null : r.getReason();
    }

    private String handleSpace(String op, SpacePushItem item) {
        if ("DELETE".equals(op)) {
            spaceMapper.delete(new QueryWrapper<Space>().eq("master_id", item.getId()));
            return null;
        }
        UpsertResult r = spaceService.upsertByMasterId(item.getId(), item.getName(), item.getPid(), item.getSort());
        return r.isOk() ? null : r.getReason();
    }

    private String handleDevice(String op, String deviceType, DevicePushItem item) {
        Device exist = deviceMapper.selectOne(new QueryWrapper<Device>().eq("master_id", item.getId()));
        if ("DELETE".equals(op)) {
            if (exist != null) {
                deviceMapper.deleteById(exist.getId());
            }
            return null;
        }
        // UPSERT / SNAPSHOT
        // 1. 引用校验：categoryId / spaceId 必须在本地存在（按 master_id 查）
        Long categoryId = item.getCategoryId() == null ? null
                : toLocalId(categoryMapper, item.getCategoryId());
        if (item.getCategoryId() != null && categoryId == null) return "类别不存在";
        Long spaceId = item.getSpaceId() == null ? null
                : toLocalId(spaceMapper, item.getSpaceId());
        if (item.getSpaceId() != null && spaceId == null) return "空间不存在";
        // 2. 名称冲突（撞别的 master_id）
        Device nameOwner = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_name", item.getName()).last("limit 1"));
        if (nameOwner != null && (nameOwner.getMasterId() == null
                || !nameOwner.getMasterId().equals(item.getId()))) {
            return "设备名称冲突";
        }
        if (exist == null) {
            Device d = new Device();
            d.setMasterId(item.getId());
            d.setDeviceName(item.getName());
            // 信任报文 deviceCode；为空时用 name 兜底，避免本地编码为 null
            d.setDeviceCode((item.getDeviceCode() == null || item.getDeviceCode().isEmpty())
                    ? item.getName() : item.getDeviceCode());
            d.setDeviceType(deviceType);
            d.setCategoryId(categoryId);
            d.setSpaceId(spaceId);
            d.setRemark(item.getRemark());
            d.setSort(0);
            deviceMapper.insert(d);
        } else {
            exist.setDeviceName(item.getName());
            // 信任报文 deviceCode，空不覆盖原值
            if (item.getDeviceCode() != null && !item.getDeviceCode().isEmpty()) {
                exist.setDeviceCode(item.getDeviceCode());
            }
            exist.setDeviceType(deviceType);
            exist.setCategoryId(categoryId);
            exist.setSpaceId(spaceId);
            exist.setRemark(item.getRemark());
            deviceMapper.updateById(exist);
        }
        return null;
    }

    /** 按 master_id 查本地类别 Long id；查不到返回 null */
    private Long toLocalId(EquipmentCategoryMapper mapper, String masterId) {
        EquipmentCategory c = mapper.selectOne(new QueryWrapper<EquipmentCategory>().eq("master_id", masterId));
        return c == null ? null : c.getId();
    }

    /** 按 master_id 查本地空间 Long id；查不到返回 null */
    private Long toLocalId(SpaceMapper mapper, String masterId) {
        Space s = mapper.selectOne(new QueryWrapper<Space>().eq("master_id", masterId));
        return s == null ? null : s.getId();
    }

    // FAIL_ON_UNKNOWN_PROPERTIES=false：master 报文会带 specModel/professionId/systemId 等
    // 本地不落库的字段，DTO 上不加这些属性，反序列化时忽略，避免抛 UnrecognizedPropertyException
    private static final com.fasterxml.jackson.databind.ObjectMapper OM =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Object convert(Object raw, Class<?> clz) {
        // payload.data 经 Jackson 解析为 LinkedHashMap；用 ObjectMapper 转目标类型
        return OM.convertValue(raw, clz);
    }

    private String extractId(Object raw) {
        if (raw instanceof java.util.Map) {
            Object id = ((java.util.Map<?, ?>) raw).get("id");
            return id == null ? null : id.toString();
        }
        return null;
    }
}
