package org.jeecg.modules.bems.monitorSource.vo;

import lombok.Data;

import java.util.List;

/**
 * 监控源树第一层：分类节点（含其下设备）
 */
@Data
public class MonitorSourceCategoryVo {

    private Long categoryId;
    private String categoryName;
    private List<MonitorSourceDeviceVo> children;

    public MonitorSourceCategoryVo() {
    }

    public MonitorSourceCategoryVo(Long categoryId, String categoryName, List<MonitorSourceDeviceVo> children) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.children = children;
    }
}
