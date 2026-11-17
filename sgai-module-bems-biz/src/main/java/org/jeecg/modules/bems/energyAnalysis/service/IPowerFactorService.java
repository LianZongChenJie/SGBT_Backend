package org.jeecg.modules.bems.energyAnalysis.service;

import org.jeecg.modules.bems.energyAnalysis.dto.PowerFactorQueryDto;
import org.jeecg.modules.bems.energyAnalysis.vo.power.PowerFactorAnalysisVo;

/**
 * 功率因数分析
 */
public interface IPowerFactorService {

    /**
     * 功率因数分析：实时概览 + 趋势 + 分布 + 达标率统计
     */
    PowerFactorAnalysisVo analysis(PowerFactorQueryDto query);
}
