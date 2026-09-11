package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;

import java.util.List;

/**
 * 海康区域查询响应（/api/irds/v2/region/nodesByParams）
 *
 * @author bems
 */
@Data
public class RegionNodesResponse {

    /** 查询数据记录总数 */
    private Integer total;

    /** 当前页码范围 (0, ~) */
    private Integer pageNo;

    /** 每页记录总数 范围 (0, 1000] */
    private Integer pageSize;

    /** 区域列表 */
    private List<RegionItem> list;

    /**
     * 海康返回的区域信息
     */
    @Data
    public static class RegionItem {

        /** 区域编号 */
        private String indexCode;

        /** 区域名称 */
        private String name;

        /** 区域完整目录，含本节点，/进行分割，上级节点在前 */
        private String regionPath;

        /** 父区域唯一标识码 */
        private String parentIndexCode;

        /** 是否有权限操作：true-有权限，false-无权限 */
        private Boolean available;

        /** 是否叶子节点：true-是叶子节点，false-不是叶子节点 */
        private Boolean leaf;

        /** 级联平台标识，多个级联编号以@分隔，本级区域默认值"0" */
        private String cascadeCode;

        /** 区域标识：0-本级，1-级联，2-混合 */
        private Integer cascadeType;

        /** 区域类型：0-国标区域，1-雪亮工程区域，2-司法行政区域，9-自定义区域，10-历史兼容普通区域，11-历史兼容级联区域，12-楼栋单元 */
        private Integer catalogType;

        /** 外码（如：国际码） */
        private String externalIndexCode;

        /** 父外码（如：国际码） */
        private String parentExternalIndexCode;

        /** 同级区域顺序 */
        private Integer sort;

        /** 本区域资源数量（只统计本级挂的资源数量） */
        private Integer localQuantity;

        /** 本区域及下级区域资源数量（包含本级及下级） */
        private Integer totalQuantity;

        /** 创建时间（ISO8601格式） */
        private String createTime;

        /** 更新时间（ISO8601格式） */
        private String updateTime;
    }
}
