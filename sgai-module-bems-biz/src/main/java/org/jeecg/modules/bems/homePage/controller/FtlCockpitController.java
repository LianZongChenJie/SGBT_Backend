package org.jeecg.modules.bems.homePage.controller;

import lombok.AllArgsConstructor;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.bems.energyAnalysis.entity.MeteringPointDataDay;
import org.jeecg.modules.bems.energyAnalysis.entity.MeteringPointDataHour;
import org.jeecg.modules.bems.energyAnalysis.entity.MeteringPointDataMonth;
import org.jeecg.modules.bems.energyAnalysis.entity.MeteringPointDataYear;
import org.jeecg.modules.bems.energyAnalysis.service.*;
import org.jeecg.modules.bems.energyAnalysis.vo.Chat;
import org.jeecg.modules.bems.energyAnalysis.vo.ChatSeries;
import org.jeecg.modules.bems.homePage.dto.CockpitCarbonEmissionsPerUnitArea;
import org.jeecg.modules.bems.homePage.dto.CockpitEnergyConsumptionTrend;
import org.jeecg.modules.bems.homePage.dto.EnergyAlarmEvent;
import org.jeecg.modules.bems.service.IBusinessConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 负碳楼驾驶舱相关接口
 */
@RestController
@RequestMapping("/bems/cockpit")
@AllArgsConstructor
public class FtlCockpitController {

    private static final String ENERGY_CONSUMPTION_MONTH_PATH = "cockpit:energy_consumption:month:";

    private static final String ENERGY_CONSUMPTION_YEAR_PATH = "cockpit:energy_consumption:year:";

    private static final String ENERGY_CONSUMPTION_DAY_PATH = "cockpit:energy_consumption:day:";

    private static final String ENERGY_CONSUMPTION_SEVEN_DAYS_PATH = "cockpit:energy_consumption:seven_days:";

    private static final String ENERGY_CONSUMPTION_YEAR_MONTH_PATH = "cockpit:energy_consumption:year_month:";

    private static final String CARBON_EMISSION_YEAR = "cockpit:carbon_emission:year:";

    private static final String CARBON_EMISSION_TREND = "cockpit:carbon_emission:trend:";

    private static final String CARBON_EMISSION_PRE_UNIT_AREA = "cockpit:carbon_emission:pre_unit_area:";

    private static final String ENERGY_CONSUMPTION_DAY_MOM = "cockpit:energy_consumption:day:mom:";

    private static final String ENERGY_CONSUMPTION_YEAR_MOM = "cockpit:energy_consumption:year:mom:";

    private static final String ENERGY_ALARM_EVENT = "cockpit:energy_alarm_event:";

    private final IMeteringPointDataYearService meteringPointDataYearService;

    private final IMeteringPointDataMonthService meteringPointDataMonthService;

    private final IMeteringPointDataDayService meteringPointDataDayService;

    private final IMeteringPointDataHourService meteringPointDataHourService;

    private final IBusinessConfigService businessConfigService;

    private final ICarbonEmissionFactorService carbonEmissionFactorService;

