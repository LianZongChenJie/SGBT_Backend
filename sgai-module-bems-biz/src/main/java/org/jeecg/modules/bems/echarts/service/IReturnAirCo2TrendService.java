package org.jeecg.modules.bems.echarts.service;


import org.jeecg.modules.bems.echarts.dto.ActivePowerTrendQueryDto;
import org.jeecg.modules.bems.echarts.dto.ReturnAirCo2TrendQueryDto;
import org.jeecg.modules.bems.echarts.vo.ReturnAirCo2TrendVo;

/**
 * 设备属性趋势图服务
 *
 * @author sgai-fwbz
 */
public interface IReturnAirCo2TrendService {

    /**
     * 查询设备某属性历史值并组装为 ECharts 折线图数据
     * <p>
     * 逻辑：
     * <ol>
     *   <li>按 deviceId + attributeName 从 {@code device_attribute} 定位属性</li>
     *   <li>从 {@code device_attribute_history} 按时间范围取历史</li>
     *   <li>按 deviceId 分组、按时间桶（默认 1 小时）聚合</li>
     *   <li>填齐 xAxis 时间槽，缺失值填 null</li>
     * </ol>
     *
     * @param query 查询参数
     * @return ECharts 折线图数据
     */
    ReturnAirCo2TrendVo getReturnAirCo2Trend(ReturnAirCo2TrendQueryDto query);


}
