package org.jeecg.modules.bems.mdm.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.bems.mdm.entity.MonitorPoint;

import java.util.List;

/**
 * 监测采集点 Service
 */
public interface IMonitorPointService extends IService<MonitorPoint> {

    /**
     * 分页/条件查询实时采集点
     *
     * @param category  设备类别（可空）
     * @param online    在线状态（可空，1在线 0离线）
     * @param keyword   名称/描述关键字（可空，模糊）
     * @param pageNo    页码
     * @param pageSize  页大小
     */
    IPage<MonitorPoint> pagePoints(String category, Integer online, String keyword,
                                   long pageNo, long pageSize);

    /**
     * 设备类别去重列表
     */
    List<String> listCategories();
}
