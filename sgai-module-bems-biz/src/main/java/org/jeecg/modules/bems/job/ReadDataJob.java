package org.jeecg.modules.bems.job;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.dataRead.service.IPspaceWork;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointDataService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@AllArgsConstructor
@Slf4j
public class ReadDataJob {

    private final IMeteringPointDataService service;

    private final IPspaceWork pspaceWork;

    /** 标记是否是启动后的第一次触发 */
    private final AtomicBoolean firstRun = new AtomicBoolean(true);

    @Scheduled(cron = "0 */15 * * * ?")
    public void calculationMeteringPointData() {
        if (firstRun.getAndSet(false)) {
            log.info("首次触发，跳过执行");
            return;
        }
        log.info("获取数据开始执行开始执行");
        int t = pspaceWork.refreshRealValueByNumericAcquisition("");
        log.info("refreshRealValueByNumericAcquisition 执行完成，更新 {} 条数据", t);
    }
}