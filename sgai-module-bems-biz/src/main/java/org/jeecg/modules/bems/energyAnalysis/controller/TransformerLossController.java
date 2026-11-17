package org.jeecg.modules.bems.energyAnalysis.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.energyAnalysis.dto.TransformerLossQueryDto;
import org.jeecg.modules.bems.energyAnalysis.service.ITransformerLossService;
import org.jeecg.modules.bems.energyAnalysis.vo.loss.TransformerLossAnalysisVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 能效分析 - 变损分析
 * <p>
 * 现场暂无高压侧计量，当前为铭牌参数理论估算（模拟口径），
 * 返回体 simulated=true、mode=theory 用于前端明确标注。
 */
@Api(tags = "能效分析-变损分析")
@RestController
@RequestMapping("/bems/energyAnalysis/transformerLoss")
@AllArgsConstructor
public class TransformerLossController {

    private final ITransformerLossService transformerLossService;

    @ApiOperation(value = "变损分析", notes = "返回变压器参数、变损汇总、变损趋势与按计量点分摊（当前为理论估算口径）")
    @GetMapping("/analysis")
    public Result<TransformerLossAnalysisVo> analysis(TransformerLossQueryDto param) {
        return Result.ok(transformerLossService.analysis(param));
    }
}
