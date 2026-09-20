package org.jeecg.modules.bems.job;

import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

        @Scheduled(cron = "0 */30 * * * ?")
//    @PostConstruct
    public void meteringPointCostDataUpdate() {
        log.info("获取数据开始执行开始执行 MeteringPointCostDataUpdateJob");
        meteringPointDataService.calculateValue(LocalDateTime.now());
        List<MeteringPointDataHour> dataHours = meteringPointDataService.getHourLast();
        for (MeteringPointDataHour dataHour : dataHours){
            meteringPointCostDataService.calculationCost(dataHour.getMeteringPointId(), dataHour.getTime(), dataHour.getValue());
        }

        log.info("calculationCost 执行完成");
    }
}
