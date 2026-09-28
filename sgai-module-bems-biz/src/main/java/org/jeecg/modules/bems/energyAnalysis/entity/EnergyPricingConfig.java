package org.jeecg.modules.bems.energyAnalysis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jeecg.modules.bems.energyAnalysis.util.pricing.LadderPricing;
import org.jeecg.modules.bems.entity.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 能源价格配置
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("energy_pricing_config")
public class EnergyPricingConfig extends BaseEntity {

    public static final DateTimeFormatter filedForMatter = DateTimeFormatter.ofPattern("MM-HH");


    /**
     * 电
     */
    public static final String CATEGORY_ELECTRICITY = "electricity";
    /**
     * 水
     */
    public static final String CATEGORY_WATER = "water";

    /**
     * 热
     */
    public static final String CATEGORY_HEATING = "heating";

    /**
     * 状态：启用
     */
    public static final String STATUS_ENABLE = "1";
    /**
     * 状态：禁用
     */
    public static final String STATUS_DISABLE = "0";

    /**
     * 仪表类别id
     */
    private Long categoryId;
    /**
     * 类别。电：electricity；水：water；热：heating
     */
    private String category;
    /**
     * 计价方式 1-峰谷分时计价 2-固定计价 3-阶梯计价
     */
    private String billingWay;
    /**
     * 固定单价
     */
    private BigDecimal fixedUnitPrice;
    /**
     * 阶梯计价-第一阶段-最大值
     */
    private BigDecimal step1Max;
    /**
     * 阶梯计价-第一阶段-单价
     */
    private BigDecimal step1UnitPrice;
    /**
     * 阶梯计价-第二阶段-最大值
     */
    private BigDecimal step2Max;
    /**
     * 阶梯计价-第二阶段-最小值
     */
    private BigDecimal step2Min;
    /**
     * 阶梯计价-第二阶段-单价
     */
    private BigDecimal step2UnitPrice;
    /**
     * 阶梯计价-第三阶段-最小值
     */
    private BigDecimal step3Min;
    /**
     * 阶梯计价-第三阶段-单价
     */
    private BigDecimal step3UnitPrice;
    /**
     * 峰谷分时计价-尖电价
     */
    private BigDecimal tipPrice;
    /**
     * 峰谷分时计价-峰电价
     */
    private BigDecimal peakPrice;
    /**
     * 峰谷分时计价-平电价
     */
    private BigDecimal flatPrice;
    /**
     * 峰谷分时计价-谷电价
     */
    private BigDecimal valleyPrice;
    /**
     * 峰谷分时计价-适用月份1
     */
    private String applyMonths1;
    /**
     * 峰谷分时计价-尖时段1
     */
    private String tipTimeSlot1;
    /**
     * 峰谷分时计价-峰时段1
     */
    private String peakTimeSlot1;
    /**
     * 峰谷分时计价-平时段1
     */
    private String flatTimeSlot1;
    /**
     * 峰谷分时计价-谷时段1
     */
    private String valleyTimeSlot1;
    /**
     * 峰谷分时计价-适用月份2
     */
    private String applyMonths2;
    /**
     * 峰谷分时计价-尖时段2
     */
    private String tipTimeSlot2;
    /**
     * 峰谷分时计价-峰时段2
     */
    private String peakTimeSlot2;
    /**
     * 峰谷分时计价-平时段2
     */
    private String flatTimeSlot2;
    /**
     * 峰谷分时计价-谷时段2
     */
    private String valleyTimeSlot2;

    /**
     * 启用：1；禁用：0
     */
    private String status;

    /**
     * 格式化峰谷分时计价
     *
     * @return 格式化后的峰谷分时计价，key：MM:HH，value：价格
     */
    public Map<String, BigDecimal> formatPVTS() {
        Map<String, BigDecimal> res = new HashMap<>();
        // 第一段（主方案）
        res.putAll(formatPVTS(
                this.getApplyMonths1(),
                this.getTipTimeSlot1(), this.getTipPrice(),
                this.getPeakTimeSlot1(), this.getPeakPrice(),
                this.getFlatTimeSlot1(), this.getFlatPrice(),
                this.getValleyTimeSlot1(), this.getValleyPrice()
        ));
        // 第二段（备用方案，可能为 null，方法内已判空）
        res.putAll(formatPVTS(
                this.getApplyMonths2(),
                this.getTipTimeSlot2(), this.getTipPrice(),
                this.getPeakTimeSlot2(), this.getPeakPrice(),
                this.getFlatTimeSlot2(), this.getFlatPrice(),
                this.getValleyTimeSlot2(), this.getValleyPrice()
        ));
        return res;
    }

