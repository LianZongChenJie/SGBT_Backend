package org.jeecg.modules.bems.mdm.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.bems.mdm.service.IBuildingControlImportService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Api(tags = "楼控设备导入")
@RestController
@RequestMapping("/bems/buildingControlImport")
@AllArgsConstructor
@Slf4j
public class BuildingControlImportController {

    private final IBuildingControlImportService buildingControlImportService;

    @ApiOperation(value = "导入楼控设备")
    @AutoLog(value = "楼控设备导入")
    @RequiresPermissions("bems:buildingControl:import")
    @PostMapping("/import")
    public Result<Map<String, Integer>> importBuildingControl(
            @RequestParam("file") MultipartFile file,
            @RequestParam("spaceId") Long spaceId) {
        Map<String, Integer> stats = buildingControlImportService.importBuildingControl(file, spaceId);
        return Result.OK("导入成功", stats);
    }
}
