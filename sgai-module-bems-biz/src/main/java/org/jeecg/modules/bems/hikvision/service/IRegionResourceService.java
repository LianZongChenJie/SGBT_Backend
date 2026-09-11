package org.jeecg.modules.bems.hikvision.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.bems.hikvision.entity.RegionResource;
import org.jeecg.modules.bems.hikvision.dto.RegionTreeVO;

import java.util.List;

/**
 * 区域资源同步服务接口
 *
 * @author bems
 */
public interface IRegionResourceService extends IService<RegionResource> {

    /**
     * 从海康平台全量拉取区域数据并同步到数据库
     * <p>同步逻辑：先清空表内全部数据，再逐页拉取海康数据批量插入。</p>
     *
     * @return 同步成功的记录数
     */
    int syncFromHikvision();

    /**
     * 从数据库查询全部区域并构建树形结构
     * <p>从根节点（parentIndexCode = "-1"）开始递归构建。</p>
     *
     * @return 区域树根节点列表
     */
    List<RegionTreeVO> buildRegionTree();

    /**
     * 根据摄像头资源表统计并更新各区域的摄像头资源数量
     * <p>localQuantity = 直接挂载在该区域下的摄像头数量（region_index_code 等于该区域）；
     * totalQuantity = 该区域及所有下级区域的摄像头数量总和。</p>
     *
     * @return 更新的区域条数
     */
    int syncCameraQuantity();
}
