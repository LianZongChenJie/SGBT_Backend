package org.jeecg.modules.bems.job;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.fire.service.IFireAlarmService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 消防报警采集定时任务（每 2 分钟）
 * <p>
 * 请求 pSpace 历史数据接口 /HistData（tagids 5282-5292），
 * 查询区间为 [上次成功采集截止时间 - 重叠窗口, 当前时间]（水位线存 business_config，
 * 见 FireAlarmServiceImpl#WATERMARK_KEY），而非固定的「当前时间前2分钟~当前」，
 * 以便失败、晚到数据、服务重启/停机后自动补齐增量；
 * 过滤 qy=192 且 5292 二次码有效(长度10、非全0)的记录，
 * 匹配消防设备属性并结合 5282/5283/5284 写入 alarm_record（按 点位+时间 去重，窗口重叠不会重复入库）。
 * 方法内兜底异常，不影响后续调度。
 */
@Slf4j
@Component
@AllArgsConstructor
public class FireAlarmJob {

    private final IFireAlarmService fireAlarmService;

    @Scheduled(cron = "0 */2 * * * ?")
    public void collect() {
        log.info("消防报警采集定时任务开始");
        try {
            int n = fireAlarmService.collectFireAlarm();
            log.info("消防报警采集定时任务完成: 新增告警={}", n);
        } catch (Exception e) {
            log.error("消防报警采集定时任务异常", e);
        }
    }
}
