package org.jeecg.modules.bems.visualization.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 时间区间与周期工具类
 * <p>
 * 统一封装"当日、本周、本月、本年"的时间区间计算，以及周期参数归一化。
 * <p>
 * 周期统一使用英文常量 {@link #PERIOD_WEEK} / {@link #PERIOD_MONTH} / {@link #PERIOD_YEAR} 作为内部标识，
 * 展示名通过 {@link #toPeriodLabel(String)} 转换为中文。
 *
 * @author smart-cockpit
 */
public final class DateRangeUtils {

    // ==================== 周期常量（英文，内部统一标识） ====================

    /** 本周 */
    public static final String PERIOD_WEEK = "WEEK";
    /** 本月 */
    public static final String PERIOD_MONTH = "MONTH";
    /** 本年 */
    public static final String PERIOD_YEAR = "YEAR";

    /** 周期顺序：本周 -> 本月 -> 本年 */
    public static final String[] PERIODS = {PERIOD_WEEK, PERIOD_MONTH, PERIOD_YEAR};

    // ==================== 时间常量 ====================

    /** 一天的最早时间 00:00:00.000000000 */
    public static final LocalTime START_OF_DAY = LocalTime.MIN;
    /** 一天的最晚时间 23:59:59.999999999 */
    public static final LocalTime END_OF_DAY = LocalTime.MAX;

    private DateRangeUtils() {
        // 工具类禁止实例化
    }

    // ==================== 周期参数归一化 ====================

    /**
     * 归一化周期入参：兼容 中文 / 英文 / 大小写，统一返回英文常量。
     *
     * @param period 本周 / WEEK / week / 本月 / MONTH / ...
     * @return {@link #PERIOD_WEEK} / {@link #PERIOD_MONTH} / {@link #PERIOD_YEAR}
     */
    public static String normalizePeriod(String period) {
        if (period == null) {
            throw new IllegalArgumentException("period 不能为空");
        }
        switch (period.trim().toUpperCase()) {
            case "WEEK":
            case "本周":
                return PERIOD_WEEK;
            case "MONTH":
            case "本月":
                return PERIOD_MONTH;
            case "YEAR":
            case "本年":
                return PERIOD_YEAR;
            default:
                throw new IllegalArgumentException("period 非法: " + period);
        }
    }

    /**
     * 英文周期 -> 中文展示名
     *
     * @param normalized WEEK / MONTH / YEAR
     * @return 本周 / 本月 / 本年
     */
    public static String toPeriodLabel(String normalized) {
        switch (normalized) {
            case PERIOD_WEEK:  return "本周";
            case PERIOD_MONTH: return "本月";
            case PERIOD_YEAR:  return "本年";
            default: throw new IllegalArgumentException("period 非法: " + normalized);
        }
    }

    /**
     * 周期 -> 时间区间
     *
     * @param normalized WEEK / MONTH / YEAR
     * @return [start, end]
     */
    public static LocalDateTime[] resolveRange(String normalized) {
        switch (normalized) {
            case PERIOD_WEEK:  return currentWeek();
            case PERIOD_MONTH: return currentMonth();
            case PERIOD_YEAR:  return currentYear();
            default: throw new IllegalArgumentException("period 非法: " + normalized);
        }
    }

    /**
     * 归一化 + 解析区间的便捷方法
     *
     * @param period 本周 / WEEK / 本月 / MONTH / 本年 / YEAR
     * @return [start, end]
     */
    public static LocalDateTime[] normalizeAndResolve(String period) {
        return resolveRange(normalizePeriod(period));
    }

    // ==================== 时间区间 ====================

    /**
     * 当日区间（00:00:00 ~ 23:59:59.999999999）
     */
    public static LocalDateTime[] currentDay() {
        LocalDate today = LocalDate.now();
        return of(today, today);
    }

    /**
     * 本周区间（周一 00:00:00 ~ 周日 23:59:59.999999999）
     * <p>
     * 以 ISO 标准，周一为一周的第一天
     */
    public static LocalDateTime[] currentWeek() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return of(monday, sunday);
    }

    /**
     * 本月区间（1 号 00:00:00 ~ 月末 23:59:59.999999999）
     */
    public static LocalDateTime[] currentMonth() {
        LocalDate today = LocalDate.now();
        return of(
                today.with(TemporalAdjusters.firstDayOfMonth()),
                today.with(TemporalAdjusters.lastDayOfMonth())
        );
    }

    /**
     * 本年区间（1 月 1 日 00:00:00 ~ 12 月 31 日 23:59:59.999999999）
     */
    public static LocalDateTime[] currentYear() {
        LocalDate today = LocalDate.now();
        return of(
                today.with(TemporalAdjusters.firstDayOfYear()),
                today.with(TemporalAdjusters.lastDayOfYear())
        );
    }

    /**
     * 根据起止日期组装为 [起始日 00:00:00, 结束日 23:59:59.999999999]
     */
    private static LocalDateTime[] of(LocalDate startDate, LocalDate endDate) {
        return new LocalDateTime[]{
                LocalDateTime.of(startDate, START_OF_DAY),
                LocalDateTime.of(endDate, END_OF_DAY)
        };
    }
}