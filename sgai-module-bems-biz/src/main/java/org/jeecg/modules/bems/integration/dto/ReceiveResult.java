package org.jeecg.modules.bems.integration.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReceiveResult {
    private String batchId;
    private int accepted;
    private List<RejectedItem> rejected = new ArrayList<>();

    public ReceiveResult(String batchId) { this.batchId = batchId; }
}
