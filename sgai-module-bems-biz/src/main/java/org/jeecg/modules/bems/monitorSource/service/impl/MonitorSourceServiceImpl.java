package org.jeecg.modules.bems.monitorSource.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.EquipmentCategory;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.jeecg.modules.bems.mdm.service.IEquipmentCategoryService;
import org.jeecg.modules.bems.monitorSource.service.IMonitorSourceService;
import org.jeecg.modules.bems.monitorSource.vo.MonitorSourceCategoryVo;
import org.jeecg.modules.bems.monitorSource.vo.MonitorSourceDeviceVo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 监控源 Service 实现
 * <p>
 * 组装 设备分类(第一层) -> 设备(第二层) 两级树：分类取全部 equipment_category（不区分 type），
 * 设备取全部 device（不区分 device_type），按 category_id 归组；category_id 为 null 的设备归入"未分类"。
 */
@Slf4j
@Service
@AllArgsConstructor
public class MonitorSourceServiceImpl implements IMonitorSourceService {

    private final IEquipmentCategoryService equipmentCategoryService;
    private final IDeviceService deviceService;

    @Override
    public List<MonitorSourceCategoryVo> buildMonitorSourceTree() {
        // 全部设备分类，按 sort/id 排序
        List<EquipmentCategory> categories = equipmentCategoryService.list(
                new LambdaQueryWrapper<EquipmentCategory>()
                        .orderByAsc(EquipmentCategory::getSort)
                        .orderByAsc(EquipmentCategory::getId));
        // 全部设备，按 sort/id 排序
        List<Device> devices = deviceService.list(
                new LambdaQueryWrapper<Device>()
                        .orderByAsc(Device::getSort)
                        .orderByAsc(Device::getId));
        // 按 category_id 分组（保留 null 归未分类）
        Map<Long, List<Device>> grouped = devices.stream()
                .filter(d -> d.getCategoryId() != null)
                .collect(Collectors.groupingBy(Device::getCategoryId, LinkedHashMap::new, Collectors.toList()));
        List<Device> uncategorized = devices.stream()
                .filter(d -> d.getCategoryId() == null)
                .collect(Collectors.toList());

        List<MonitorSourceCategoryVo> result = new ArrayList<>();
        for (EquipmentCategory c : categories) {
            List<Device> children = grouped.get(c.getId());
            if (children == null || children.isEmpty()) {
                continue; // 分类下无设备则不展示
            }
            result.add(new MonitorSourceCategoryVo(c.getId(), c.getCategoryName(), toDeviceVos(children)));
        }
        if (!uncategorized.isEmpty()) {
            result.add(new MonitorSourceCategoryVo(null, "未分类", toDeviceVos(uncategorized)));
        }
        return result;
    }

    private List<MonitorSourceDeviceVo> toDeviceVos(List<Device> devices) {
        return devices.stream()
                .map(d -> new MonitorSourceDeviceVo(d.getId(), d.getDeviceCode(), d.getDeviceName()))
                .collect(Collectors.toList());
    }
}
