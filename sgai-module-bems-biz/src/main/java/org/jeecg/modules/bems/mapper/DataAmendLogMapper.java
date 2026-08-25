package org.jeecg.modules.bems.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.jeecg.modules.bems.entity.DataAmendLog;

import java.util.List;
import java.util.Set;

public interface DataAmendLogMapper extends BaseMapper<DataAmendLog> {

    /**
     * 使用 EXISTS 子查询的分页查询（带数据权限）
     *
     * @param page 分页对象
     * @param deviceId 设备ID（可选）
     * @param deviceName 设备名称（可选）
     * @param deviceCode 设备编码（可选）
     * @param spaceIdList 用户传入的空间ID列表（可选）
     * @param categoryIds 数据权限-专业ID集合
     * @param spaceIds 数据权限-空间ID集合
     * @param amendType 修正类型（可选）
     * @return 分页结果
     */
    IPage<DataAmendLog> selectPageWithPermission(
        Page<DataAmendLog> page,
        @Param("deviceId") Long deviceId,
        @Param("deviceName") String deviceName,
        @Param("deviceCode") String deviceCode,
        @Param("spaceIdList") List<Long> spaceIdList,
        @Param("categoryIds") Set<Long> categoryIds,
        @Param("spaceIds") Set<Long> spaceIds,
        @Param("amendType") String amendType
    );
}
