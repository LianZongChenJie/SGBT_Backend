package org.jeecg.modules.bems.integration.dto;

import lombok.Data;
import java.util.List;

@Data
public class IntegrationPayload<T> {
    private String source;
    private String systemCode;
    private String type;       // DEVICE / CATEGORY / SPACE
    private String op;         // UPSERT / DELETE / SNAPSHOT
    private String batchId;
    private List<T> data;
}
