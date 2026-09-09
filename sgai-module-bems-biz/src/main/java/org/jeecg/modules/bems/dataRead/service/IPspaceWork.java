package org.jeecg.modules.bems.dataRead.service;


import com.sunwayland.pspace.entity.PsDataWithTagId;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;

import java.time.LocalDateTime;
import java.util.List;

public interface IPspaceWork {

    /**
     * 获取实时数据
     * @param tagIds 标签id集合
     * @return 实时数据集合
     */
    List<PsDataWithTagId> realReadList(List<Long> tagIds);

    /**
     * 从数据库查询所有采集编码(acquisition_coding)为纯数字的设备属性，
     * 以采集编码作为 tagId 批量读取实时数据，并按返回数据中的 tagId 回写对应行的 value。
     * @return 实时读取结果集合（元素含 tagId/value/timestamp 等），读取失败或无数值返回时为空集合
     */
    int refreshRealValueByNumericAcquisition();

    /**
     * 根据设备属性直接刷新所属设备的运行状态与最后采集时间：
     * 对 attributes 中非空的 deviceId 去重后逐个更新。
     * @param attributes 设备属性列表（取其 deviceId 定位所属设备）
     * @param time 本次采集时间，写入设备 last_gather_time
     * @param online 目标运行状态，取 DeviceConstant.DEVICE_RUN_STATA_ONLINE/OFFLINE
     * @return 更新设备数
     */
    int updateDeviceGatherStatus(List<DeviceAttribute> attributes, LocalDateTime time, String online);
}
