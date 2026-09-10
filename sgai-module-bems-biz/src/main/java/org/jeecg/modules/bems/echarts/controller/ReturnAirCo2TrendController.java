package org.jeecg.modules.bems.echarts.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.jeecg.common.api.vo.Result;

import org.jeecg.modules.bems.echarts.dto.ActivePowerTrendQueryDto;
import org.jeecg.modules.bems.echarts.dto.ReturnAirCo2TrendQueryDto;
import org.jeecg.modules.bems.echarts.service.IReturnAirCo2TrendService;
import org.jeecg.modules.bems.echarts.vo.ReturnAirCo2TrendVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 设备属性 ECharts 趋势图接口
 *
 * @author sgai-fwbz
 */
@Api(tags = "设备属性趋势图")
@RestController
@RequestMapping("/bems/echarts/returnAirCo2Trend")
@AllArgsConstructor
public class ReturnAirCo2TrendController {

    private final IReturnAirCo2TrendService service;

    /**
     * 根据设备ID列表查询"回风二氧化碳"等属性的历史趋势，返回 ECharts 曲线图所需数据。
     * <p>
     * 联动表：device_attribute（按设备ID+属性名定位 attribute_id） → device_attribute_history（按 attribute_id + 时间区间拉历史）。
     * <p>
     * 示例：{@code GET /fwbz/echarts/returnAirCo2Trend?deviceIds=1,2,3,4&attributeName=回风二氧化碳&startTime=2025-09-02 00:00:00&endTime=2025-09-02 23:59:59&granularity=hour&threshold=800}
     */
    @GetMapping("/query")
    @ApiOperation("查询设备属性历史图，根据属性名称")
    public Result<ReturnAirCo2TrendVo> query(@Valid ReturnAirCo2TrendQueryDto query) {
        return Result.ok(service.getReturnAirCo2Trend(query));
    }


}
