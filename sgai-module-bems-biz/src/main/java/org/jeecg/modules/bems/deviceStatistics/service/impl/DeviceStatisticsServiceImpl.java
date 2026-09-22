package org.jeecg.modules.bems.deviceStatistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.deviceStatistics.service.IDeviceStatisticsService;
import org.jeecg.modules.bems.deviceStatistics.vo.DeviceStatisticsVo;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.jeecg.modules.bems.mdm.service.IEquipmentCategoryService;
import org.springframework.stereotype.Service;

/**
 * 设备统计
 */
@Slf4j
@Service
@AllArgsConstructor
public class DeviceStatisticsServiceImpl implements IDeviceStatisticsService {

    /**
     * 质量戳：好的数据（与 pspace PsQualityEnum 的 desc 对应，存于 device_attribute.quality_stamp）
     */
    private static final String QUALITY_STAMP_GOOD = "好的数据";

    private final IDeviceService deviceService;

    private final IEquipmentCategoryService equipmentCategoryService;

    private final IDeviceAttributeService deviceAttributeService;

    @Override
    public DeviceStatisticsVo statistics() {
        DeviceStatisticsVo vo = new DeviceStatisticsVo();
        // 1.设备总数量
        vo.setDeviceCount(deviceService.count());
        // 2.设备类别数量
        vo.setCategoryCount(equipmentCategoryService.count());
        // 3.采集点位数量（设备属性数量）
        vo.setAttributeCount(deviceAttributeService.count());
        // 4.质量戳为“好的数据”的采集点数量
        vo.setGoodQualityCount(deviceAttributeService.count(new LambdaQueryWrapper<DeviceAttribute>()
                .eq(DeviceAttribute::getQualityStamp, QUALITY_STAMP_GOOD)));
        log.info("设备统计完成: {}", vo);
        return vo;
    }
}
