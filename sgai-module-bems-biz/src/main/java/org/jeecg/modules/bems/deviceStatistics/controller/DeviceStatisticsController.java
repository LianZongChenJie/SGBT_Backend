package org.jeecg.modules.bems.deviceStatistics.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.deviceStatistics.service.IDeviceStatisticsService;
import org.jeecg.modules.bems.deviceStatistics.vo.DeviceStatisticsVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备统计
 */
@Api(tags = "设备统计")
@RestController
@RequestMapping("/bems/deviceStatistics")
@Slf4j
@AllArgsConstructor
public class DeviceStatisticsController {

    private final IDeviceStatisticsService deviceStatisticsService;

    /**
     * 设备统计：设备总数量、设备类别数量、采集点位数量、质量戳为“好的数据”的采集点数量
     */
    @ApiOperation(value = "设备统计", notes = "设备统计：设备总数量、设备类别数量、采集点位数量、质量戳为“好的数据”的采集点数量")
    @GetMapping("/statistics")
    public Result<DeviceStatisticsVo> statistics() {
        return Result.ok(deviceStatisticsService.statistics());
    }
}
