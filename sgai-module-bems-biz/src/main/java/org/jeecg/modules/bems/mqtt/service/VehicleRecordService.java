package org.jeecg.modules.bems.mqtt.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.bems.mqtt.entity.VehicleRecordEntity;

public interface VehicleRecordService extends IService<VehicleRecordEntity> {

    /**
     * 根据 msgId 保存或更新
     */
    void saveOrUpdateByMsgId(VehicleRecordEntity entity);

    /**
     * 根据 utc_ts 查找最近一条未上传图片的记录
     */
    VehicleRecordEntity findLatestByUtcTs(Long utcTs);
}