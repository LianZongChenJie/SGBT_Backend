package org.jeecg.modules.bems.job;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.mdm.service.IMonitorPointCollectService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 监测点实时数据采集定时任务（每 15 分钟）
 * <p>
 * 参照 fwbz 楼控 BuildingControlRealPushJob：
 * 每 15 分钟调用第三方实时接口拉取 monitor_point 各采集点当前值，
 * 更新 monitor_point 实时值表，并按 15 分钟槽位写 monitor_point_history。
 * 整体异常由方法内兜底，不抛出到 Spring 调度器。
 */
@Slf4j
@Component
@AllArgsConstructor
public class MonitorPointCollectJob {

    private final IMonitorPointCollectService monitorPointCollectService;

    @Scheduled(cron = "0 1/15 * * * ?")
    public void collect() {
        log.info("监测点采集定时任务开始");
        try {
            monitorPointCollectService.collectOnce();
            log.info("监测点采集定时任务完成");
        } catch (Exception e) {
            log.error("监测点采集定时任务异常", e);
        }
    }
}
