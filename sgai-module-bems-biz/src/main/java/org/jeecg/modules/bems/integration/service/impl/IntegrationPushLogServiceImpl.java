package org.jeecg.modules.bems.integration.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.bems.integration.entity.IntegrationPushLog;
import org.jeecg.modules.bems.integration.mapper.IntegrationPushLogMapper;
import org.jeecg.modules.bems.integration.service.IIntegrationPushLogService;
import org.springframework.stereotype.Service;

@Service
public class IntegrationPushLogServiceImpl
        extends ServiceImpl<IntegrationPushLogMapper, IntegrationPushLog>
        implements IIntegrationPushLogService {
}
