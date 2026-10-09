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
}