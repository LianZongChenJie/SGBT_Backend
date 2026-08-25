package org.jeecg.modules.bems.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("integration_push_log")
public class IntegrationPushLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String op;
    private String type;
    private Integer dataCount;
    private String dataIds;
    private String status;
    private Integer httpStatus;
    private String responseMsg;
    private Date createTime;
}
