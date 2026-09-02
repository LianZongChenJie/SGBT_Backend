package org.jeecg.modules.bems.patterned.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("quality_stamp")
public class QualityStamp{

    /**
     * ID
     */
    @TableField("`ID`")
    private String ID;

    /**
     * 名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 描述
     */
    @TableField("`desc`")
    private String desc;
}
