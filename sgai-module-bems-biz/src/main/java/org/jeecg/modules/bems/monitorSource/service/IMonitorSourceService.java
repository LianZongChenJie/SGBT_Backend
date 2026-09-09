package org.jeecg.modules.bems.monitorSource.service;

import org.jeecg.modules.bems.monitorSource.vo.MonitorSourceCategoryVo;

import java.util.List;

/**
 * 监控源 Service：设备分类 -> 设备 两级树
 */
public interface IMonitorSourceService {

    /**
     * 设备分类(第一层) -> 设备(第二层) 两级树，不含属性数
     */
    List<MonitorSourceCategoryVo> buildMonitorSourceTree();
}
