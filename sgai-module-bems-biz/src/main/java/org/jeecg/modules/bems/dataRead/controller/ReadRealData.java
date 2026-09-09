package org.jeecg.modules.bems.dataRead.controller;

import com.sunwayland.pspace.entity.PsDataWithTagId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.dataRead.service.IPspaceWork;
import org.jeecg.modules.bems.dataRead.service.impl.SpaceWorkImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采集设备数据
 */
@RestController
@RequestMapping("/read/device")
@AllArgsConstructor
@Slf4j
@Api(tags = "采集设备数据")
public class ReadRealData {
    private final IPspaceWork spaceWork;
    /**
     * 读取冷源设备属性实时值
     * 获取 cold_source_device_attribute 表中所有 tagid 非空的属性（按 sort_order, id 排序），
     * 批量调用 readLatestValue 返回最新值；
     * 返回: 属性信息 + 实时值(value/实时dataType)，value 为 null 表示该测点读取失败或无数据
     */
    @GetMapping("/realRead")
    @ApiOperation(value = "读取属性实时值", notes = "批量读取最新值")
    public Result<List<PsDataWithTagId>> readDeviceAttributeValue(@RequestParam("tagIds") List<Long> tagIds) {
        List<PsDataWithTagId> latestList = spaceWork.realReadList(tagIds);
        return Result.ok(latestList);
    }
}

