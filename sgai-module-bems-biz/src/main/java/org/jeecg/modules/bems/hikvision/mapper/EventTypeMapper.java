package org.jeecg.modules.bems.hikvision.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.bems.hikvision.entity.EventType;

/**
 * 海康事件类型字典表 Mapper
 *
 * @author bems
 */
@Mapper
public interface EventTypeMapper extends BaseMapper<EventType> {

}
