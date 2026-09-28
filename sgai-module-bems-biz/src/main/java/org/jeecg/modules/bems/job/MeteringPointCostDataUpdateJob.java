package org.jeecg.modules.bems.job;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.boot.starter.lock.client.RedissonLockClient;
import org.jeecg.modules.bems.energyAnalysis.entity.MeteringPointDataHour;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointCostDataService;
import org.jeecg.modules.bems.energyAnalysis.service.IMeteringPointDataService;
import org.jeecg.modules.bems.mq.constant.MqConstant;
import org.springframework.amqp.core.Message;
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
}
