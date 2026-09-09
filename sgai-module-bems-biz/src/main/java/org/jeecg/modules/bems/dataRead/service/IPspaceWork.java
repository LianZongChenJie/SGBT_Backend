package org.jeecg.modules.bems.dataRead.service;


import com.sunwayland.pspace.entity.PsDataWithTagId;

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
}
