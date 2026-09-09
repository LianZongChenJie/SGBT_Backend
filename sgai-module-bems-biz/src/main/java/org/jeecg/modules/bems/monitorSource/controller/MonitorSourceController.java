package org.jeecg.modules.bems.monitorSource.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.monitorSource.service.IMonitorSourceService;
import org.jeecg.modules.bems.monitorSource.vo.MonitorSourceCategoryVo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 监控源（设备监控分类 → 设备 两级树）
 */
@Api(tags = "监控源")
@RestController
@RequestMapping("/bems/monitorSource")
@AllArgsConstructor
@Slf4j
public class MonitorSourceController {

    private final IMonitorSourceService monitorSourceService;

    @ApiOperation(value = "监控源树", notes = "设备分类(第一层) -> 设备(第二层) 两级树，不含属性数")
    @GetMapping("/tree")
    public Result<List<MonitorSourceCategoryVo>> tree() {
        return Result.ok(monitorSourceService.buildMonitorSourceTree());
    }
}
