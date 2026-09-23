package org.jeecg.modules.bems.visualization.scgl.controller;

import io.swagger.annotations.Api;
import org.jeecg.modules.bems.visualization.scgl.service.SystemOperationInformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生产管理数据接口
 * 系统运行信息
 */
@Api(tags = "生产管理数据接口")
@RestController
@RequestMapping("/bems/visualization/scgl")
public class SystemOperationInformationController {
    @Autowired
    private SystemOperationInformationService systemOperationInformationService;
}
