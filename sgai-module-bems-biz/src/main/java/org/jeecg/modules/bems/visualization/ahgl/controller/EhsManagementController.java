package org.jeecg.modules.bems.visualization.ahgl.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.visualization.ahgl.service.EhsManagementService;
import org.jeecg.modules.bems.visualization.ahgl.vo.AlarmsByTypeNumberVO;
import org.jeecg.modules.bems.visualization.ahgl.vo.DividedIntoSixVO;
import org.jeecg.modules.bems.visualization.zhjsc.service.SmartCockpitService;
import org.jeecg.modules.bems.visualization.zhjsc.vo.EnvironmentalMonitoringVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 安环管理数据接口
 * 提供环境监测，报警组成数据
 */
@Api(tags = "安环管理数据接口")
@RestController
@RequestMapping("/bems/visualization/ahgl")
public class EhsManagementController {

    @Autowired
    private SmartCockpitService smartCockpitService;
    @Autowired
    private EhsManagementService ehsManagementService;

    /**
     * 环境监测
     * cems1、cems2、cems3的二氧化碳浓度、粉尘浓度 (本周、本月、本年)
     */
    @ApiOperation(value = "环境监测", notes = "cems1、cems2、cems3的二氧化碳浓度、粉尘浓度，按周期查询")
    @GetMapping("/environmentalMonitoring")
    public Result<List<EnvironmentalMonitoringVO>> environmentalMonitoring(@RequestParam(defaultValue = "本周") String period) {
        return Result.ok(smartCockpitService.environmentalMonitoring(period));
    }

    /**
     * 环境监测曲线图6组折线，x轴为时间，y轴为co2和粉尘数据(cems1、cems2、cems3)
     */
    @ApiOperation(value = "环境监测曲线图6组折线", notes = "环境监测曲线图6组折线，x轴为时间，y轴为co2和粉尘数据(cems1、cems2、cems3)")
    @GetMapping("/dividedIntoSix")
    public Result<List<DividedIntoSixVO>> dividedIntoSix(
            @ApiParam(value = "是否按天做平均", example = "true按天做平均")
            @RequestParam(value = "byDay", required = false) String byDay,
            @ApiParam(value = "开始时间，默认当前日期往前一周", example = "2026-09-08")
            @RequestParam(value = "startTime", required = false) String startTime,
            @ApiParam(value = "结束时间，默认当前日期", example = "2026-09-15")
            @RequestParam(value = "endTime", required = false) String endTime) {

        // 日期格式
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate today = LocalDate.now();
        // 是否按天做平均
        boolean byDayT = Objects.isNull(byDay)
                ? true
                : Boolean.valueOf(byDay);

        // 结束时间默认当前日期
        LocalDate endDate = (endTime == null || endTime.trim().isEmpty())
                ? today
                : LocalDate.parse(endTime, formatter);

        // 开始时间默认当前日期往前一周
        LocalDate startDate = (startTime == null || startTime.trim().isEmpty())
                ? endDate.minusWeeks(1)
                : LocalDate.parse(startTime, formatter);

        return Result.ok(ehsManagementService.dividedIntoSix(startDate, endDate, byDayT));
    }

    /**
     * 饼图，统计按照类型统计告警次数（本周、本月、本年）
     */
    @ApiOperation(value = "统计按照类型统计告警次数", notes = "饼图，统计按照类型统计告警次数（本周、本月、本年）")
    @GetMapping("/alarmsByTypeNumber")
    public Result<List<AlarmsByTypeNumberVO>> alarmsByTypeNumber() {
        return Result.ok(ehsManagementService.alarmsByTypeNumber());
    }
}