    /**
     * 年能耗量
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/energyConsumptionThisYear")
    public Result<String> energyConsumptionThisYear(@RequestParam(value = "path", required = false)String path){
        path = ENERGY_CONSUMPTION_YEAR_PATH + path;
        String result = "0";
        // 获取计量点
        Long pointId = businessConfigService.getLongByKey(path);
        if(pointId == null){
            return Result.OK("", result);
        }
        // 获取年用水量
        MeteringPointDataYear meteringPointDataYear = meteringPointDataYearService.findByDateAndPointId(LocalDate.now(), pointId);
        if(meteringPointDataYear != null){
            result = meteringPointDataYear.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return Result.OK("",result);
    }

    /**
     * 月能耗量
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/energyConsumptionThisMonth")
    public Result<String> energyConsumptionThisMonth(@RequestParam(value = "path", required = false)String path){
        path = ENERGY_CONSUMPTION_MONTH_PATH + path;
        String result = "0";
        // 获取计量点
        Long pointId = businessConfigService.getLongByKey(path);
        if(pointId == null){
            return Result.OK("", result);
        }
        // 获取月用水量
        MeteringPointDataMonth meteringPointDataMonth = meteringPointDataMonthService.findByDateAndPointId(LocalDate.now(), pointId);
        if(meteringPointDataMonth != null){
            result = meteringPointDataMonth.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return Result.OK("",result);
    }

    /**
     * 日能耗量
     * @param path 点位路径
     */
    @IgnoreAuth
    @GetMapping("/energyConsumptionToDay")
    public Result<String> energyConsumptionToDay(@RequestParam(value = "path",required = false) String path){
        path = ENERGY_CONSUMPTION_DAY_PATH + path;
        String result = "0";
        // 获取计量点
        Long pointId = businessConfigService.getLongByKey(path);
        if(pointId == null){
            return Result.OK("", result);
        }
        // 获取月用水量
        MeteringPointDataDay meteringPointDataDay = meteringPointDataDayService.findByDateAndPointId(LocalDate.now(), pointId);
        if(meteringPointDataDay != null){
            result = meteringPointDataDay.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
        return Result.OK("",result);
    }

    /**
     * 7天能耗量趋势
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/energyConsumptionTrendInThePastSevenDays")
    public Result<Chat> energyConsumptionTrendInThePastSevenDays(@RequestParam(value="path",required = false)String path){
        path = ENERGY_CONSUMPTION_SEVEN_DAYS_PATH + path;
        // 获取计量点
        Long pointId = businessConfigService.getLongByKey(path);
        LocalDate date = LocalDate.now();
        // 横坐标
        List<String> xAxis = IntStream.range(0, 8).mapToObj(i -> date.minusDays(7-i).format(DateTimeFormatter.ofPattern("MM/dd"))).collect(Collectors.toList());
        // 获取能耗数据
        Map<String,BigDecimal> dataMap =  meteringPointDataDayService.findByTimeRangeAndPointId(date.minusDays(7), date.minusDays(1), pointId)
                .stream()
                .filter(item -> item.getTime() != null && item.getValue() != null)
                .collect(Collectors.toMap(item -> item.getTime().format(DateTimeFormatter.ofPattern("MM/dd")),MeteringPointDataDay::getValue));
        Chat chat = new Chat();
        chat.setXAxis(xAxis);
        List<ChatSeries> chatSeriesList = new ArrayList<>();
        List<Object> data = new ArrayList<>();
        for(String day : xAxis){
            data.add(dataMap.getOrDefault(day, BigDecimal.ZERO));
        }
        ChatSeries chatSeries = new ChatSeries("能耗", data);
        chatSeriesList.add(chatSeries);
        chat.setChatSeriesList(chatSeriesList);
        return Result.ok(chat);
    }

    /**
     * 当年每月能耗趋势
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/energyConsumptionTrendEveryMonthOfThatYear")
    public Result<Chat> energyConsumptionTrendEveryMonthOfThatYear(@RequestParam(value="path",required = false)String path){
        path = ENERGY_CONSUMPTION_YEAR_MONTH_PATH + path;
        // 获取点位信息
        List<CockpitEnergyConsumptionTrend> configs = businessConfigService.getListByKey(path, CockpitEnergyConsumptionTrend.class);
        // 获取数据
        LocalDate end = LocalDate.now();
        LocalDate start = end.withMonth(1);
        Chat chat = new Chat();
        List<ChatSeries> chatSeriesList = new ArrayList<>();
        List<String> xAxis = IntStream.range(1,end.getMonthValue()+1).mapToObj(i -> i + "月").toList();
        for(CockpitEnergyConsumptionTrend config : configs){
            List<Object> data = new ArrayList<>();
            Map<String,String> map = meteringPointDataMonthService.findByTimeRangeAndPointId(start, end, config.getPointId())
                    .stream()
                    .filter(item -> item.getValue() != null && item.getTime() != null)
                    .collect(Collectors.toMap(item -> item.getTime().getMonthValue() + "月",item -> item.getValue().setScale(2,RoundingMode.HALF_UP).toPlainString()));
            for(String month : xAxis){
                data.add(map.getOrDefault(month, "0"));
            }
            ChatSeries chatSeries = new ChatSeries(config.getName(),data,config.getUnit());
            chatSeriesList.add(chatSeries);
        }
        chat.setXAxis(xAxis);
        chat.setChatSeriesList(chatSeriesList);
        return Result.ok(chat);
    }

    /**
     * 年碳排量
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/carbonEmissionsThisYear")
    public Result<String> carbonEmissionsThisYear(@RequestParam(value = "path", required = false)String path){
        path = CARBON_EMISSION_YEAR + path;
        String result = "0";
        // 获取点位id
        Long pointId = businessConfigService.getLongByKey(path);
        return Result.ok(getCarbonEmissionsThisYear(pointId).toPlainString());
    }

    /**
     * 单位面积碳排量
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/carbonEmissionsPerUnitAreaThisYear")
    public Result<String> carbonEmissionsPerUnitAreaThisYear(@RequestParam(value = "path",required = false)String path){
        path = CARBON_EMISSION_PRE_UNIT_AREA + path;
        CockpitCarbonEmissionsPerUnitArea config = businessConfigService.getObjectByKey(path, CockpitCarbonEmissionsPerUnitArea.class);
        // 获取当年
        BigDecimal value = getCarbonEmissionsThisYear(config.getPointId());
        return Result.ok(value.divide(config.getArea(),2,RoundingMode.HALF_UP).toPlainString());
    }

    private BigDecimal getCarbonEmissionsThisYear(Long pointId){
        BigDecimal emissionFactor = carbonEmissionFactorService.getElectricityCarbonEmissionFactor();
        LocalDate now = LocalDate.now();
        MeteringPointDataYear pointDataYear = meteringPointDataYearService.findByDateAndPointId(now, pointId);
        if(pointDataYear != null){
            return pointDataYear.getValue().multiply(emissionFactor).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 碳排趋势
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/carbonEmissionsTrend")
    public Result<Chat> carbonEmissionsTrend(@RequestParam(value = "path",required = false)String path){
        // 获取点位id
        Long pointId = businessConfigService.getLongByKey(CARBON_EMISSION_TREND + path);
        BigDecimal emissionFactor = carbonEmissionFactorService.getElectricityCarbonEmissionFactor();
        LocalDate now = LocalDate.now();
        Map<String,String> list = meteringPointDataMonthService.findByTimeRangeAndPointId(now.withMonth(1), now, pointId)
                .stream()
                .filter(item -> item.getTime() != null && item.getValue() != null)
                .collect(Collectors.toMap(
                        item -> item.getTime().getMonthValue() + "月",
                        item -> item.getValue().multiply(emissionFactor).setScale(2, RoundingMode.HALF_UP).toPlainString()));
        // 将list中数据组装为chat格式数据，其中xAxis为月份，yAxis为碳排量
        List<String> xAxis = IntStream.range(1, now.getMonthValue()).mapToObj(i -> i + "月").toList();
        Chat chat = new Chat();
        List<Object> data = new ArrayList<>();
        for(String str : xAxis){
            data.add(list.getOrDefault(str, "0"));
        }
        ChatSeries chatSeries = new ChatSeries("碳排量", data);
        chat.setChatSeriesList(List.of(chatSeries));
        chat.setXAxis(xAxis);
        return Result.ok(chat);
    }

    /**
     * 当天能耗量与前一天相比
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/energyConsumptionToDayMom")
    public Result<Map<String,String>> energyConsumptionToDayMom(@RequestParam(value = "path", required = false)String path) {
        // 获取点位id
        Long pointId = businessConfigService.getLongByKey(ENERGY_CONSUMPTION_DAY_MOM + path);
        LocalDateTime now = LocalDateTime.now();
        MeteringPointDataDay nowData = meteringPointDataDayService.findByDateAndPointId(now.toLocalDate(), pointId);
        // 获取前一天数据
        BigDecimal lastValue = meteringPointDataHourService.findByPointIdAndTimeRange(pointId, now.minusDays(1).withHour(0).withMinute(0).withSecond(0),now.minusDays(1))
                .stream()
                .filter(item -> item.getTime() != null && item.getValue() != null)
                .map(MeteringPointDataHour::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = nowData == null ? BigDecimal.ZERO : nowData.getValue();
        Map<String,String> result = new HashMap<>();
        result.put("value",value.setScale(2,RoundingMode.HALF_UP).toPlainString());
        result.put("mom",rate(lastValue,value));
        return Result.ok(result);
    }

    /**
     * 当年能耗量与上一年相比
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/energyConsumptionThisYearMom")
    public Result<Map<String,String>> energyConsumptionThisYearMom(@RequestParam(value = "path", required = false)String path){
        Long pointId = businessConfigService.getLongByKey(ENERGY_CONSUMPTION_YEAR_MOM + path);
        LocalDate now = LocalDate.now();
        MeteringPointDataYear pointDataYear = meteringPointDataYearService.findByDateAndPointId(now, pointId);
        BigDecimal lastYear = meteringPointDataMonthService.findByTimeRangeAndPointId(now.minusYears(1).withDayOfYear(1),now.minusYears(1),pointId)
                .stream()
                .filter(item -> item.getTime() != null && item.getValue() != null)
                .map(MeteringPointDataMonth::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = pointDataYear == null || pointDataYear.getValue() == null ? BigDecimal.ZERO : pointDataYear.getValue();

        Map<String,String> result = new HashMap<>();
        result.put("value",value.setScale(2,RoundingMode.HALF_UP).toPlainString());
        result.put("mom",rate(lastYear,value));
        return Result.ok(result);
    }

    /**
     * 能源告警事件
     * @param path 路径
     */
    @IgnoreAuth
    @GetMapping("/energyAlarmEvent")
    public Result<?> energyAlarmEvent(@RequestParam(value = "path",required = false)String path){
        path = ENERGY_ALARM_EVENT + path;
        // 获取配置信息
        List<EnergyAlarmEvent> data = businessConfigService.getListByKey(path, EnergyAlarmEvent.class);
        return Result.ok(data);
    }

    /**
     * 计算环比增长率
     *
     * @param before 上期数值
     * @param after  本期数值
     * @return 环比增长率（以百分比形式表示，保留两位小数）
     */
    public String rate(BigDecimal before, BigDecimal after) {
        if (before == null || after == null || before.compareTo(BigDecimal.ZERO) == 0) {
            // 如果分母为零或者任一数据为空，则返回特定信息
            return "";
        } else {
            // 计算环比增长率
            BigDecimal growthRate = after.subtract(before).divide(before, 4, RoundingMode.HALF_UP);
            // 格式化结果为百分比形式，保留两位小数
            DecimalFormat df = new DecimalFormat("+0.00%;-0.00%");
            return df.format(growthRate);
        }
    }
}
