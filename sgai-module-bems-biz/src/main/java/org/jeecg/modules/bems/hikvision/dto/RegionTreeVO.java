package org.jeecg.modules.bems.hikvision.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 区域树节点VO，供前端渲染树形结构
 *
 * @author bems
 */
@Data
public class RegionTreeVO {

    @ApiModelProperty(value = "区域编号")
    private String indexCode;

    @ApiModelProperty(value = "区域名称")
    private String name;

    @ApiModelProperty(value = "区域完整目录")
    private String regionPath;

    @ApiModelProperty(value = "父区域唯一标识码")
    private String parentIndexCode;

    @ApiModelProperty(value = "是否有权限操作")
    private Integer available;

    @ApiModelProperty(value = "是否叶子节点")
    private Integer leaf;

    @ApiModelProperty(value = "级联平台标识")
    private String cascadeCode;

    @ApiModelProperty(value = "区域标识：0-本级，1-级联，2-混合")
    private Integer cascadeType;

    @ApiModelProperty(value = "区域类型")
    private Integer catalogType;

    @ApiModelProperty(value = "外码")
    private String externalIndexCode;

    @ApiModelProperty(value = "同级区域顺序")
    private Integer sort;

    @ApiModelProperty(value = "本区域资源数量")
    private Integer localQuantity;

    @ApiModelProperty(value = "本区域及下级区域资源数量")
    private Integer totalQuantity;

    @ApiModelProperty(value = "子节点列表")
    private List<RegionTreeVO> children = new ArrayList<>();
}
