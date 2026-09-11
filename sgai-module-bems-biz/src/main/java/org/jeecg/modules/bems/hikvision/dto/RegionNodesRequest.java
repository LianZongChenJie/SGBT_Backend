package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 海康区域查询请求参数（/api/irds/v2/region/nodesByParams）
 *
 * @author bems
 */
@Data
@Accessors(chain = true)
public class RegionNodesRequest {

    /** 资源类型，传region时查询用户有配置权限的区域树 */
    private String resourceType;

    /** 父编号集合，个数<=1000个，单个长度<=64Byte */
    private List<String> parentIndexCodes;

    /** 是否包含下级区域，true时搜索所有子、孙区域，false时只搜索直接子区域 */
    private Boolean isSubRegion;

    /** 当前页码，pageNo≥1 */
    private Integer pageNo;

    /** 分页大小，0<pageSize≤1000 */
    private Integer pageSize;

    /** 权限码集合，个数<=20个，单个长度<=40Byte */
    private List<String> authCodes;

    /** 区域类型，10-普通区域，11-级联区域，12-楼栋单元 */
    private Integer regionType;

    /** 区域名称，根据区域名称模糊查询，若包含中文最大长度40 */
    private String regionName;

    /** 本级区域向上查询，个数<=10个，单个长度<=64Byte */
    private List<String> sonOrgIndexCodes;

    /** 级联标识，0-全部，1-本级，2-级联，默认0 */
    private Integer cascadeFlag;

    /** 排序字段，必须是查询条件 */
    private String orderBy;

    /** 降序升序，desc-降序，asc-升序 */
    private String orderType;

    /** 查询表达式 */
    private List<Expression> expressions;

    /**
     * 查询表达式
     */
    @Data
    @Accessors(chain = true)
    public static class Expression {

        /** 资源属性名，如updateTime */
        private String key;

        /** 操作运算符：0-=，1->=，2-<=，3-in，4-not in，5-between，6-like，7-pre like，8-suffix like */
        private Integer operator;

        /** 资源属性值 */
        private List<String> values;
    }
}
