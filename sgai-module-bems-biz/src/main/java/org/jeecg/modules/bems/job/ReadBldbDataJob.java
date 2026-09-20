package org.jeecg.modules.bems.job;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.dataRead.service.IPspaceWork;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointDataService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@AllArgsConstructor
@Slf4j
public class ReadBldbDataJob {

    private final IMeteringPointDataService service;

    private final IPspaceWork pspaceWork;

    @Scheduled(cron = "0 */15 * * * ?")
//    @PostConstruct
    public void calculationMeteringPointData() {
        log.info("获取ELDB数据开始执行开始执行");
        int t = pspaceWork.refreshRealValueByNumericAcquisition("ELDB");
        log.info("refreshRealValueByNumericAcquisition 执行完成，更新 {} 条数据", t);
    }

}
