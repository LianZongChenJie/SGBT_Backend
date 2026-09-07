package org.jeecg.modules.bems.mdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.bems.mdm.entity.MonitorPointHistory;

import java.util.Collection;

/**
 * 监测点历史 Mapper
 */
public interface MonitorPointHistoryMapper extends BaseMapper<MonitorPointHistory> {

    /**
     * 批量写入历史；同一 (pid, collection_time) 槽位已存在则更新 value（upsert）
     *
     * @param list 待写入历史列表
     */
    @Insert("<script>" +
            "INSERT INTO monitor_point_history(pid, category, value, collection_time) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.pid}, #{item.category}, #{item.value}, #{item.collectionTime})" +
            "</foreach> " +
            "ON DUPLICATE KEY UPDATE value = VALUES(value)" +
            "</script>")
    int upsertHistory(@Param("list") Collection<MonitorPointHistory> list);
}
