package org.jeecg.modules.bems.mdm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.bems.mdm.entity.MonitorPoint;
import org.jeecg.modules.bems.mdm.mapper.MonitorPointMapper;
import org.jeecg.modules.bems.mdm.service.IMonitorPointService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 监测采集点 Service 实现
 */
@Service
@AllArgsConstructor
public class MonitorPointServiceImpl extends ServiceImpl<MonitorPointMapper, MonitorPoint> implements IMonitorPointService {

    @Override
    public IPage<MonitorPoint> pagePoints(String category, Integer online, String keyword,
                                          long pageNo, long pageSize) {
        LambdaQueryWrapper<MonitorPoint> qw = new LambdaQueryWrapper<MonitorPoint>();
        if (StringUtils.isNotBlank(category)) {
            qw.eq(MonitorPoint::getCategory, category);
        }
        if (online != null) {
            qw.eq(MonitorPoint::getOnline, online);
        }
        if (StringUtils.isNotBlank(keyword)) {
            qw.and(w -> w.like(MonitorPoint::getDescription, keyword)
                    .or().like(MonitorPoint::getLongName, keyword));
        }
        qw.orderByAsc(MonitorPoint::getCategory).orderByAsc(MonitorPoint::getPid);
        return page(new Page<>(pageNo, pageSize), qw);
    }

    @Override
    public List<String> listCategories() {
        return list(new LambdaQueryWrapper<MonitorPoint>()
                        .select(MonitorPoint::getCategory)
                        .groupBy(MonitorPoint::getCategory)
                        .orderByAsc(MonitorPoint::getCategory))
                .stream()
                .map(MonitorPoint::getCategory)
                .collect(Collectors.toList());
    }
}
