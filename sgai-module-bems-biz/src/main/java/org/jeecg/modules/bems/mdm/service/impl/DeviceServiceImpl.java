package org.jeecg.modules.bems.mdm.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import dm.jdbc.util.StringUtil;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.bems.mdm.constant.DeviceConstant;
import org.jeecg.modules.bems.mdm.dto.DeviceDto;
import org.jeecg.modules.bems.mdm.dto.DeviceRunStateStatisticsDto;
import org.jeecg.modules.bems.mdm.dto.DeviceStatusDto;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.DeviceAttribute;
import org.jeecg.modules.bems.mdm.entity.DeviceModelAttribute;
import org.jeecg.modules.bems.mdm.entity.Space;
import org.jeecg.modules.bems.mdm.mapper.DeviceMapper;
import org.jeecg.modules.bems.mdm.service.IDeviceAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceModelAttributeService;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.jeecg.modules.bems.mdm.service.ISpaceService;
import org.jeecg.modules.bems.integration.service.IntegrationPushService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description: 设备基础信息
 * @Author: jeecg-boot
 * @Date: 2025-02-20
 * @Version: V1.0
 */
@Service
@AllArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements IDeviceService {

    private final IDeviceModelAttributeService deviceModelAttributeService;

    private final IDeviceAttributeService deviceAttributeService;

    private final ISpaceService spaceService;

    /**
     * master 主数据推送服务。用 @Lazy 字段注入避免与推送链路可能的循环依赖
     * （类使用 @AllArgsConstructor，无法对其构造器参数单独加 @Lazy）。
     */
    @Autowired
    @Lazy
    private IntegrationPushService integrationPushService;

    @Override
    public IPage<Device> listPage(DeviceDto params) {
        return page(new Page<>(params.getPageNo(), params.getPageSize()),getQueryWrapper(params));
    }

    @Override
    public List<Device> list(DeviceDto params){
        return super.list(getQueryWrapper(params));
    }

    @Override
    @Transactional
    public void addDevice(Device device) {
        // 校验设备编号是否重复
        if (baseMapper.selectCount(new LambdaUpdateWrapper<Device>().eq(Device::getDeviceCode, device.getDeviceCode())) > 0) {
            throw new JeecgBootException("设备编号重复");
        }
        device.setRunState("离线");
        baseMapper.insert(device);
        // 增加运行状态属性
        deviceAttributeService.addRunStateAttribute(device.getId());
        if(device.getModelId() != null){
            // 获取设备模型下属性
            List<DeviceModelAttribute> deviceModelAttributes = deviceModelAttributeService.listByModelId(device.getModelId());
            List<DeviceAttribute> attributes = deviceModelAttributes.stream().map(DeviceAttribute::convert).peek(item -> item.setDeviceId(device.getId())).collect(Collectors.toList());
            deviceAttributeService.saveBatch(attributes);
        }
        // 新增后事务提交触发 master 推送（UPSERT）
        registerAfterCommitPush(Collections.singletonList(device), "UPSERT");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(Device device) {
        device.setRunState(null);
        device.setModelId(null);
        device.setDeviceType(null);
        // 校验设备编号是否重复
        if (baseMapper.selectCount(new LambdaUpdateWrapper<Device>().eq(Device::getDeviceCode, device.getDeviceCode()).ne(Device::getId, device.getId())) > 0) {
            throw new JeecgBootException("设备编号重复");
        }
        boolean ok = SqlHelper.retBool(baseMapper.updateById(device));
        if (ok) {
            // 更新成功后取 DB 全量快照触发推送（UPSERT）：前端编辑表单不回传
            // masterId/categoryId/spaceId 等字段，若直接用入参 device 推送，下游
            // IntegrationPushServiceImpl.toItem 见 masterId 为空会每次重新生成 uuid
            // 并回写，导致 master 把同一设备当成新设备，产生重复/孤儿记录。
            Device snapshot = this.getById(device.getId());
            if (snapshot != null) {
                registerAfterCommitPush(Collections.singletonList(snapshot), "UPSERT");
            }
        }
        return ok;
    }

    /**
     * 删除前取快照（保留原 categoryId/spaceId/masterId 供 master 过滤），
     * 删除成功且事务提交后触发 master 推送（DELETE）。
     */
    @Override
    public boolean removeById(Serializable id) {
        Device snapshot = this.getById(id);
        boolean ok = super.removeById(id);
        if (ok && snapshot != null) {
            registerAfterCommitPush(Collections.singletonList(snapshot), "DELETE");
        }
        return ok;
    }

    /**
     * 事务提交后异步推送；无事务上下文时直接推送。
     */
    private void registerAfterCommitPush(List<Device> devices, String op) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    integrationPushService.pushDevices(devices, op);
                }
            });
        } else {
            integrationPushService.pushDevices(devices, op);
        }
    }

    @Override
    public void updateAutomaticAlgorithm(Long id, String automaticAlgorithm) {
        // 更新自动算法
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<Device>()
                .eq(Device::getId, id)
                .set(Device::getAutomaticAlgorithm, automaticAlgorithm);
        update(wrapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Device> findCodeAndName() {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>().select(Device::getId, Device::getDeviceCode, Device::getDeviceName);
        return baseMapper.selectList(wrapper);
    }


    @Override
    public IPage<Device> findMeasuring(Device device) {
        LambdaQueryWrapper<Device> wrapper = getQueryWrapper(device)
                .eq(Device::getDeviceType, Device.DEVICE_TYPE_MEASURING);

        IPage<Device> page = new Page<>(device.getPageNo(), device.getPageSize());
        return page(page, wrapper);
    }

    @Override
    public IPage<Device> find(Device device) {
        LambdaQueryWrapper<Device> wrapper = getQueryWrapper(device);
        IPage<Device> page = new Page<>(device.getPageNo(), device.getPageSize());
        return page(page, wrapper);
    }

    @Override
    public List<Device> findByDeviceIds(Collection<Long> deviceIds) {
        if (CollectionUtil.isEmpty(deviceIds)) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<Device>().in(Device::getId, deviceIds));
    }


    @Override
    public List<Device> findMeasurementBySpaceIdAndCategoryId(String deviceName, String deviceCode, List<Long> spaceIds, List<Long> categoryIds) {
        return findBySpaceIdAndCategoryId(deviceName, deviceCode, spaceIds, categoryIds, Device.DEVICE_TYPE_MEASURING);
    }

    @Override
    public Device getByDeviceCode(String deviceCode) {
        return getOne(new LambdaQueryWrapper<Device>().eq(Device::getDeviceCode, deviceCode));
    }

    @Override
    public void updateStatus(DeviceStatusDto data) {
        update(new LambdaUpdateWrapper<Device>()
                .eq(Device::getDeviceCode, data.getEquipmentCode())
                .set(Device::getRunState, data.getDeviceRunStatus())
        );
    }

    @Override
    public void updateStatus(String deviceCode, String runStatus) {
        if(StringUtils.isEmpty(deviceCode) || runStatus == null){
            return;
        }
        update(new LambdaUpdateWrapper<Device>()
                .eq(Device::getDeviceCode,deviceCode)
                .set(Device::getRunState,runStatus));
        // 发送消息更新设备运行状态
        deviceAttributeService.updateAttributeForRunState(deviceCode,runStatus);
    }

    @Override
    public Device getDetail(Long id) {
        Device byId = getById(id);
        if (byId != null) {
            Space space = spaceService.getById(byId.getSpaceId());
            byId.setSpaceName(space != null ? space.getFullName() : null);
            return byId;
        }
        return null;
    }

    @Override
    public List<Device> findByType(String type) {
        if(StringUtil.isEmpty(type)){
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<Device>().eq(Device::getDeviceType, type));
    }

    /**
     * 计量仪表运行状态统计
     * @return 统计结果
     */
    @Override
    public DeviceRunStateStatisticsDto statisticsRunState() {
        List<Device> list = super.list(new LambdaQueryWrapper<Device>()
                        .select(Device::getId, Device::getRunState)
                .eq(Device::getDeviceType, Device.DEVICE_TYPE_MEASURING));
        Map<String, Long> collect = list.stream().filter(item -> item.getRunState() != null).collect(Collectors.groupingBy(Device::getRunState, Collectors.counting()));
        DeviceRunStateStatisticsDto dto = new DeviceRunStateStatisticsDto();
        dto.setCount((long) list.size());
        dto.setOnline(collect.getOrDefault(DeviceConstant.DEVICE_RUN_STATA_ONLINE, 0L));
        dto.setOffline(collect.getOrDefault(DeviceConstant.DEVICE_RUN_STATA_OFFLINE, 0L));
        return dto;
    }

    @Override
    public DeviceRunStateStatisticsDto statisticsRunState(Long categoryId) {
        List<Device> list = super.list(new LambdaQueryWrapper<Device>().select(Device::getId,Device::getRunState).eq(categoryId != null, Device::getCategoryId, categoryId));
        Map<String, Long> collect = list.stream().filter(item -> item.getRunState() != null).collect(Collectors.groupingBy(Device::getRunState, Collectors.counting()));
        DeviceRunStateStatisticsDto dto = new DeviceRunStateStatisticsDto();
        dto.setCount((long) list.size());
        dto.setOnline(collect.getOrDefault(DeviceConstant.DEVICE_RUN_STATA_ONLINE, 0L));
        dto.setOffline(collect.getOrDefault(DeviceConstant.DEVICE_RUN_STATA_OFFLINE, 0L));
        return dto;
    }

    /**
     * 更新设备最后采集时间
     *
     * @param deviceCode 设备编号
     * @param time       采集时间
     */
    @Override
    public void updateLastGatherTime(String deviceCode, LocalDateTime time) {
        if(StrUtil.isEmpty(deviceCode) || time == null){
            return;
        }
        update(new LambdaUpdateWrapper<Device>().eq(Device::getDeviceCode,deviceCode).set(Device::getLastGatherTime,time));
    }

    @Override
    public List<Device> findByCategoryIds(Collection<Long> categoryIds) {
        if(CollectionUtil.isEmpty(categoryIds)){
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<Device>().in(Device::getCategoryId, categoryIds));
    }

    @Override
    public IPage<Device> findDeviceAndAttribute(DeviceDto params) {
        IPage<Device> deviceIPage = this.listPage(params);
        if(CollectionUtil.isEmpty(deviceIPage.getRecords())){
            return deviceIPage;
        }
        List<Device> records = deviceIPage.getRecords();
        List<Long> deviceIds = records.stream().map(Device::getId).toList();
        Map<Long,List<DeviceAttribute>> deviceAttributeMap = deviceAttributeService.findByDeviceIds(deviceIds)
                .stream()
                .sorted(Comparator.comparing(DeviceAttribute::getSort))
                .collect(Collectors.groupingBy(DeviceAttribute::getDeviceId, Collectors.toList()));
        for(Device device : records){
            device.setAttributes(deviceAttributeMap.getOrDefault(device.getId(),Collections.emptyList()));
        }
        return deviceIPage;
    }

    @Override
    public List<Device> findByDeviceCodes(List<String> deviceCodes) {
        return super.list(new LambdaQueryWrapper<Device>().in(Device::getDeviceCode,deviceCodes));
    }

    private List<Device> findBySpaceIdAndCategoryId(String deviceName, String deviceCode, List<Long> spaceIds, List<Long> categoryIds, String deviceType) {
        return list(new LambdaQueryWrapper<Device>()
                .in(CollectionUtil.isNotEmpty(spaceIds),Device::getSpaceId, spaceIds)
                .in(CollectionUtil.isNotEmpty(categoryIds),Device::getCategoryId, categoryIds)
                .eq(StringUtils.isNotEmpty(deviceType), Device::getDeviceType, deviceType)
                .and(StringUtils.isNotEmpty(deviceName) || StringUtils.isNotEmpty(deviceCode),
                        wrapper -> wrapper.like(StringUtils.isNotEmpty(deviceName), Device::getDeviceName, deviceName)
                                .or()
                                .like(StringUtils.isNotEmpty(deviceCode), Device::getDeviceCode, deviceCode)))
                ;
    }

    /**
     * 插入一条记录（选择字段，策略插入）。
     * 新增成功后取 DB 全量快照触发推送（UPSERT），覆盖批量导入等经 save 路径
     * 新增的设备（addDevice 用 baseMapper.insert 独立触发，不走此方法，无双推）。
     *
     * @param entity 实体对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Device entity) {
        boolean ok = super.save(entity);
        if (ok) {
            Device snapshot = this.getById(entity.getId());
            if (snapshot != null) {
                // 导入等 save 路径新增设备后推送（UPSERT）
                registerAfterCommitPush(Collections.singletonList(snapshot), "UPSERT");
            }
        }
        return ok;
    }

    private LambdaQueryWrapper<Device> getQueryWrapper(Device device){
        return new LambdaQueryWrapper<Device>()
                .like(StringUtils.isNotEmpty(device.getDeviceCode()),Device::getDeviceCode, device.getDeviceCode())
                .like(StringUtils.isNotEmpty(device.getDeviceName()),Device::getDeviceName, device.getDeviceName())
                .eq(device.getCategoryId() != null,Device::getCategoryId, device.getCategoryId())
                .eq(device.getSpaceId() != null,Device::getSpaceId, device.getSpaceId())
                .eq(StringUtils.isNotEmpty(device.getRunState()),Device::getRunState, device.getRunState())
                .orderByAsc(Device::getSort);
    }

    private LambdaQueryWrapper<Device> getQueryWrapper(DeviceDto params){
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>()
                .eq(StringUtils.isNotEmpty(params.getDeviceType()),  Device::getDeviceType, params.getDeviceType())
                .like(StringUtils.isNotEmpty(params.getDeviceName()),  Device::getDeviceName, params.getDeviceName())
                .like(StringUtils.isNotEmpty(params.getDeviceCode()),  Device::getDeviceCode, params.getDeviceCode())
                .and(StringUtils.isNotEmpty(params.getNameOrCode()),wp -> wp.like(Device::getDeviceName, params.getNameOrCode()).or().like(Device::getDeviceCode, params.getNameOrCode()))
                .eq(params.getCategoryId() != null,  Device::getCategoryId, params.getCategoryId())
                .eq(params.getSpaceId() != null,  Device::getSpaceId, params.getSpaceId())
                .eq(StringUtils.isNotEmpty(params.getRunState()),  Device::getRunState, params.getRunState())
                .orderByDesc(Device::getSort);
        if(StringUtils.isNotEmpty(params.getSpaceIds())){
            wrapper.in(Device::getSpaceId, Arrays.stream(params.getSpaceIds().split(",")).map(Long::parseLong).collect(Collectors.toList()));
        }
        if (StringUtils.isNotEmpty(params.getCategoryIds())){
            wrapper.in(Device::getCategoryId, Arrays.stream(params.getCategoryIds().split(",")).map(Long::parseLong).collect(Collectors.toList()));
        }
        if(params.getAssociatedPoint() != null){
            String sql = "select distinct device_id from device_attribute where acquisition_coding is not null";
            if(params.getAssociatedPoint()){
                wrapper.inSql(Device::getId,sql);
            }else{
                wrapper.notInSql(Device::getId,sql);
            }
        }
        return wrapper;
    }
}
