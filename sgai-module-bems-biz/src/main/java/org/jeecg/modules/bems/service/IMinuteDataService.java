package org.jeecg.modules.bems.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.bems.entity.MinuteData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface IMinuteDataService extends IService<MinuteData> {

    MinuteData findByDeviceAndTime(Long deviceId, LocalDateTime time);

    List<MinuteData> findByDeviceIdsAndTime(Collection<Long> deviceIds, LocalDateTime time);

    List<MinuteData> findByTimeRange(LocalDateTime startTime,LocalDateTime endTime);

    boolean saveOrUpdate(MinuteData minuteData);

    void preGeneration(List<Long> deviceIds, LocalDate date);
    /**
     * 获取设备最新的（上一条）分钟数据
     *
     * @param deviceId 设备id
     * @return 最新的分钟数据
     */
    MinuteData findLatest(Long deviceId);
}
