package org.jeecg.modules.bems.integration.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.bems.integration.dto.IntegrationPayload;
import org.jeecg.modules.bems.integration.dto.ReceiveResult;
import org.jeecg.modules.bems.integration.service.IntegrationReceiveService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "主数据对接接收")
@RestController
@RequestMapping("/bems/integration/receive")
@AllArgsConstructor
public class IntegrationController {

    private IntegrationReceiveService receiveService;

    @ApiOperation("接收仪表（类别/空间/设备，type=1）")
    @IgnoreAuth
    @PostMapping("/meter")
    public Result<ReceiveResult> receiveMeter(@RequestBody IntegrationPayload<Object> payload) {
        return buildResult(receiveService.receive(payload, "1"));
    }

    @ApiOperation("接收设备（类别/空间/设备，type=2）")
    @IgnoreAuth
    @PostMapping("/equipment")
    public Result<ReceiveResult> receiveEquipment(@RequestBody IntegrationPayload<Object> payload) {
        return buildResult(receiveService.receive(payload, "2"));
    }

    /**
     * 按 accepted 判定 success 语义：全部条目被拒时 success=false，便于调用方察觉失败。
     * 无论成功失败都返回完整 result（batchId/accepted/rejected）便于排查。
     */
    private Result<ReceiveResult> buildResult(ReceiveResult r) {
        if (r.getAccepted() == 0) {
            Result<ReceiveResult> res = new Result<>();
            res.setSuccess(false);
            res.setCode(500);
            res.setMessage("所有条目处理失败");
            res.setResult(r);
            return res;
        }
        return Result.OK("操作成功", r);
    }
}
