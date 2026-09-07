package org.jeecg.modules.bems.mdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.jeecg.modules.bems.mdm.entity.MonitorPoint;

import java.util.Collection;

/**
 * 监测采集点 Mapper
 */
public interface MonitorPointMapper extends BaseMapper<MonitorPoint> {

    /**
     * 按 pid 批量更新采集值与采集时间，并置在线=1
     * 一条 SQL 更新多行，减少数据库交互
     *
     * @param list 待更新数据（需含 pid、value、gatherTime）
     */
    @Update("<script>" +
            "UPDATE monitor_point " +
            "SET value = CASE pid " +
            "<foreach collection='list' item='item'>" +
            "WHEN #{item.pid} THEN #{item.value} " +
            "</foreach>" +
            "END, " +
            "gather_time = CASE pid " +
            "<foreach collection='list' item='item'>" +
            "WHEN #{item.pid} THEN #{item.gatherTime} " +
            "</foreach>" +
            "END, " +
            "online = 1 " +
            "WHERE pid IN " +
            "<foreach collection='list' item='item' open='(' separator=',' close=')'>" +
            "#{item.pid}" +
            "</foreach>" +
            "</script>")
    int updateValues(@Param("list") Collection<MonitorPoint> list);
}
