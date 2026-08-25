package org.jeecg.modules.bems.mdm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.bems.mdm.entity.DeviceModel;
import org.jeecg.modules.bems.mdm.mapper.DeviceModelMapper;
import org.jeecg.modules.bems.mdm.service.IDeviceModelService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceModelServiceImpl extends ServiceImpl<DeviceModelMapper, DeviceModel> implements IDeviceModelService {

    /**
     * 设置默认模板时的互斥处理：
     * 1. categoryId 为空则报错
     * 2. 把同类别下其他默认模板置为非默认
     */
    private void handleDefaultSwitch(Long categoryId, Long excludeId) {
        if (categoryId == null) {
            throw new JeecgBootException("请先选择设备类别");
        }
        this.update(new LambdaUpdateWrapper<DeviceModel>()
                .eq(DeviceModel::getCategoryId, categoryId)
                .eq(DeviceModel::getIsDefault, 1)
                .ne(excludeId != null, DeviceModel::getId, excludeId)
                .set(DeviceModel::getIsDefault, 0));
    }

    @Override
    public boolean save(DeviceModel entity) {
        // 校验名称是否存在
        if(baseMapper.selectCount(new LambdaQueryWrapper<DeviceModel>().eq(DeviceModel::getModelName, entity.getModelName()) ) > 0){
            throw new JeecgBootException("模型名称已存在");
        }
        // 如果设为默认，先取消同类别下其他默认模板
        if (Integer.valueOf(1).equals(entity.getIsDefault())) {
            handleDefaultSwitch(entity.getCategoryId(), null);
        }
        return super.save(entity);
    }

    @Override
    public boolean updateById(DeviceModel entity) {
        // 校验名称是否存在
        if(baseMapper.selectCount(new LambdaQueryWrapper<DeviceModel>().eq(DeviceModel::getModelName, entity.getModelName()).ne(DeviceModel::getId, entity.getId())) > 0){
            throw new JeecgBootException("模型名称已存在");
        }
        // 设为默认时的互斥处理
        if (Integer.valueOf(1).equals(entity.getIsDefault())) {
            Long categoryId = entity.getCategoryId();
            if (categoryId == null) {
                // 入参没带 categoryId，从库里取当前值兜底
                DeviceModel current = this.getById(entity.getId());
                categoryId = current != null ? current.getCategoryId() : null;
            }
            handleDefaultSwitch(categoryId, entity.getId());
        }
        return super.updateById(entity);
    }

    @Override
    public IPage<DeviceModel> queryPage(DeviceModel params) {
        IPage<DeviceModel> page = new Page<DeviceModel>(params.getPageNo(),params.getPageSize());
        return page(page, new LambdaQueryWrapper<DeviceModel>()
                .like(StringUtils.isNotEmpty(params.getModelName()), DeviceModel::getModelName, params.getModelName())
                .eq(params.getCategoryId() != null, DeviceModel::getCategoryId, params.getCategoryId())
                .orderByDesc(DeviceModel::getCreateTime));
    }

    @Override
    public List<DeviceModel> queryByCategoryId(Long categoryId) {
        return list(new LambdaQueryWrapper<DeviceModel>().eq(DeviceModel::getCategoryId, categoryId));
    }

    @Override
    public DeviceModel queryDefaultByCategoryId(Long categoryId) {
        return getOne(new LambdaQueryWrapper<DeviceModel>()
                .eq(DeviceModel::getCategoryId, categoryId)
                .eq(DeviceModel::getIsDefault, 1)
                .last("LIMIT 1"));
    }
}
