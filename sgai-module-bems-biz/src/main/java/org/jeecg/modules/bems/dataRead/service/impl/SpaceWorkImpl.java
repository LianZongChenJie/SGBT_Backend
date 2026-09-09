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
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SpaceWorkImpl implements IPspaceWork {
    private final PspaceUtils pspaceUtils;

    private final IDeviceAttributeService deviceAttributeService;

    public SpaceWorkImpl(PspaceUtils pspaceUtils, IDeviceAttributeService deviceAttributeService) {
        this.pspaceUtils = pspaceUtils;
        this.deviceAttributeService = deviceAttributeService;
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
        // 1.查询配置了采集编码的属性
        List<DeviceAttribute> attributes = deviceAttributeService.list(
                new LambdaQueryWrapper<DeviceAttribute>()
                        .isNotNull(DeviceAttribute::getAcquisitionCoding)
                        .ne(DeviceAttribute::getAcquisitionCoding, ""));
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
        // 4.按返回的 tagId(=采集编码) 回写 value（BOOL 转 0/1，其余类型转字符串）
        int updateCount = 0;
        for (PsDataWithTagId item : dataList) {
            if (item == null || item.getTagId() == null || item.getValue() == null) {
                continue;
            }
            Object rawValue = item.getValue();
            String valueStr = rawValue instanceof Boolean
                    ? (Boolean.TRUE.equals(rawValue) ? "1" : "0")
                    : String.valueOf(rawValue);
            boolean updated = deviceAttributeService.update(new LambdaUpdateWrapper<DeviceAttribute>()
                    .eq(DeviceAttribute::getAcquisitionCoding, String.valueOf(item.getTagId()))
                    .set(DeviceAttribute::getValue, valueStr));
            if (updated) {
                updateCount++;
            }
        }
        log.info("数字采集编码实时值刷新完成: 请求点数={}, 返回点数={}, 命中更新行数={}",
                tagIds.size(), dataList.size(), updateCount);
        return updateCount;
    }

}
