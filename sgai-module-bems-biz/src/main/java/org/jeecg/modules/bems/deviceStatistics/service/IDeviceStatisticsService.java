package org.jeecg.modules.bems.deviceStatistics.service;

import org.jeecg.modules.bems.deviceStatistics.vo.DeviceStatisticsVo;

/**
 * 设备统计
 */
public interface IDeviceStatisticsService {

    /**
     * 设备统计：设备总数量、设备类别数量、采集点位数量（设备属性数量）、
     * 质量戳为“好的数据”的采集点数量。
     *
     * @return 统计结果
     */
    DeviceStatisticsVo statistics();
}
