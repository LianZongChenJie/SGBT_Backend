package org.jeecg.modules.bems.integration.dto;

import lombok.Data;

@Data
public class CategoryPushItem {
    private String id;
    private String name;
    private String fullName;   // bems 端忽略，仅接收
    private String pid;        // uuid 或 "0"
    private Integer sort;      // 同级排序，可空
}
