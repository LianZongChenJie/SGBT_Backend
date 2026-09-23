package org.jeecg.modules.bems.mqtt.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.bems.mqtt.entity.CameraMessageLogEntity;
import org.jeecg.modules.bems.mqtt.mapper.CameraMessageLogMapper;
import org.jeecg.modules.bems.mqtt.service.CameraMessageLogService;
import org.springframework.stereotype.Service;

@Service
public class CameraMessageLogServiceImpl
        extends ServiceImpl<CameraMessageLogMapper, CameraMessageLogEntity>
        implements CameraMessageLogService {
}