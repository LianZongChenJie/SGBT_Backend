package org.jeecg.modules.bems.mdm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.bems.mdm.entity.MonitorPointHistory;

/**
 * 监测点历史 Service
 */
public interface IMonitorPointHistoryService extends IService<MonitorPointHistory> {

    /**
     * 分页查询历史值：优先按 pid 查单点，否则按 category 查整类
     *
     * @param pid        点ID（可空）
     * @param category   设备类别（pid 为空时生效）
     * @param startTime  开始时间 yyyy-MM-dd HH:mm:ss（可空）
     * @param endTime    结束时间 yyyy-MM-dd HH:mm:ss（可空）
     */
    IPage<MonitorPointHistory> pageHistory(Long pid, String category, String startTime, String endTime,
                                           long pageNo, long pageSize);
}