    public List<LadderPricing> formatLadderPricing() {
        List<LadderPricing> res = new ArrayList<>();
        res.add(new LadderPricing(BigDecimal.ZERO, this.getStep1Max(), this.getStep1UnitPrice()));
        res.add(new LadderPricing(this.getStep2Min(), this.getStep2Max(), this.getStep2UnitPrice()));
        res.add(new LadderPricing(this.getStep3Min(), null, this.getStep3UnitPrice()));
        return res;
    }

    /**
     * 获取当前能耗成本
     *
     * @param pricings 阶梯价格
     * @param history  历史用量
     * @param now      当前用量
     */
    private BigDecimal calculationLadderPricing(List<LadderPricing> pricings, BigDecimal history, BigDecimal now) {
        BigDecimal total = history.add(now);
        BigDecimal cost = BigDecimal.ZERO;
        for (LadderPricing pricing : pricings) {
            if (total.compareTo(pricing.getStepMin()) <= 0) {
                continue;
            }
            if (pricing.getStepMax() == null) {
                if (history.compareTo(pricing.getStepMin()) >= 0) {
                    cost = cost.add(now.multiply(pricing.getPricing()));
                } else {
                    cost = cost.add(total.subtract(pricing.getStepMin()).multiply(pricing.getPricing()));
                }
            } else if (total.compareTo(pricing.getStepMax()) <= 0) {
                if (history.compareTo(pricing.getStepMin()) >= 0) {
                    cost = cost.add(now.multiply(pricing.getPricing()));
                } else {
                    cost = cost.add(total.subtract(pricing.getStepMin()).multiply(pricing.getPricing()));
                }
            } else {
                if (history.compareTo(pricing.getStepMax()) >= 0) {
                    continue;
                } else if (history.compareTo(pricing.getStepMin()) >= 0) {
                    cost = cost.add(pricing.getStepMax().subtract(history).multiply(pricing.getPricing()));
                } else {
                    cost = cost.add(pricing.getStepMax().subtract(pricing.getStepMin()).multiply(pricing.getPricing()));
                }
            }
        }
        return cost;
    }

    private Map<String, BigDecimal> formatPVTS(String months,
                                               String tipTimeSlot, BigDecimal tipPrice,
                                               String peakTimeSlot, BigDecimal peakPrice,
                                               String flatTimeSlot, BigDecimal flatPrice,
                                               String valleyTimeSlot, BigDecimal valleyPrice) {
        Map<String, BigDecimal> res = new HashMap<>();
        if (months == null || months.trim().isEmpty()) {
            return res;
        }
        String[] applyMonths = months.split(",");
        for (String month : applyMonths) {
            String m = month.trim();
            if (m.isEmpty()) continue;
            // 尖时段（蒙西无，跳过）
            putSlot(res, m, tipTimeSlot, tipPrice);
            // 高峰
            putSlot(res, m, peakTimeSlot, peakPrice);
            // 平段
            putSlot(res, m, flatTimeSlot, flatPrice);
            // 低谷
            putSlot(res, m, valleyTimeSlot, valleyPrice);
        }
        return res;
    }

    /**
     * 将某个时段字符串按逗号拆分，逐个写入 map；空值直接跳过
     */
    private void putSlot(Map<String, BigDecimal> res, String month,
                         String timeSlot, BigDecimal price) {
        if (timeSlot == null || timeSlot.trim().isEmpty() || price == null) {
            return;
        }
        int mon = Integer.parseInt(month.trim());
        for (String item : timeSlot.split(",")) {
            String slot = item.trim();
            if (slot.isEmpty()) continue;
            // slot 形如 "12:00-17:00"
            String[] range = slot.split("-");
            if (range.length != 2) continue;
            int startHour = Integer.parseInt(range[0].split(":")[0]);
            int endHour = Integer.parseInt(range[1].split(":")[0]);
            for (int h = startHour; h < endHour; h++) {
                LocalDateTime dt = LocalDateTime.of(LocalDate.now().getYear(), mon, 1, h, 0);
                String key = filedForMatter.format(dt);
                res.put(key, price);
            }
        }
    }
}
