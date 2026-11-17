package org.jeecg.modules.bems.visualization.zhjsc.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.visualization.zhjsc.service.EnergyStationRealtimeService;
import org.jeecg.modules.bems.visualization.zhjsc.vo.RealtimeSnapshotVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 能源站实时数据接口
 * <p>
 * 供组态画面/实时监控使用，返回能源站集成点位当前值。数据取自库表，随采集任务每 15 分钟刷新一次，
 * 接口本身是纯查询，调用频率不受限制；结果中的 dataTime 为该批数据的采集时刻，stale 表示数据是否已超期未更新。
 */
@Api(tags = "能源站实时数据接口")
@RestController
@RequestMapping("/visualization/zhjsc")
public class EnergyStationRealtimeController {

    @Value("${fdlTokenSjgl:AVcncn2njikigh78VHGffhjG6fhJVKjg5tDghjvYJKLHyuvt}")
    private String fdlToken;

    @Autowired
    private EnergyStationRealtimeService energyStationRealtimeService;

    /**
     * 能源站实时数据
     * 一次返回整屏所需点位，默认返回白名单全部
     */
    @ApiOperation(value = "能源站实时数据", notes = "按 keys 过滤，不传返回白名单全部点位")
    @GetMapping("/energyStationRealtime")
    public Result<RealtimeSnapshotVO> energyStationRealtime(
            @RequestParam(value = "keys", required = false) String keys,
            @RequestHeader(value = "Token", required = false) String accessToken) {

        if (!fdlToken.equals(accessToken)) {
            return Result.error("Token无效");
        }
        List<String> keyList = StringUtils.isBlank(keys) ? null : Arrays.asList(keys.split(","));
        return Result.ok(energyStationRealtimeService.snapshot(keyList));
    }
}
