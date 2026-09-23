package org.jeecg.modules.bems.visualization.zhjsc.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.visualization.zhjsc.service.SmartCockpitService;
import org.jeecg.modules.bems.visualization.zhjsc.vo.AlarmListVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.CarbonFootprintVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.EnergySupplyOverviewVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.EnvironmentalMonitoringVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.OperationStatusKeyEquipmentVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.ProductionOverviewVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.SteamElectricityProductionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智慧驾驶舱数据接口
 * 提供生产数据、供能概况、碳足迹、环境监测、安全评分、生产概况、重点设备运行状态、人员管理、报警列表等数据
 */
@Api(tags = "智慧驾驶舱数据接口")
@RestController
@RequestMapping("/bems/visualization/zhjsc")
public class SmartCockpitController {

    @Autowired
    private SmartCockpitService smartCockpitService;


    /**
     * 生产数据
     * 各个能源系统的当日生产数据（累计统计当日），例如蒸汽产量、电能产量
     */
    @ApiOperation(value = "蒸汽产量,电能产量", notes = "各个能源系统的当日生产数据（累计统计当日）")
    @GetMapping("/steamElectricityProduction")
    public Result<List<SteamElectricityProductionVO>> steamElectricityProduction() {
        return Result.ok(smartCockpitService.steamElectricityProduction());
    }
    /**
     * 供能概况
     * 外供蒸汽量、换算热能；余热热水、光伏产电；
     */
    @ApiOperation(value = "供能概况", notes = "外供蒸汽量、换算热能；余热热水、光伏产电")
    @GetMapping("/energySupplyOverview")
    public Result<EnergySupplyOverviewVO> energySupplyOverview() {
        return Result.ok(smartCockpitService.energySupplyOverview());
    }

    /**
     * 碳足迹
     * 碳排放总量、等效植树林、再利用能源减排量、绿电减排、绿植固碳
     */
    @ApiOperation(value = "碳足迹", notes = "碳排放总量、等效植树林、再利用能源减排量、绿电减排、绿植固碳")
    @GetMapping("/carbonFootprint")
    public Result<CarbonFootprintVO> carbonFootprint() {
        return Result.ok(smartCockpitService.carbonFootprint());
    }
    /**
     * 环境监测
     * cems1、cems2、cems3的二氧化碳浓度、粉尘浓度
     *
     * @param period 周期：本周/WEEK、本月/MONTH、本年/YEAR
     */
    @ApiOperation(value = "环境监测", notes = "cems1、cems2、cems3的二氧化碳浓度、粉尘浓度，按周期查询")
    @GetMapping("/environmentalMonitoring")
    public Result<List<EnvironmentalMonitoringVO>> environmentalMonitoring(@RequestParam(defaultValue = "本周") String period) {
        return Result.ok(smartCockpitService.environmentalMonitoring(period));
    }

    /**
     * 生产概况
     * 风电、光伏两种类型设备产能折线图；X轴为时间，Y轴为发电量,折线图 (本周、本月、本年) 统计
     */
    @ApiOperation(value = "生产概况", notes = "风电、光伏两种类型设备产能折线图；X轴为时间，Y轴为发电量,折线图 (本周、本月、本年) 统计 ")
    @GetMapping("/productionOverview")
    public Result<ProductionOverviewVO> productionOverview(@RequestParam(defaultValue = "本周") String period) {
        return Result.OK(smartCockpitService.productionOverview(period));
    }
    /**
     * 重点设备运行状态
     * 除氧1、2；锅炉1、2、3的运行状态
     */
    @ApiOperation(value = "重点设备运行状态", notes = "除氧1、2；锅炉1、2、3的运行状态")
    @GetMapping("/operationStatusKeyEquipment")
    public Result<OperationStatusKeyEquipmentVO> operationStatusKeyEquipment() {
        return Result.ok(smartCockpitService.operationStatusKeyEquipment());
    }

    /**
     * 报警列表
     * 报警列表，包含等级、报警名称、对应设备、处理状态
     */
    @ApiOperation(value = "报警列表", notes = "等级、报警名称、对应设备、处理状态")
    @GetMapping("/alarmList")
    public Result<AlarmListVO> alarmList() {
        return Result.ok(smartCockpitService.alarmList());
    }

}
