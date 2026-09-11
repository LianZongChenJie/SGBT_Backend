package org.jeecg.modules.bems.hikvision.dto;

import lombok.Data;

/**
 * 今日进场人数响应VO
 *
 * @author bems
 */
@Data
public class TodayEntryCountVO {

    /** 今日进场总人数 */
    private Integer entryCount;

    public static TodayEntryCountVO of(Integer count) {
        TodayEntryCountVO vo = new TodayEntryCountVO();
        vo.setEntryCount(count);
        return vo;
    }
}
