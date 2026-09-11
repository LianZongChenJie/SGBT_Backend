package org.jeecg.modules.bems.hikvision.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.bems.hikvision.entity.EventNotify;
import org.jeecg.modules.bems.hikvision.entity.EventType;
import org.jeecg.modules.bems.hikvision.dto.EventNotifyPushRequest;
import org.jeecg.modules.bems.hikvision.dto.EventNotifyPushRequest.EventNotifyEvent;
import org.jeecg.modules.bems.hikvision.dto.EventNotifyPushRequest.EventNotifyParams;
import org.jeecg.modules.bems.hikvision.service.IEventNotifyService;
import org.jeecg.modules.bems.hikvision.mapper.EventNotifyMapper;
import org.jeecg.modules.bems.hikvision.mapper.EventTypeMapper;
import org.jeecg.modules.bems.hikvision.util.HikvisionUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 海康事件通知服务实现
 * <p>解析海康推送事件JSON，转换成EventNotify实体入库；支持分页查询。</p>
 *
 * @author bems
 */
@Slf4j
@Service
public class EventNotifyServiceImpl extends ServiceImpl<EventNotifyMapper, EventNotify>
        implements IEventNotifyService {

    private final EventTypeMapper eventTypeMapper;

    private final HikvisionUtil hikvisionUtil;

    public EventNotifyServiceImpl(EventTypeMapper eventTypeMapper, HikvisionUtil hikvisionUtil) {
        this.eventTypeMapper = eventTypeMapper;
        this.hikvisionUtil = hikvisionUtil;
    }

    @Override
    public int handleEventNotify(EventNotifyPushRequest pushRequest) {
        if (pushRequest == null) {
            log.warn("接收到空推送事件，跳过处理");
            return 0;
        }

        EventNotifyParams params = pushRequest.getParams();
        if (params == null || params.getEvents() == null || params.getEvents().isEmpty()) {
            log.warn("推送事件中events为空, ability={}, sendTime={}",
                    params != null ? params.getAbility() : null,
                    params != null ? params.getSendTime() : null);
            return 0;
        }

        String ability = params.getAbility();
        String sendTime = params.getSendTime();
        List<EventNotifyEvent> events = params.getEvents();

        log.info("接收到海康事件推送, ability={}, sendTime={}, 事件数量={}", ability, sendTime, events.size());

        List<EventNotify> entityList = new ArrayList<>();
        int skipCount = 0;

        for (EventNotifyEvent event : events) {
            try {
                EventNotify entity = convertToEntity(event, ability, sendTime);
                entityList.add(entity);
            } catch (Exception e) {
                log.error("转换事件失败, eventId={}, eventType={}", event.getEventId(), event.getEventType(), e);
                skipCount++;
            }
        }

        if (!entityList.isEmpty()) {
            // 达梦驱动对JDBC批量(executeBatch)支持有缺陷，会报index out of range/TypeException，改为循环单条插入绕开该问题
            for (EventNotify entity : entityList) {
                baseMapper.insert(entity);
            }
            log.info("海康事件保存完成, 成功{}条, 跳过{}条", entityList.size(), skipCount);
        }

        if (skipCount > 0) {
            log.warn("海康事件推送有{}条转换失败", skipCount);
        }

        return entityList.size();
    }

    /**
     * 将推送事件转换为数据库实体
     */
    private EventNotify convertToEntity(EventNotifyEvent event, String ability, String sendTime) {
        EventNotify entity = new EventNotify()
                .setSendTime(sendTime)
                .setAbility(ability)
                .setEventId(event.getEventId())
                .setEventType(event.getEventType())
                .setHappenTime(event.getHappenTime())
                .setSrcIndex(event.getSrcIndex())
                .setSrcName(event.getSrcName())
                .setSrcType(event.getSrcType())
                .setStatus(event.getStatus() != null ? event.getStatus() : 0)
                .setEventLvl(event.getEventLvl() != null ? event.getEventLvl() : 0)
                .setTimeout(event.getTimeout() != null ? event.getTimeout() : 0);

        // 提取父设备编码（优先使用事件自带的，其次从data中提取）
        String parentIndex = event.getSrcParentIndex();
        if (parentIndex == null || parentIndex.isEmpty()) {
            parentIndex = extractParentIndex(event.getData());
        }
        entity.setSrcParentIndex(parentIndex);

        // 将data对象序列化为JSON字符串存入event_data字段
        JSONObject data = event.getData();
        if (data != null && !data.isEmpty()) {
            entity.setEventData(data.toJSONString());
        }

        return entity;
    }

    @Override
    public IPage<EventNotify> getEventNotifyList(int pageNo, int pageSize,
                                                   String ability, Integer eventType,
                                                   Integer status, Integer eventLvl,
                                                   String srcIndex, String srcName, String srcType,
                                                   String happenTimeStart, String happenTimeEnd) {
        log.info("分页查询事件通知记录, pageNo={}, pageSize={}, ability={}, eventType={}, status={}, eventLvl={}, srcIndex={}, srcName={}, srcType={}, happenTimeStart={}, happenTimeEnd={}",
                pageNo, pageSize, ability, eventType, status, eventLvl, srcIndex, srcName, srcType, happenTimeStart, happenTimeEnd);

        LambdaQueryWrapper<EventNotify> wrapper = new LambdaQueryWrapper<EventNotify>()
                .eq(StringUtils.isNotBlank(ability), EventNotify::getAbility, ability)
                .eq(eventType != null, EventNotify::getEventType, eventType)
                .eq(status != null, EventNotify::getStatus, status)
                .eq(eventLvl != null, EventNotify::getEventLvl, eventLvl)
                .eq(StringUtils.isNotBlank(srcIndex), EventNotify::getSrcIndex, srcIndex)
                .like(StringUtils.isNotBlank(srcName), EventNotify::getSrcName, srcName)
                .eq(StringUtils.isNotBlank(srcType), EventNotify::getSrcType, srcType)
                .ge(StringUtils.isNotBlank(happenTimeStart), EventNotify::getHappenTime, happenTimeStart)
                .le(StringUtils.isNotBlank(happenTimeEnd), EventNotify::getHappenTime, happenTimeEnd)
                .orderByDesc(EventNotify::getHappenTime);

        IPage<EventNotify> resultPage = page(new Page<>(pageNo, pageSize), wrapper);

        // 联动table_event_type，填充eventTypeName
        List<EventNotify> records = resultPage.getRecords();
        if (!records.isEmpty()) {
            List<Integer> eventCodes = records.stream()
                    .map(EventNotify::getEventType)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            if (!eventCodes.isEmpty()) {
                List<EventType> types = eventTypeMapper.selectList(
                        new QueryWrapper<EventType>().in("event_code", eventCodes));
                Map<Integer, String> codeNameMap = types.stream()
                        .collect(Collectors.toMap(EventType::getEventCode, EventType::getEventType));
                records.forEach(r -> {
                    if (r.getEventType() != null) {
                        r.setEventTypeName(codeNameMap.get(r.getEventType()));
                    }
                });
            }
        }

        log.info("分页查询事件通知记录完成, 共{}条, 当前页{}条", resultPage.getTotal(), resultPage.getRecords().size());
        return resultPage;
    }

    @Override
    public JSONObject viewSubscription() throws Exception {
        log.info("查询海康事件订阅情况");
        String responseBody = hikvisionUtil.doPostJson("/api/eventService/v1/eventSubscriptionView", "{}");
        if (!hikvisionUtil.isSuccess(responseBody)) {
            JSONObject resp = JSON.parseObject(responseBody);
            String code = resp.getString("code");
            String msg = resp.getString("msg");
            throw new RuntimeException("查询事件订阅失败, code=" + code + ", msg=" + msg);
        }
        JSONObject data = hikvisionUtil.getResponseData(responseBody);
        log.info("查询海康事件订阅情况成功: {}", data);
        return data;
    }

    @Override
    public JSONObject subscribeByEventTypes(List<Integer> eventTypes, String eventDest) throws Exception {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("事件类型列表不能为空");
        }
        if (StringUtils.isBlank(eventDest)) {
            throw new IllegalArgumentException("事件接收地址eventDest不能为空");
        }
        JSONObject requestBody = new JSONObject();
        requestBody.put("eventTypes", eventTypes);
        requestBody.put("eventDest", eventDest);
        log.info("按事件类型订阅事件, eventTypes={}, eventDest={}", eventTypes, eventDest);

        String responseBody = hikvisionUtil.doPostJson("/api/eventService/v1/eventSubscriptionByEventTypes", requestBody.toJSONString());
        JSONObject resp = JSON.parseObject(responseBody);
        if (!hikvisionUtil.isSuccess(responseBody)) {
            String code = resp.getString("code");
            String msg = resp.getString("msg");
            throw new RuntimeException("订阅事件失败, code=" + code + ", msg=" + msg);
        }
        log.info("按事件类型订阅事件成功, code={}, msg={}", resp.getString("code"), resp.getString("msg"));
        return resp;
    }

    @Override
    public JSONObject unsubscribeByEventTypes(List<Integer> eventTypes) throws Exception {
        if (eventTypes == null || eventTypes.isEmpty()) {
            throw new IllegalArgumentException("事件类型列表不能为空");
        }
        JSONObject requestBody = new JSONObject();
        requestBody.put("eventTypes", eventTypes);
        log.info("按事件类型取消订阅, eventTypes={}", eventTypes);

        String responseBody = hikvisionUtil.doPostJson("/api/eventService/v1/eventUnSubscriptionByEventTypes", requestBody.toJSONString());
        JSONObject resp = JSON.parseObject(responseBody);
        if (!hikvisionUtil.isSuccess(responseBody)) {
            String code = resp.getString("code");
            String msg = resp.getString("msg");
            throw new RuntimeException("取消订阅事件失败, code=" + code + ", msg=" + msg);
        }
        log.info("按事件类型取消订阅成功, code={}, msg={}", resp.getString("code"), resp.getString("msg"));
        return resp;
    }

    /**
     * 从事件data中尝试提取父设备编码
     * <p>不同事件类型的data结构不同，优先从常见路径提取：</p>
     * <ul>
     *   <li>data.fielddetection[0].targetAttrs.deviceIndexCode</li>
     *   <li>data.targetAttrs.deviceIndexCode</li>
     * </ul>
     */
    private String extractParentIndex(JSONObject data) {
        if (data == null) {
            return null;
        }

        try {
            // 尝试从行为分析事件的fielddetection中提取
            String eventTypeName = data.getString("eventType");
            if (eventTypeName != null && !eventTypeName.isEmpty()) {
                // 尝试通过事件类型名找到对应的数组字段
                Object eventArray = data.get(eventTypeName);
                if (eventArray instanceof List) {
                    List<?> list = (List<?>) eventArray;
                    if (!list.isEmpty() && list.get(0) instanceof JSONObject) {
                        JSONObject firstItem = (JSONObject) list.get(0);
                        JSONObject targetAttrs = firstItem.getJSONObject("targetAttrs");
                        if (targetAttrs != null) {
                            String deviceIndexCode = targetAttrs.getString("deviceIndexCode");
                            if (deviceIndexCode != null && !deviceIndexCode.isEmpty()) {
                                return deviceIndexCode;
                            }
                        }
                    }
                }
            }

            // 兼容：直接从data.targetAttrs提取
            JSONObject targetAttrs = data.getJSONObject("targetAttrs");
            if (targetAttrs != null) {
                String parentIndex = targetAttrs.getString("deviceIndexCode");
                if (parentIndex != null && !parentIndex.isEmpty()) {
                    return parentIndex;
                }
            }
        } catch (Exception e) {
            log.debug("提取父设备编码失败, dataType={}", data.getString("dataType"));
        }

        return null;
    }
}
