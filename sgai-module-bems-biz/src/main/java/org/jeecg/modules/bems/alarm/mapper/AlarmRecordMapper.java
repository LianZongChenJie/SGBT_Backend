package org.jeecg.modules.bems.alarm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.bems.alarm.entity.AlarmRecord;

import java.util.List;
import java.util.Map;

public interface AlarmRecordMapper extends BaseMapper<AlarmRecord> {
    @Select("SELECT " +
            "    alarm_category_name AS name, " +
            "    COUNT(*)            AS value " +
            "FROM alarm_record " +
            "GROUP BY alarm_category_name " +
            "ORDER BY value DESC")
    List<Map<String, Object>> selectAlarmCountByCategory();

    @Select("SELECT " +
            "    device_name         AS deviceName, " +
            "    alarm_time          AS alarmTime, " +
            "    alarm_category_name AS alarmCategoryName " +
            "FROM alarm_record " +
            "ORDER BY alarm_time DESC")
    List<Map<String, Object>> selectSimpleAlarmList();
}