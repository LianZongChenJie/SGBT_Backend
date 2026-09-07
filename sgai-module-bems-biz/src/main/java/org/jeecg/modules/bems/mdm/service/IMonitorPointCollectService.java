package org.jeecg.modules.bems.mdm.service;

/**
 * 监测点实时数据采集服务（读点 -> 更新实时值表 + 写 15 分钟历史）
 * <p>
 * 参照 fwbz 楼控 BuildingControlRealPushService 落库骨架：
 * 每 15 分钟从 monitor_point 读点(pid)，调用第三方实时接口拉取当前值，
 * 覆盖更新 monitor_point.value/gather_time（实时值，只留最新），
 * 并按 15 分钟对齐槽位 upsert 到 monitor_point_history（每点每槽一条）。
 */
public interface IMonitorPointCollectService {

    /**
     * 执行一次全量采集（定时任务入口）
     */
    void collectOnce();
}
