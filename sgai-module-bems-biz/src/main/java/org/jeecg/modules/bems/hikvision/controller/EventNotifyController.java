package org.jeecg.modules.bems.hikvision.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.bems.hikvision.dto.EventNotifyPushRequest;
import org.jeecg.modules.bems.hikvision.entity.EventNotify;
import org.jeecg.modules.bems.hikvision.service.IEventNotifyService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 事件通知查询接口
 *
 * @author bems
 */
@Slf4j
@Api(tags = "事件通知")
@RestController
@RequestMapping("/bems/hikvision/eventNotify")
@AllArgsConstructor
public class EventNotifyController {


    private final IEventNotifyService eventNotifyService;

    /**
     * 分页查询事件通知记录
     * <p>支持按事件类型、状态、等级、事件源、时间范围等筛选，为空查全部</p>
     */
    @GetMapping("/list")
    @ApiOperation(value = "分页查询事件通知", notes = "支持多条件筛选，从 table_event_notify 分页查询")
    public Result<IPage<EventNotify>> getEventNotifyList(
            @ApiParam(value = "页码，从1开始", defaultValue = "1") @RequestParam(defaultValue = "1") int pageNo,
            @ApiParam(value = "每页条数", defaultValue = "10") @RequestParam(defaultValue = "10") int pageSize,
            @ApiParam(value = "事件类别（如：视频事件）") @RequestParam(required = false) String ability,
            @ApiParam(value = "事件类型，数值编码") @RequestParam(required = false) Integer eventType,
            @ApiParam(value = "事件状态：0-瞬时 1-开始 2-停止 4-联动结果更新 5-图片异步上传") @RequestParam(required = false) Integer status,
            @ApiParam(value = "事件等级：0-未配置 1-低 2-中 3-高") @RequestParam(required = false) Integer eventLvl,
            @ApiParam(value = "事件源编号，精确匹配") @RequestParam(required = false) String srcIndex,
            @ApiParam(value = "事件源名称，模糊匹配") @RequestParam(required = false) String srcName,
            @ApiParam(value = "事件源类型") @RequestParam(required = false) String srcType,
            @ApiParam(value = "事件发生开始时间") @RequestParam(required = false) String happenTimeStart,
            @ApiParam(value = "事件发生结束时间") @RequestParam(required = false) String happenTimeEnd) {
        return Result.OK(eventNotifyService.getEventNotifyList(
                pageNo, pageSize, ability, eventType, status, eventLvl,
                srcIndex, srcName, srcType, happenTimeStart, happenTimeEnd));
    }

    /**
     * 查询事件订阅情况
     * <p>请求海康OpenAPI查询当前事件订阅详情，返回响应中的 data 部分
     * （含订阅的事件类型 eventTypes 及事件接收地址 eventDest）。</p>
     */
    @GetMapping("/viewSubscription")
    @ApiOperation(value = "查询事件订阅情况", notes = "请求海康SDK /api/eventService/v1/eventSubscriptionView，返回订阅事件类型及接收地址列表")
    public Result<JSONObject> viewSubscription() {
        try {
            JSONObject data = eventNotifyService.viewSubscription();
            return Result.ok(data);
        } catch (Exception e) {
            log.error("查询事件订阅情况失败", e);
            return Result.error("查询事件订阅情况失败: " + e.getMessage());
        }
    }

    /**
     * 按事件类型订阅事件
     * <p>前端需传事件类型数组及事件接收地址，
     * 如 {"eventTypes": [123, 223], "eventDest": "https://ip:port/eventRcv"}，
     * 订阅类型使用默认值0。</p>
     */
    @PostMapping("/subscribe")
    @ApiOperation(value = "按事件类型订阅事件", notes = "请求海康SDK /api/eventService/v1/eventSubscriptionByEventTypes，按事件类型订阅事件推送")
    public Result<JSONObject> subscribeByEventTypes(@RequestBody @ApiParam(value = "订阅请求，如 {\"eventTypes\": [123, 223], \"eventDest\": \"https://ip:port/eventRcv\"}") JSONObject body) {
        try {
            JSONArray eventTypes = body.getJSONArray("eventTypes");
            if (eventTypes == null || eventTypes.isEmpty()) {
                return Result.error("事件类型列表不能为空");
            }
            String eventDest = body.getString("eventDest");
            if (StringUtils.isBlank(eventDest)) {
                return Result.error("事件接收地址eventDest不能为空");
            }
            List<Integer> types = eventTypes.toJavaList(Integer.class);
            JSONObject resp = eventNotifyService.subscribeByEventTypes(types, eventDest);
            return Result.ok(resp);
        } catch (Exception e) {
            log.error("按事件类型订阅事件失败", e);
            return Result.error("按事件类型订阅事件失败: " + e.getMessage());
        }
    }

    /**
     * 按事件类型取消订阅
     * <p>前端仅需传事件类型数组，如 {"eventTypes": [123, 223]}，
     * 请求海康SDK取消对应类型的事件推送。</p>
     */
    @PostMapping("/unsubscribe")
    @ApiOperation(value = "按事件类型取消订阅", notes = "请求海康SDK /api/eventService/v1/eventUnSubscriptionByEventTypes，按事件类型取消订阅事件推送")
    public Result<JSONObject> unsubscribeByEventTypes(@RequestBody @ApiParam(value = "取消订阅请求，如 {\"eventTypes\": [123, 223]}") JSONObject body) {
        try {
            JSONArray eventTypes = body.getJSONArray("eventTypes");
            if (eventTypes == null || eventTypes.isEmpty()) {
                return Result.error("事件类型列表不能为空");
            }
            List<Integer> types = eventTypes.toJavaList(Integer.class);
            JSONObject resp = eventNotifyService.unsubscribeByEventTypes(types);
            return Result.ok(resp);
        } catch (Exception e) {
            log.error("按事件类型取消订阅失败", e);
            return Result.error("按事件类型取消订阅失败: " + e.getMessage());
        }
    }

    /**
     * 接收海康事件推送
     * <p>海康平台通过HTTP POST方式将订阅的事件推送到此接口（事件接收地址 eventDest 即指向该接口）。
     * 按海康要求，收到消息后须立即返回 HTTP 200 OK，否则接收太慢会导致事件积压，
     * 因此处理失败仅记录日志，不向海康返回错误。</p>
     */
    @PostMapping("/receive")
    @ApiOperation(value = "接收海康事件推送", notes = "海康平台事件订阅回调接口，接收并落库保存推送的事件")
    public Result<Object> receive(@RequestBody @ApiParam(value = "海康事件推送报文") EventNotifyPushRequest pushRequest) {
        log.info("收到海康事件推送请求");
        try {
            int count = eventNotifyService.handleEventNotify(pushRequest);
            return Result.ok(count);
        } catch (Exception e) {
            // 海康要求立即返回200，处理失败也不返回错误，避免事件积压
            log.error("处理海康事件推送异常", e);
            return Result.ok();
        }
    }

}
