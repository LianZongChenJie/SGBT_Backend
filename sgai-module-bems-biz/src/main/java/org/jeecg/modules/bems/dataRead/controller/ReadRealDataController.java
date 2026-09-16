package org.jeecg.modules.bems.dataRead.controller;

import com.sunwayland.pspace.entity.PsDataWithTagId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.dataRead.service.IPspaceWork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 采集设备数据
 */
@RestController
@RequestMapping("/read/device")
@Slf4j
@Api(tags = "采集设备数据")
public class ReadRealDataController {
    @Autowired
    private IPspaceWork spaceWork;
    @Value("${fdlToken:HAeHAiOHE3ODk2NHk3MTgsInVzZXHuYW1AIHoiYmVtczAyIn}")
    private String fdlToken;

    /**
     * 读取冷源设备属性实时值
     * 获取 cold_source_device_attribute 表中所有 tagid 非空的属性（按 sort_order, id 排序），
     * 批量调用 readLatestValue 返回最新值；
     * 返回: 属性信息 + 实时值(value/实时dataType)，value 为 null 表示该测点读取失败或无数据
     */
    @GetMapping("/realRead")
    @ApiOperation(value = "读取属性实时值", notes = "批量读取最新值")
    public Result<List<PsDataWithTagId>> readDeviceAttributeValue(
            @RequestParam("tagIds") List<Long> tagIds,
            @RequestHeader(value = "Token", required = false) String accessToken) {

        // 校验 token
        if (!fdlToken.equals(accessToken)) {
            return Result.error("Token无效");
        }
        List<PsDataWithTagId> latestList = spaceWork.realReadList(tagIds);
        return Result.ok(latestList);
    }
}

