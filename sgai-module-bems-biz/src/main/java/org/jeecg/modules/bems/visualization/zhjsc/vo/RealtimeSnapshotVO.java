package org.jeecg.modules.bems.visualization.zhjsc.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 能源站实时数据快照
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("能源站实时数据快照")
public class RealtimeSnapshotVO {

    @ApiModelProperty("是否为降级数据：true 表示本次未成功刷新，返回的是上一次采集到的结果")
    private Boolean stale;

    @ApiModelProperty("本批数据的采集时刻 yyyy-MM-dd HH:mm:ss；无数据时为 null")
    private String dataTime;

    @ApiModelProperty("本批数据的采集时刻（毫秒时间戳）；无数据时为 null")
    private Long dataTimestamp;

    @ApiModelProperty("点位列表")
    private List<RealtimePointVO> points;
}
