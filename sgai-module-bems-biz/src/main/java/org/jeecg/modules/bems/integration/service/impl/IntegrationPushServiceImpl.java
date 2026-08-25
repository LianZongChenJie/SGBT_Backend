package org.jeecg.modules.bems.integration.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.integration.client.IntegrationPushClient;
import org.jeecg.modules.bems.integration.config.IntegrationProperties;
import org.jeecg.modules.bems.integration.dto.DevicePushItem;
import org.jeecg.modules.bems.integration.entity.IntegrationPushLog;
import org.jeecg.modules.bems.integration.service.IIntegrationPushLogService;
import org.jeecg.modules.bems.integration.service.IntegrationPushService;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.EquipmentCategory;
import org.jeecg.modules.bems.mdm.entity.Space;
import org.jeecg.modules.bems.mdm.mapper.DeviceMapper;
import org.jeecg.modules.bems.mdm.mapper.EquipmentCategoryMapper;
import org.jeecg.modules.bems.mdm.mapper.SpaceMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IntegrationPushServiceImpl implements IntegrationPushService {

    private final IntegrationProperties props;
    private final IntegrationPushClient client;
    private final IIntegrationPushLogService pushLogService;
    private final DeviceMapper deviceMapper;
    private final EquipmentCategoryMapper categoryMapper;
    private final SpaceMapper spaceMapper;
    private final Executor executor;

    public IntegrationPushServiceImpl(IntegrationProperties props,
                                      IntegrationPushClient client,
                                      IIntegrationPushLogService pushLogService,
                                      DeviceMapper deviceMapper,
                                      EquipmentCategoryMapper categoryMapper,
                                      SpaceMapper spaceMapper,
                                      @Qualifier("integrationPushExecutor") Executor executor) {
        this.props = props;
        this.client = client;
        this.pushLogService = pushLogService;
        this.deviceMapper = deviceMapper;
        this.categoryMapper = categoryMapper;
        this.spaceMapper = spaceMapper;
        this.executor = executor;
    }

    @Override
    public void pushDevices(List<Device> devices, String op) {
        if (!props.isEnabled() || CollectionUtil.isEmpty(devices)) return;
        // 按 deviceType 分组：计量("1")归 meter，其余（含 "2" 与 null）归 equipment
        Map<String, List<Device>> byType = devices.stream().collect(
                Collectors.groupingBy(d -> Device.DEVICE_TYPE_MEASURING.equals(d.getDeviceType()) ? "meter" : "equipment"));
        byType.forEach((group, list) -> {
            String token = "meter".equals(group) ? props.getToken().getMeter() : props.getToken().getEquipment();
            List<DevicePushItem> items = list.stream().map(this::toItem).collect(Collectors.toList());
            executor.execute(() -> doPush(items, op, token));
        });
    }

    private DevicePushItem toItem(Device d) {
        DevicePushItem item = new DevicePushItem();
        // master_id 懒生成
        if (d.getMasterId() == null || d.getMasterId().isEmpty()) {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            d.setMasterId(uuid);
            Device patch = new Device();
            patch.setId(d.getId());
            patch.setMasterId(uuid);
            deviceMapper.updateById(patch);
        }
        item.setId(d.getMasterId());
        item.setName(d.getDeviceName());
        item.setDeviceCode(d.getDeviceCode());
        item.setRemark(d.getRemark());
        item.setCategoryId(toMasterId(d.getCategoryId(), true));
        item.setSpaceId(toMasterId(d.getSpaceId(), false));
        return item;
    }

    /** 本地 Long 主键 -> master uuid；isCategory 区分类别/空间 */
    private String toMasterId(Long localId, boolean isCategory) {
        if (localId == null) return null;
        if (isCategory) {
            EquipmentCategory c = categoryMapper.selectById(localId);
            return c == null ? null : c.getMasterId();
        } else {
            Space s = spaceMapper.selectById(localId);
            return s == null ? null : s.getMasterId();
        }
    }

    private void doPush(List<DevicePushItem> items, String op, String token) {
        String batchId = UUID.randomUUID().toString().replace("-", "");
        IntegrationPushLog log0 = new IntegrationPushLog();
        log0.setBatchId(batchId);
        log0.setOp(op);
        log0.setType("DEVICE");
        log0.setDataCount(items.size());
        log0.setDataIds(items.stream().map(DevicePushItem::getId)
                .collect(Collectors.joining(",")));
        log0.setCreateTime(new Date());

        Map<String, Object> payload = new HashMap<>();
        payload.put("source", props.getSource());
        payload.put("type", "DEVICE");
        payload.put("op", op);
        payload.put("batchId", batchId);
        payload.put("data", items);

        try {
            Map<String, Object> resp = client.postReceive(payload, token);
            int httpStatus = (Integer) resp.get("httpStatus");
            log0.setHttpStatus(httpStatus);
            log0.setStatus(httpStatus >= 200 && httpStatus < 300 ? "SUCCESS" : "FAIL");
            log0.setResponseMsg(String.valueOf(resp.get("body")));
        } catch (Exception e) {
            log.error("推送 master 失败 batchId={}", batchId, e);
            log0.setStatus("FAIL");
            log0.setResponseMsg(e.getClass().getSimpleName() + ":" + e.getMessage());
        }
        pushLogService.save(log0);
    }
}
