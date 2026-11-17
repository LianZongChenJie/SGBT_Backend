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

    /**
     * 历史表最新的采集时间（用于默认查询区间对齐，避免采集中断后查出来全是空图）
     */
    @Select("select max(collection_time) from device_attribute_history")
    LocalDateTime selectLatestCollectionTime();
}