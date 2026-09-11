package org.jeecg.modules.bems.hikvision.job;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.hikvision.service.ICameraResourceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 海康看板数据同步定时任务
 * <p>每5分钟从海康API同步一次客流数据及人员统计数据；
 * 每1分钟同步一次摄像头/门禁点/门禁设备在线情况及门禁点事件。
 * 各项同步失败不抛异常，避免影响后续调度。</p>
 *
 * @author bems
 */
@Slf4j
@Component
@AllArgsConstructor
public class HikvisionDashboardJob {



    private final ICameraResourceService cameraResourceService;


    /**
     * 每1分钟执行一次设备在线状态及门禁点事件同步
     * <p>依次同步摄像头在线情况、门禁点在线情况、门禁设备在线情况、门禁点事件，
     * 各项独立 try-catch，任一项失败不影响其他项及后续调度。</p>
     */
    @Scheduled(cron = "0 */2 * * * ?")
    public void syncDeviceStatus() {
//        try {
//            int count = cameraResourceService.syncOnlineStatus();
//            log.info("摄像头在线情况同步完成, 更新{}条", count);
//        } catch (Exception e) {
//            log.error("摄像头在线情况同步异常", e);
//        }
//        try {
//            int count = doorResourceService.syncDoorStatus();
//        } catch (Exception e) {
//            log.error("门禁点在线情况同步异常", e);
//        }
//        try {
//            int count = acsDeviceService.syncOnlineStatus();
//        } catch (Exception e) {
//            log.error("门禁设备在线情况同步异常", e);
//        }
//        try {
//            int count = doorEventService.syncFromHikvision();
//        } catch (Exception e) {
//            log.error("门禁点事件同步异常", e);
//        }
    }

    /**
     * 每5分钟执行一次看板数据同步（客流 + 人员统计）
     */
    // @Scheduled(cron = "0 */5 * * * ?")
    /*
    public void syncDashboardData() {
        log.debug("海康看板数据同步定时任务开始执行");
        try {
            dashboardTaskService.syncVisitorFlow();
            log.info("海康客流数据同步完成");
        } catch (Exception e) {
            log.error("海康客流数据同步异常", e);
        }
        try {
            dashboardTaskService.syncPersonRecognition();
            log.info("人员识别记录同步完成");
        } catch (Exception e) {
            log.error("人员识别记录同步异常", e);
        }
        try {
            dashboardTaskService.syncPersonnelStatistics();
            log.info("人员统计数据同步完成");
        } catch (Exception e) {
            log.error("人员统计数据同步异常", e);
        }
        log.info("海康看板数据同步定时任务结束");
    }*/
}
