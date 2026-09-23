package org.jeecg.modules.bems.visualization.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PowerTrendVO {
    private String bucketTime;
    private BigDecimal totalValue;
}