package org.jeecg.modules.bems.mdm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.jeecg.modules.bems.mdm.entity.DeviceAttributeHistory;
import org.jeecg.modules.bems.visualization.vo.PowerTrendVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface DeviceAttributeHistoryMapper extends BaseMapper<DeviceAttributeHistory> {

    @Select("""
        SELECT SUM(CAST(t.value AS DECIMAL(20,4))) AS total_value
        FROM (
            SELECT h.value,
                   a.attribute_name,
                   ROW_NUMBER() OVER (
                       PARTITION BY h.attribute_id
                       ORDER BY h.collection_time DESC
                   ) AS rn
            FROM device_attribute_history h
            INNER JOIN device_attribute a ON a.id = h.attribute_id
            WHERE a.attribute_name LIKE CONCAT('%', #{name}, '%')
              AND h.value REGEXP '^-?[0-9]+([.][0-9]+)?$'
        ) t
        WHERE t.rn = 1
        """)
    BigDecimal sumLatestValueByAttributeName(@Param("name") String name);

    @Select("""
        SELECT a.attribute_name AS attributeName,
               SUM(CAST(t.value AS DECIMAL(20,4))) AS totalValue
        FROM (
            SELECT h.value,
                   h.attribute_id,
                   ROW_NUMBER() OVER (
                       PARTITION BY h.attribute_id
                       ORDER BY h.collection_time DESC
                   ) AS rn
            FROM device_attribute_history h
            INNER JOIN device_attribute a ON a.id = h.attribute_id
            WHERE a.attribute_name LIKE CONCAT('%', #{name}, '%')
              AND h.value REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
        ) t
        INNER JOIN device_attribute a ON a.id = t.attribute_id
        WHERE t.rn = 1
        GROUP BY a.attribute_name
        """)
    List<Map<String, Object>> sumLatestValueGroupByAttributeName(@Param("name") String name);
    /**
     * 按时间粒度统计某类型设备的日发电量之和
     *
     * @param attrName   属性名，这里传 "日发电量"
     * @param startTime  开始时间
     * @param endTime    结束时间（左闭右开）
     * @param dateFormat 时间粒度：'%Y-%m-%d' 天 / '%Y-%m' 月 / '%Y' 年
     */
    @Select("""
            SELECT DATE_FORMAT(h.collection_time, #{dateFormat}) AS bucketTime,
                   SUM(CAST(h.value AS DECIMAL(20,4)))           AS totalValue
            FROM device_attribute_history h
            INNER JOIN device_attribute a ON a.id = h.attribute_id
            WHERE a.attribute_name LIKE CONCAT('%', #{attrName}, '%')
              AND h.collection_time >= #{startTime}
              AND h.collection_time <  #{endTime}
              AND h.value REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
            GROUP BY DATE_FORMAT(h.collection_time, #{dateFormat})
            ORDER BY bucketTime
            """)
    List<PowerTrendVO> selectPowerTrend(@Param("attrName") String attrName,
                                        @Param("startTime") LocalDateTime startTime,
                                        @Param("endTime") LocalDateTime endTime,
                                        @Param("dateFormat") String dateFormat);
}


