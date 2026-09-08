package org.jeecg.modules.bems.mdm.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.mdm.entity.MonitorPoint;
import org.jeecg.modules.bems.mdm.entity.MonitorPointHistory;
import org.jeecg.modules.bems.mdm.service.IMonitorPointCollectService;
import org.jeecg.modules.bems.mdm.service.IMonitorPointHistoryService;
import org.jeecg.modules.bems.mdm.service.IMonitorPointService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 监测点实时值 / 历史查询
 * <p>
 * 定时采集任务把第三方实时数据落到 monitor_point（实时值）与
 * monitor_point_history（15 分钟历史），前端通过本接口查库即可。
 */
@Api(tags = "监测点数据")
@RestController
@RequestMapping("/bems/monitorPoint")
@AllArgsConstructor
@Slf4j
public class MonitorPointController {

    private final IMonitorPointService monitorPointService;
    private final IMonitorPointHistoryService monitorPointHistoryService;
    private final IMonitorPointCollectService monitorPointCollectService;

    /**
     * 手动触发一次监测点采集（与定时任务 MonitorPointCollectJob 同一逻辑）
     * <p>
     * 采集涉及全量点(约 5000+)拉取第三方接口，故放入后台线程异步执行、接口立即返回；
     * 进度可通过日志（MonitorPointCollectServiceImpl）观察。
     */
    @ApiOperation(value = "手动触发采集", notes = "立即执行一次第三方实时数据采集并落库")
    @PostMapping("/collectNow")
    public Result<String> collectNow() {
        new Thread(() -> {
            try {
                monitorPointCollectService.collectOnce();
            } catch (Exception e) {
                log.error("手动触发监测点采集异常", e);
            }
        }, "monitor-point-collect-manual").start();
        return Result.ok("采集已触发，请稍后查询实时/历史数据或查看日志");
    }

    @ApiOperation(value = "实时值分页查询", notes = "按类别/在线/关键字查询监测点最新采集值")
    @GetMapping("/queryPage")
    public Result<IPage<MonitorPoint>> queryPage(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "online", required = false) Integer online,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize) {
        return Result.ok(monitorPointService.pagePoints(category, online, keyword, pageNo, pageSize));
    }

    @ApiOperation(value = "设备类别列表", notes = "监测点去重类别")
    @GetMapping("/listCategory")
    public Result<List<String>> listCategory() {
        return Result.ok(monitorPointService.listCategories());
    }

    @ApiOperation(value = "历史分页查询", notes = "按 pid 或 类别+时间 查询 15 分钟历史值")
    @GetMapping("/historyList")
    public Result<IPage<MonitorPointHistory>> historyList(
            @RequestParam(name = "pid", required = false) Long pid,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime,
            @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") long pageSize) {
        return Result.ok(monitorPointHistoryService.pageHistory(pid, category, startTime, endTime, pageNo, pageSize));
    }
}
