package org.jeecg.modules.bems.energyAnalysis.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.energyAnalysis.dto.PowerFactorQueryDto;
import org.jeecg.modules.bems.energyAnalysis.service.IPowerFactorService;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 能效分析 - 功率因数分析
 * <p>
 * 数据源：device_attribute（实时）+ device_attribute_history（15 分钟历史）。
 */
@Api(tags = "能效分析-功率因数分析")
@RestController
@RequestMapping("/bems/energyAnalysis/powerFactor")
@AllArgsConstructor
public class PowerFactorController {

    private final IPowerFactorService powerFactorService;

    @ApiOperation(value = "功率因数分析", notes = "一次返回点位清单、实时概览、趋势、分布、达标率与超限时长")
    @GetMapping("/analysis")
    public Result<PowerFactorAnalysisVo> analysis(PowerFactorQueryDto param) {
        return Result.ok(powerFactorService.analysis(param));
    }
}
