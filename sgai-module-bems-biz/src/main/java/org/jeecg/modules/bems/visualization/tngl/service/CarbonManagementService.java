package org.jeecg.modules.bems.visualization.tngl.service;

import org.jeecg.modules.bems.visualization.tngl.vo.*;

import java.util.List;

public interface CarbonManagementService {

    /**
     * 锅炉能碳指标：3 个锅炉的蒸汽产量（1 小时内数据，单位 t/h）
     */
    List<BoilerCarbonEmissionsIndexVo> boilerCarbonEmissionsIndex();

    /**
     * 锅炉能耗转换碳排放量
     */
    List<BoilerEnergyCarbonConversionVO> boilerEnergyCarbonConversion();

    /**
     * 锅炉能耗：电、水、汽耗折线图（本周/本月/本年）
     */
    BoilerEnergyConsumptionVO boilerEnergyConsumption(String period);

    /**
     * 水处理量折线图：原水输入、一次成水量、二次成水量
     */
    WaterTreatmentProductionVO waterTreatmentProduction(String period);

    /**
     * 光伏能源指标：累计光伏发电量、碳排放量
     */
    PhotovoltaicEnergyIndexVO photovoltaicEnergyIndex(String period);

    /**
     * 光伏发电量柱状图
     */
    PhotovoltaicPowerGenerationVO photovoltaicPowerGeneration(String period);
}
