package org.jeecg.modules.bems.patterned.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.bems.patterned.entity.QualityStamp;
import org.jeecg.modules.bems.patterned.mapper.QualityStampMapper;
import org.jeecg.modules.bems.patterned.service.IQualityStampService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QualityStampServiceImpl  extends ServiceImpl<QualityStampMapper, QualityStamp> implements IQualityStampService {
    @Override
    public List<QualityStamp> getList() {
        return list();
    }
}
