package org.jeecg.modules.bems.visualization.ahgl.service;

import org.jeecg.modules.bems.visualization.ahgl.vo.AlarmsByTypeNumberVO;
import org.jeecg.modules.bems.visualization.ahgl.vo.DividedIntoSixVO;

import java.time.LocalDate;
import java.util.List;

public interface EhsManagementService {

    List<DividedIntoSixVO> dividedIntoSix(LocalDate startDate, LocalDate endDate, boolean byDay);

    List<AlarmsByTypeNumberVO> alarmsByTypeNumber();

}
