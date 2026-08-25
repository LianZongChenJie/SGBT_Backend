package org.jeecg.modules.bems.energyAnalysis.vo;

import lombok.Data;

import java.util.List;

/**
 * 描述:
 *
 * @author ppliu-life
 * created in 2024/4/22 14:33
 */
@Data
public class ChatSeries {
    private Object name;
    private List<Object> data;
    private String unit;

    public ChatSeries() {
    }
    public ChatSeries(Object name, List<Object> data) {
        this.name = name;
        this.data = data;
    }
    public ChatSeries(Object name, List<Object> data, String unit) {
        this.name = name;
        this.data = data;
        this.unit = unit;
    }

}
