package org.jeecg.modules.bems.dataRead.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sunwayland.pspace.entity.PsDataWithTagId;
import com.sunwayland.pspace.entity.PsResult;
import com.sunwayland.pspace.enums.PsErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.bems.dataRead.service.IPspaceWork;
import org.jeecg.modules.bems.dataRead.util.PspaceUtils;
import org.jeecg.modules.bems.mdm.constant.DeviceConstant;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.entity.DeviceAttributeHistory;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeHistoryService;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SpaceWorkImpl implements IPspaceWork {
    private final PspaceUtils pspaceUtils;

    private final IDeviceAttributeService deviceAttributeService;

    private final IDeviceService deviceService;

    private final IDeviceAttributeHistoryService deviceAttributeHistoryService;

    public SpaceWorkImpl(PspaceUtils pspaceUtils, IDeviceAttributeService deviceAttributeService,
                         IDeviceService deviceService, IDeviceAttributeHistoryService deviceAttributeHistoryService) {
        this.pspaceUtils = pspaceUtils;
        this.deviceAttributeService = deviceAttributeService;
        this.deviceService = deviceService;
        this.deviceAttributeHistoryService = deviceAttributeHistoryService;
    }

    /**
     * 批量获取实时数据
     * @param tagIds 标签id集合
     * @return 实时数据集合；未连接 pspace 或读取失败时返回 null
     */
    @Override
    public List<PsDataWithTagId> realReadList(List<Long> tagIds) {
        if (pspaceUtils.client == null) {
            log.warn("pspace 未连接（mock 模式），走降级逻辑");
            return null;
        }
        PsResult<PsDataWithTagId> psResult = pspaceUtils.client.realReadListV2(tagIds);
        if (Objects.equals(psResult.getCode(), PsErrorCodeEnum.PSRET_OK)
                && psResult.getData() != null && !psResult.getData().isEmpty()) {
            return psResult.getData();
        }else {
            log.warn("冷源历史数据无缓存且处于 mock 模式(connect 不可用), 无法读取: 读取点位数={}", tagIds.size());
            return null;
        }
    }

    /**
     * 从 device_attribute 查询所有采集编码(acquisition_coding)为纯数字的属性，
     * 以其作为 tagId 批量读取实时数据，并按返回数据中的 tagId 回写对应行的 value。
     * @return 实时数据集合；读取失败或返回空时为空集合
     */
    @Override
    public int refreshRealValueByNumericAcquisition() {
        // 1.只查询采集编码为纯数字的属性（兼容 MySQL 的 REGEXP）
        List<DeviceAttribute> attributes = deviceAttributeService.list(
                new LambdaQueryWrapper<DeviceAttribute>()
                        .isNotNull(DeviceAttribute::getAcquisitionCoding)
                        .ne(DeviceAttribute::getAcquisitionCoding, "")
                        .apply("acquisition_coding REGEXP '^[0-9]+$'"));
        // 2.过滤采集编码为纯数字的记录，作为 tagId 集合
        List<Long> tagIds = attributes.stream()
                .map(DeviceAttribute::getAcquisitionCoding)
                .filter(StringUtils::isNumeric)
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());
        if (tagIds.isEmpty()) {
            log.info("device_attribute 中未找到采集编码为纯数字的属性点");
            return 0;
        }
        // 3.批量读取实时数据
        List<PsDataWithTagId> dataList = this.realReadList(tagIds);
        if (dataList == null || dataList.isEmpty()) {
            return 0;
        }
        // 4.按返回的 tagId(=采集编码) 回写 value（BOOL 转 0/1，其余类型转字符串）：
        //   先更新数据库，再把新值同步写回 attributes 内存对象，并收集本次刷新到的属性行
        int updateCount = 0;
        List<DeviceAttribute> newAttributes = new ArrayList<>();
        Set<Long> collectedAttrIds = new HashSet<>();
        for (PsDataWithTagId item : dataList) {
            if (item == null || item.getTagId() == null || item.getValue() == null) {
                continue;
            }
            String coding = String.valueOf(item.getTagId());
            Object rawValue = item.getValue();
            String valueStr = rawValue instanceof Boolean
                    ? (Boolean.TRUE.equals(rawValue) ? "1" : "0")
                    : String.valueOf(rawValue);
            boolean updated = deviceAttributeService.update(new LambdaUpdateWrapper<DeviceAttribute>()
                    .eq(DeviceAttribute::getAcquisitionCoding, coding)
                    .set(DeviceAttribute::getValue, valueStr));
            if (updated) {
                updateCount++;
            }
            // 编码匹配的属性行：写回 value，并作为“本次刷新点”收集（同一属性行重复返回时只保留一条）
            for (DeviceAttribute attr : attributes) {
                if (attr != null && attr.getId() != null && Objects.equals(attr.getAcquisitionCoding(), coding)) {
                    attr.setValue(valueStr);
                    if (collectedAttrIds.add(attr.getId())) {
                        newAttributes.add(attr);
                    }
                }
            }
        }
        log.info("数字采集编码实时值刷新完成: 请求点数={}, 返回点数={}, 命中更新行数={}",
                tagIds.size(), dataList.size(), updateCount);
        // 5.更新所属设备的运行状态与最后采集时间
        this.updateDeviceGatherStatus(newAttributes, LocalDateTime.now(), "在线");
        //6.存储设备属性历史数据（仅本次刷新到实时值的属性行）
        this.saveDeviceAttributeHistory(newAttributes, LocalDateTime.now());
        return updateCount;
    }

    /**
     * 将本次刷新到实时值的属性行（value 已写回内存对象）存入设备属性历史表 device_attribute_history：
     * 逐条组装历史记录后循环入库（attribute_id/device_id/value/collection_time）。
     * @param attributes 本次刷新到实时值的设备属性列表（value 已是最新）
     * @param now 本次采集时间，作为 collection_time
     */
    private void saveDeviceAttributeHistory(List<DeviceAttribute> attributes, LocalDateTime now) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        // 1.遍历属性行组装历史记录（value 已由刷新步骤写回内存对象，无需再依赖实时返回集合）
        List<DeviceAttributeHistory> historyList = new ArrayList<>();
        for (DeviceAttribute attr : attributes) {
            if (attr == null || attr.getId() == null || attr.getDeviceId() == null
                    || attr.getValue() == null) {
                continue;
            }
            DeviceAttributeHistory history = new DeviceAttributeHistory();
            history.setDeviceId(attr.getDeviceId());
            history.setAttributeId(attr.getId());
            history.setCollectionTime(now);
            history.setValue(attr.getValue());
            historyList.add(history);
        }
        // 2.循环逐条入库（单条失败不影响其余记录，避免同一槽位重复插入时整批回滚）
        int successCount = 0;
        int failCount = 0;
        for (DeviceAttributeHistory history : historyList) {
            try {
                deviceAttributeHistoryService.save(history);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("设备属性历史入库失败: attributeId={}, collectionTime={}, value={}, 原因={}",
                        history.getAttributeId(), history.getCollectionTime(), history.getValue(), e.getMessage());
            }
        }
        log.info("设备属性历史入库完成: 成功={}, 失败={}, 采集时间={}", successCount, failCount, now);
    }

    /**
     * 根据设备属性直接刷新所属设备的运行状态与最后采集时间：
     * 对 attributes 中非空的 deviceId 去重后逐个更新。
     * @param attributes 设备属性列表（取其 deviceId 定位所属设备）
     * @param time 本次采集时间，写入设备 last_gather_time
     * @param online 目标运行状态，取 DeviceConstant.DEVICE_RUN_STATA_ONLINE/OFFLINE
     * @return 更新设备数
     */
    @Override
    public int updateDeviceGatherStatus(List<DeviceAttribute> attributes, LocalDateTime time, String online) {
        if (attributes == null || attributes.isEmpty()) {
            return 0;
        }
        // 1.直接对 attributes 的 deviceId 去重
        Set<Long> deviceIds = new HashSet<>();
        for (DeviceAttribute attr : attributes) {
            if (attr != null && attr.getDeviceId() != null) {
                deviceIds.add(attr.getDeviceId());
            }
        }
        if (deviceIds.isEmpty()) {
            return 0;
        }
        // 2.逐个设备更新运行状态与最后采集时间
        int updateCount = 0;
        for (Device device : deviceService.findByDeviceIds(deviceIds)) {
            if (device == null || device.getDeviceCode() == null) {
                continue;
            }
            // 一条 SQL 同时更新运行状态与最后采集时间
            deviceService.updateStatusAndGatherTime(device.getDeviceCode(), online, time);
            updateCount++;
        }
        log.info("设备采集状态更新完成: 命中设备数={}, 目标状态={}, 采集时间={}", deviceIds.size(), online, time);
        return updateCount;
    }

}
