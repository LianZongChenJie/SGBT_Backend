package org.jeecg.modules.bems.mqtt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.bems.mqtt.entity.VehicleRecordEntity;
import org.jeecg.modules.bems.mqtt.mapper.VehicleRecordMapper;
import org.jeecg.modules.bems.mqtt.service.VehicleRecordService;
import org.springframework.stereotype.Service;

@Service
public class VehicleRecordServiceImpl
        extends ServiceImpl<VehicleRecordMapper, VehicleRecordEntity>
        implements VehicleRecordService {

    @Override
    public void saveOrUpdateByMsgId(VehicleRecordEntity entity) {
        VehicleRecordEntity exist = getOne(new LambdaQueryWrapper<VehicleRecordEntity>()
                .eq(VehicleRecordEntity::getMsgId, entity.getMsgId())
                .last("limit 1"));
        if (exist != null) {
            entity.setId(exist.getId());
            updateById(entity);
        } else {
            save(entity);
        }
    }

    @Override
    public VehicleRecordEntity findLatestByUtcTs(Long utcTs) {
        return getOne(new LambdaQueryWrapper<VehicleRecordEntity>()
                .eq(VehicleRecordEntity::getUtcTs, utcTs)
                .eq(VehicleRecordEntity::getImageUploaded, false)
                .orderByDesc(VehicleRecordEntity::getCreateTime)
                .last("limit 1"));
    }
}