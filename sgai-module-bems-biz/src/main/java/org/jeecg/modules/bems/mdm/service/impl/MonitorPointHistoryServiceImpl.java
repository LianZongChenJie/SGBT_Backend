package org.jeecg.modules.bems.mdm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.bems.mdm.entity.MonitorPointHistory;
import org.jeecg.modules.bems.mdm.mapper.MonitorPointHistoryMapper;
import org.jeecg.modules.bems.mdm.service.IMonitorPointHistoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 监测点历史 Service 实现
 */
@Service
@AllArgsConstructor
public class MonitorPointHistoryServiceImpl extends ServiceImpl<MonitorPointHistoryMapper, MonitorPointHistory>
        implements IMonitorPointHistoryService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public IPage<MonitorPointHistory> pageHistory(Long pid, String category, String startTime, String endTime,
                                                  long pageNo, long pageSize) {
        LambdaQueryWrapper<MonitorPointHistory> qw = new LambdaQueryWrapper<>();
        if (pid != null) {
            qw.eq(MonitorPointHistory::getPid, pid);
        } else {
            if (StringUtils.isNotBlank(category)) {
                qw.eq(MonitorPointHistory::getCategory, category);
            }
        }
        if (StringUtils.isNotBlank(startTime)) {
            qw.ge(MonitorPointHistory::getCollectionTime, LocalDateTime.parse(startTime, FMT));
        }
        if (StringUtils.isNotBlank(endTime)) {
            qw.le(MonitorPointHistory::getCollectionTime, LocalDateTime.parse(endTime, FMT));
        }
        qw.orderByAsc(MonitorPointHistory::getCollectionTime);
        return page(new Page<>(pageNo, pageSize), qw);
    }
}
