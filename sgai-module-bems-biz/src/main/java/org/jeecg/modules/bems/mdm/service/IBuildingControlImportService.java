package org.jeecg.modules.bems.mdm.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 楼控设备导入服务
 */
public interface IBuildingControlImportService {

    /**
     * 导入楼控设备（含设备模板和属性）
     *
     * @param file    Excel 文件
     * @param spaceId 默认空间ID
     * @return 导入统计
     */
    Map<String, Integer> importBuildingControl(MultipartFile file, Long spaceId);
}
