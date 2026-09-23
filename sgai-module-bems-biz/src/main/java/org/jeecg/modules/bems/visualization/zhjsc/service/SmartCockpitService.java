package org.jeecg.modules.bems.visualization.zhjsc.service;

import org.jeecg.modules.bems.visualization.zhjsc.vo.AlarmListVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.CarbonFootprintVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.EnergySupplyOverviewVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.EnvironmentalMonitoringVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.OperationStatusKeyEquipmentVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.ProductionOverviewVO;
import org.jeecg.modules.bems.visualization.zhjsc.vo.SteamElectricityProductionVO;

import java.util.List;

public interface SmartCockpitService {

    List<EnvironmentalMonitoringVO> environmentalMonitoring(String period);

    OperationStatusKeyEquipmentVO operationStatusKeyEquipment();

    AlarmListVO alarmList();

    List<SteamElectricityProductionVO> steamElectricityProduction();

    CarbonFootprintVO carbonFootprint();

    EnergySupplyOverviewVO energySupplyOverview();

    ProductionOverviewVO productionOverview(String period);

}
