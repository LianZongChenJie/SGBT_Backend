package org.jeecg.modules.bems.visualization.zhjsc.service;

import org.jeecg.modules.bems.visualization.zhjsc.vo.RealtimeSnapshotVO;

import java.util.List;

/**
 * 能源站实时数据
 */
public interface EnergyStationRealtimeService {

    /**
     * 取能源站集成实时数据（读库，数据随采集任务每 15 分钟刷新一次）
     *
     * @param keys 点位 key 列表，为空则返回白名单全部点位；未知 key 会被忽略
     * @return 实时数据快照，不会返回 null
     */
    RealtimeSnapshotVO snapshot(List<String> keys);
}
