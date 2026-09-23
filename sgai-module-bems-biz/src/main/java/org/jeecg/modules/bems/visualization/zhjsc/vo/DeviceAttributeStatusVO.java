package org.jeecg.modules.bems.visualization.zhjsc.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备属性状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("设备属性状态")
public class DeviceAttributeStatusVO {

    @ApiModelProperty("属性编码")
    @JsonProperty("attribute_code")
    private String attributeCode;

    @ApiModelProperty("属性名称")
    @JsonProperty("attribute_name")
    private String attributeName;

    @ApiModelProperty("属性值")
    private String value;
}
