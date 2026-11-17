package org.jeecg.modules.bems.energyAnalysis.service;

import org.jeecg.modules.bems.energyAnalysis.dto.TransformerLossQueryDto;
import org.jeecg.modules.bems.energyAnalysis.vo.loss.TransformerLossAnalysisVo;

/**
 * 变压器损耗（变损）分析
 * <p>
 * 现场暂无高压侧计量点，默认走铭牌参数理论计算口径（模拟），
 * 若后续具备高压侧表可切换为实测差值口径。
 */
public interface ITransformerLossService {

    /**
     * 变损分析：汇总 + 趋势 + 分摊
     */
    TransformerLossAnalysisVo analysis(TransformerLossQueryDto query);
}
