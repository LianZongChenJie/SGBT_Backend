package org.jeecg.modules.bems.job;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.boot.starter.lock.client.RedissonLockClient;
import org.jeecg.modules.bems.energyAnalysis.entity.MeteringPointDataHour;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointCostDataService;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointDataService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;

@Component
@AllArgsConstructor
@Slf4j
public class MeteringPointCostDataUpdateJob {

    private final IMeteringPointDataService meteringPointDataService;
    private final IMeteringPointCostDataService meteringPointCostDataService;
    private final RedissonLockClient redissonLockClient;

    /**
     * 全量任务互斥锁，防止上一次执行未完成时重复触发
     */
    private static final String LOCK_KEY = "lock:bems:meteringPointCostDataUpdate";

    @Scheduled(cron = "0 0 * * * ?")
//    @PostConstruct
    public void meteringPointCostDataUpdate() {
        boolean locked = false;
        try {
            locked = redissonLockClient.tryLock(LOCK_KEY, 10, 60);
            if (!locked) {
                log.warn("上一轮 MeteringPointCostDataUpdateJob 尚未执行完成，本轮跳过");
                return;
            }
            log.info("获取数据开始执行开始执行 MeteringPointCostDataUpdateJob");
            // 计算上一个已完整结束的小时，避免当前小时数据未采集完整导致能耗值偏低
            meteringPointDataService.calculateValue(LocalDateTime.now().minusHours(1));
            List<MeteringPointDataHour> dataHours = meteringPointDataService.getHourLast();
            for (MeteringPointDataHour dataHour : dataHours) {
                meteringPointCostDataService.calculationCost(dataHour.getMeteringPointId(), dataHour.getTime(), dataHour.getValue());
            }
            log.info("calculationCost 执行完成");
        } finally {
            if (locked) {
                redissonLockClient.unlock(LOCK_KEY);
            }
        }
    }

//    public void meteringPointCostDataUpdate2() {
//        boolean locked = false;
//        try {
//            locked = redissonLockClient.tryLock(LOCK_KEY, 10, 60);
//            if (!locked) {
//                log.warn("上一轮 MeteringPointCostDataUpdateJob 尚未执行完成，本轮跳过");
//                return;
//            }
//            log.info("获取数据开始执行开始执行 MeteringPointCostDataUpdateJob");
//
//            // 指定开始时间：2026-09-17 00:00:00
//            LocalDateTime startTime = LocalDateTime.of(2026, 9, 17, 0, 0, 0);
//            // 结束时间：上一个已完整结束的小时
//            LocalDateTime endTime = LocalDateTime.now().minusHours(1).withMinute(0).withSecond(0).withNano(0);
//
//            // 从 2026-09-17 00:00 开始，每小时一个循环
//            LocalDateTime current = startTime;
//            while (!current.isAfter(endTime)) {
//                log.info("开始计算能耗值，时间：{}", current);
//                try {
//                    meteringPointDataService.calculateValue(current);
//                } catch (Exception e) {
//                    log.error("计算能耗值失败，时间：{}", current, e);
//                }
//                current = current.plusHours(1);
//            }
//
//            // 指定开始时间：2026-09-17 00:00:00
//            startTime = LocalDateTime.of(2026, 9, 17, 0, 0, 0);
//            // 结束时间：上一个已完整结束的小时
//            endTime = LocalDateTime.now().minusHours(1).withMinute(0).withSecond(0).withNano(0);
//
//            current = startTime;
//            while (!current.isAfter(endTime)) {
//                List<MeteringPointDataHour> dataHours = meteringPointDataService.getHourLast(current);
//
//                for (MeteringPointDataHour dataHour : dataHours) {
//                    meteringPointCostDataService.calculationCost(dataHour.getMeteringPointId(), dataHour.getTime(), dataHour.getValue());
//                }
//
//                current = current.plusDays(1);
//            }
//
//            log.info("calculationCost 执行完成");
//        } finally {
//            if (locked) {
//                redissonLockClient.unlock(LOCK_KEY);
//            }
//        }
//    }
}
