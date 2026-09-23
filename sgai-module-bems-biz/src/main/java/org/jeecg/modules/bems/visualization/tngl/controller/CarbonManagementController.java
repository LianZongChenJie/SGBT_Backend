package org.jeecg.modules.bems.visualization.tngl.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.visualization.tngl.service.CarbonManagementService;
import org.jeecg.modules.bems.visualization.tngl.vo.BoilerEnergyCarbonConversionVO;
import org.jeecg.modules.bems.visualization.tngl.vo.BoilerEnergyConsumptionVO;
import org.jeecg.modules.bems.visualization.tngl.vo.PhotovoltaicEnergyIndexVO;
import org.jeecg.modules.bems.visualization.tngl.vo.PhotovoltaicPowerGenerationVO;
import org.jeecg.modules.bems.visualization.tngl.vo.WaterTreatmentProductionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 碳管理数据接口
 * 提供锅炉能碳指标、碳计算指标、水处理生产指标、再利用能源指标、光伏能源等数据
 */
@Api(tags = "碳管理数据接口")
@RestController
@RequestMapping("/bems/visualization/tngl")
public class CarbonManagementController {

    @Autowired
    private CarbonManagementService carbonManagementService;
    /**
     * 外部管线
     * 园区外部管线监测设备名称以及监测数据
     */

    /**
     * 锅炉能碳指标
     * 锅炉能耗转换碳排放量
     */
    @ApiOperation(value = "锅炉能碳指标锅炉能耗转换碳排放量", notes = "锅炉能耗转换碳排放量")
    @GetMapping("/boilerEnergyCarbonConversion")
    public Result<List<BoilerEnergyCarbonConversionVO>> boilerEnergyCarbonConversion() {
        return Result.ok(carbonManagementService.boilerEnergyCarbonConversion());
    }

    /**
     * 锅炉能碳指标
     * 3个锅炉的蒸汽产量，统计1个小时内的数据 (单位: t/h)
     */
    @ApiOperation(value = "锅炉能碳指标", notes = "3个锅炉的蒸汽产量，统计1个小时内的数据 (单位: t/h)")
    @GetMapping("/boilerCarbonEmissionsIndex")
    public Result<Map<String, BigDecimal>> boilerCarbonEmissionsIndex() {
        return Result.ok(carbonManagementService.boilerCarbonEmissionsIndex());
    }

    /**
     * 锅炉能耗
     * 锅炉的电、水、汽耗数据折线图，分别提供3个锅炉的 (本周、本月、本年)
     */
    @ApiOperation(value = "锅炉能耗", notes = "锅炉的电、水、汽耗数据折线图，分别提供3个锅炉的 (本周、本月、本年)")
    @GetMapping("/boilerEnergyConsumption")
    public Result<BoilerEnergyConsumptionVO> boilerEnergyConsumption(@RequestParam(defaultValue = "本周") String period) {
        return Result.ok(carbonManagementService.boilerEnergyConsumption(period));
    }

    /**
     * 水处理生产指标
     * 水处理量折线图，含原水输入、一次成水量、二次成水量 (本周、本月、本年)
     */
    @ApiOperation(value = "水处理生产指标", notes = "水处理量折线图，含原水输入、一次成水量、二次成水量 (本周、本月、本年)")
    @GetMapping("/waterTreatmentProduction")
    public Result<WaterTreatmentProductionVO> waterTreatmentProduction(@RequestParam(defaultValue = "本周") String period) {
        return Result.ok(carbonManagementService.waterTreatmentProduction(period));
    }

    /**
     * 再利用能源指标
     * 园区累计预热蒸汽总量，对应能量 (本周、本月、本年)
     */
    /**
     * 再利用能源指标
     * 余热蒸汽量柱状图，Y轴蒸汽量，X轴时间 (本周、本月、本年)
     */

    /**
     * 光伏能源
     * 累计光伏发电量、碳排放量 (本周、本月、本年)
     */
    @ApiOperation(value = "光伏能源指标", notes = "累计光伏发电量、碳排放量 (本周、本月、本年)")
    @GetMapping("/photovoltaicEnergyIndex")
    public Result<PhotovoltaicEnergyIndexVO> photovoltaicEnergyIndex(@RequestParam(defaultValue = "本周") String period) {
        return Result.ok(carbonManagementService.photovoltaicEnergyIndex(period));
    }

    /**
     * 光伏能源
     * 发电量柱状图 Y轴发电量，X轴时间 (本周、本月、本年)
     */
    @ApiOperation(value = "光伏发电量柱状图", notes = "发电量柱状图 Y轴发电量，X轴时间 (本周、本月、本年)")
    @GetMapping("/photovoltaicPowerGeneration")
    public Result<PhotovoltaicPowerGenerationVO> photovoltaicPowerGeneration(@RequestParam(defaultValue = "本周") String period) {
        return Result.ok(carbonManagementService.photovoltaicPowerGeneration(period));
    }

}
