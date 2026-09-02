package org.jeecg.modules.bems.patterned.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.bems.patterned.entity.QualityStamp;

import java.util.List;

public interface IQualityStampService extends IService<QualityStamp> {

    List<QualityStamp> getList();
}
