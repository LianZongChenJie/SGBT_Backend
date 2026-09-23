package org.jeecg.modules.bems.mdm.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.SelectTreeModel;
import org.jeecg.common.util.oConvertUtils;
import org.jeecg.modules.bems.integration.dto.UpsertResult;
import org.jeecg.modules.bems.mdm.entity.EquipmentCategory;
import org.jeecg.modules.bems.mdm.mapper.EquipmentCategoryMapper;
import org.jeecg.modules.bems.mdm.service.IEquipmentCategoryService;
import org.jeecg.modules.bems.mdm.vo.PermissionEquipmentCategoryTreeModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description: 设备类别
 * @Author: jeecg-boot
 * @Date: 2025-02-20
 * @Version: V1.0
 */
@Service
public class EquipmentCategoryServiceImpl extends ServiceImpl<EquipmentCategoryMapper, EquipmentCategory> implements IEquipmentCategoryService {

    @Override
    public void addEquipmentCategory(EquipmentCategory equipmentCategory) {
        String fullName = equipmentCategory.getCategoryName();
        String fullId = "";
        //新增时设置hasChild为0
        equipmentCategory.setHasChild(IEquipmentCategoryService.NOCHILD);
        // 校验同级别下名称是否重复
        if (baseMapper.selectCount(new QueryWrapper<EquipmentCategory>().eq("pid", equipmentCategory.getPid() == null ? IEquipmentCategoryService.ROOT_PID_VALUE : equipmentCategory.getPid()).eq("category_name", equipmentCategory.getCategoryName())) > 0) {
            throw new JeecgBootException("已存在相同名称的类别");
        }
        if (oConvertUtils.isEmpty(equipmentCategory.getPid())) {
            equipmentCategory.setPid(IEquipmentCategoryService.ROOT_PID_VALUE);
            fullId = equipmentCategory.getPid().toString();
        } else {
            //如果当前节点父ID不为空 则设置父节点的hasChildren 为1
            EquipmentCategory parent = baseMapper.selectById(equipmentCategory.getPid());
            if (parent != null && !"1".equals(parent.getHasChild())) {
                parent.setHasChild("1");
                baseMapper.updateById(parent);
            }
            if (parent != null) {
                fullName = parent.getFullName() + fullName;
                fullId = parent.getFullId() + IEquipmentCategoryService.connector + equipmentCategory.getPid();
            }
        }
        equipmentCategory.setFullName(fullName);
        equipmentCategory.setFullId(fullId);
        baseMapper.insert(equipmentCategory);
    }

    @Override
    public void updateEquipmentCategory(EquipmentCategory equipmentCategory) {
        EquipmentCategory entity = this.getById(equipmentCategory.getId());
        if (entity == null) {
            throw new JeecgBootException("未找到对应实体");
        }
        // 校验同级别下名称是否重复
        if (baseMapper.selectCount(new QueryWrapper<EquipmentCategory>().eq("pid", equipmentCategory.getPid()).eq("category_name", equipmentCategory.getCategoryName()).ne("id", equipmentCategory.getId())) > 0) {
            throw new JeecgBootException("已存在相同名称的类别");
        }
        Long old_pid = entity.getPid();
        Long new_pid = equipmentCategory.getPid();
        if (!old_pid.equals(new_pid)) {
            updateOldParentNode(old_pid);
            if (oConvertUtils.isEmpty(new_pid)) {
                equipmentCategory.setPid(IEquipmentCategoryService.ROOT_PID_VALUE);
            }
            if (!IEquipmentCategoryService.ROOT_PID_VALUE.equals(equipmentCategory.getPid())) {
                baseMapper.updateTreeNodeStatus(equipmentCategory.getPid(), IEquipmentCategoryService.HASCHILD);
            }
        }
        EquipmentCategory parent = null;
        if (equipmentCategory.getPid().compareTo(IEquipmentCategoryService.ROOT_PID_VALUE) != 0) {
            parent = this.getById(equipmentCategory.getPid());
        }
        equipmentCategory.setFullName(parent == null ? equipmentCategory.getCategoryName() : parent.getFullName() + equipmentCategory.getCategoryName());
        equipmentCategory.setFullId(parent == null ? equipmentCategory.getPid().toString() : parent.getFullId() + IEquipmentCategoryService.connector + equipmentCategory.getPid());
        // 更新子节点fullName、fullId
        baseMapper.updateFullInfo(entity.getFullId() + IEquipmentCategoryService.connector + entity.getId(),
                equipmentCategory.getFullName(), equipmentCategory.getFullId() + IEquipmentCategoryService.connector + equipmentCategory.getId());
        equipmentCategory.setType(null);
        baseMapper.updateById(equipmentCategory);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteEquipmentCategory(String id) throws JeecgBootException {
        //查询选中节点下所有子节点一并删除
        id = this.queryTreeChildIds(id);
        if (id.indexOf(",") > 0) {
            StringBuffer sb = new StringBuffer();
            String[] idArr = id.split(",");
            for (String idVal : idArr) {
                if (idVal != null) {
                    EquipmentCategory equipmentCategory = this.getById(idVal);
                    Long pidVal = equipmentCategory.getPid();
                    //查询此节点上一级是否还有其他子节点
                    List<EquipmentCategory> dataList = baseMapper.selectList(new QueryWrapper<EquipmentCategory>().eq("pid", pidVal).notIn("id", Arrays.asList(idArr)));
                    boolean flag = (dataList == null || dataList.size() == 0) && !Arrays.asList(idArr).contains(pidVal.toString()) && !sb.toString().contains(pidVal.toString());
                    if (flag) {
                        //如果当前节点原本有子节点 现在木有了，更新状态
                        sb.append(pidVal).append(",");
                    }
                }
            }
            //批量删除节点
            baseMapper.deleteBatchIds(Arrays.asList(idArr));
            //修改已无子节点的标识
            String[] pidArr = sb.toString().split(",");
            for (String pid : pidArr) {
                this.updateOldParentNode(Long.valueOf(pid));
            }
        } else {
            EquipmentCategory equipmentCategory = this.getById(id);
            if (equipmentCategory == null) {
                throw new JeecgBootException("未找到对应实体");
            }
            updateOldParentNode(equipmentCategory.getPid());
            baseMapper.deleteById(id);
        }
    }

    @Override
    public List<EquipmentCategory> queryTreeListNoPage(QueryWrapper<EquipmentCategory> queryWrapper) {
        List<EquipmentCategory> dataList = baseMapper.selectList(queryWrapper);
        List<EquipmentCategory> mapList = new ArrayList<>();
        for (EquipmentCategory data : dataList) {
            Long pidVal = data.getPid();
            //递归查询子节点的根节点
            if (pidVal != null && !IEquipmentCategoryService.NOCHILD.equals(pidVal.toString())) {
                EquipmentCategory rootVal = this.getTreeRoot(pidVal);
                if (rootVal != null && !mapList.contains(rootVal)) {
                    mapList.add(rootVal);
                }
            } else {
                if (!mapList.contains(data)) {
                    mapList.add(data);
                }
            }
        }
        return mapList;
    }

    @Override
    public List<SelectTreeModel> queryListByCode(String parentCode) {
        return queryListByTypeAndCode(parentCode,null);
    }

    @Override
    public List<SelectTreeModel> queryListByTypeAndCode(String type, String parentCode) {
        Long pid = ROOT_PID_VALUE;
        if (oConvertUtils.isNotEmpty(parentCode)) {
            LambdaQueryWrapper<EquipmentCategory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(EquipmentCategory::getPid, parentCode);
            queryWrapper.eq(StringUtils.isNotEmpty(type),EquipmentCategory::getType, type);
            List<EquipmentCategory> list = baseMapper.selectList(queryWrapper);
            if (list == null || list.size() == 0) {
                throw new JeecgBootException("该编码【" + parentCode + "】不存在，请核实!");
            }
            if (list.size() > 1) {
                throw new JeecgBootException("该编码【" + parentCode + "】存在多个，请核实!");
            }
            pid = list.get(0).getId();
        }
        return baseMapper.queryListByTypeAndPid(pid, type);
    }

    @Override
    public List<SelectTreeModel> queryListByPid(Long pid) {
        if (oConvertUtils.isEmpty(pid)) {
            pid = ROOT_PID_VALUE;
        }
        return baseMapper.queryListByPid(pid, null);
    }

    /**
     * 构建树
     *
     * @return
     */
    @Override
    public List<SelectTreeModel> buildTree() {
        return buildTree(null);
    }

    /**
     * 构建树
     *
     * @param type 类型。仪表：1；设备：2
     * @return
     */
    @Override
    public List<SelectTreeModel> buildTree(String type) {
        Map<String, List<SelectTreeModel>> listMap = list(new LambdaQueryWrapper<EquipmentCategory>().eq(StringUtils.isNotEmpty(type), EquipmentCategory::getType, type))
                .stream().sorted(Comparator.comparing(EquipmentCategory::getSort))
                .map(EquipmentCategory::convert)
                .collect(Collectors.groupingBy(SelectTreeModel::getParentId, Collectors.toList()));
        listMap.values().forEach(v -> {
            for (SelectTreeModel item : v) {
                item.setChildren(listMap.getOrDefault(item.getKey(), Collections.emptyList()));
            }
        });
        return listMap.getOrDefault("0", Collections.emptyList());
    }

    /**
     * 根据类型查询列表
     *
     * @param type 类型。仪表：1；设备：2
     * @return
     */
    @Override
    public List<EquipmentCategory> queryListByType(String type) {
        return super.list(new LambdaQueryWrapper<EquipmentCategory>().eq(EquipmentCategory::getType, type));
    }

    @Override
    public List<EquipmentCategory> findByIds(Collection<Long> ids) {
        if(CollectionUtil.isEmpty(ids)){
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<EquipmentCategory>()
                .in(EquipmentCategory::getId, ids));
    }

    /**
     * 根据所传pid查询旧的父级节点的子节点并修改相应状态值
     *
     * @param pid
     */
    private void updateOldParentNode(Long pid) {
        if (IEquipmentCategoryService.ROOT_PID_VALUE.compareTo(pid) != 0) {
            Long count = baseMapper.selectCount(new QueryWrapper<EquipmentCategory>().eq("pid", pid));
            if (count == null || count <= 1) {
                baseMapper.updateTreeNodeStatus(pid, IEquipmentCategoryService.NOCHILD);
            }
        }
    }

    /**
     * 递归查询节点的根节点
     *
     * @param pidVal
     * @return
     */
    private EquipmentCategory getTreeRoot(Long pidVal) {
        EquipmentCategory data = baseMapper.selectById(pidVal);
        if (data != null && IEquipmentCategoryService.ROOT_PID_VALUE.compareTo(data.getPid()) != 0) {
            return this.getTreeRoot(data.getPid());
        } else {
            return data;
        }
    }

    /**
     * 根据id查询所有子节点id
     *
     * @param ids
     * @return
     */
    private String queryTreeChildIds(String ids) {
        //获取id数组
        String[] idArr = ids.split(",");
        StringBuffer sb = new StringBuffer();
        for (String pidVal : idArr) {
            if (pidVal != null) {
                if (!sb.toString().contains(pidVal)) {
                    if (sb.toString().length() > 0) {
                        sb.append(",");
                    }
                    sb.append(pidVal);
                    this.getTreeChildIds(Long.valueOf(pidVal), sb);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 递归查询所有子节点
     *
     * @param pidVal
     * @param sb
     * @return
     */
    private StringBuffer getTreeChildIds(Long pidVal, StringBuffer sb) {
        List<EquipmentCategory> dataList = baseMapper.selectList(new QueryWrapper<EquipmentCategory>().eq("pid", pidVal));
        if (dataList != null && dataList.size() > 0) {
            for (EquipmentCategory tree : dataList) {
                if (!sb.toString().contains(tree.getId().toString())) {
                    sb.append(",").append(tree.getId());
                }
                this.getTreeChildIds(tree.getId(), sb);
            }
        }
        return sb;
    }

    /**
     * 根据用户权限构建设备类别树（完全非递归实现）
     *
     * @param categoryIds 用户有权限的设备类别ID集合
     * @param type 类型。仪表：1；设备：2；null 表示全部
     * @return 包含权限标记的设备类别树
     */
    @Override
    public List<PermissionEquipmentCategoryTreeModel> buildPermissionTree(Collection<Long> categoryIds, String type) {
        // 1. 参数校验
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 查询所有设备类别节点（支持按 type 过滤）
        LambdaQueryWrapper<EquipmentCategory> wrapper = new LambdaQueryWrapper<EquipmentCategory>()
                .eq(StringUtils.isNotEmpty(type), EquipmentCategory::getType, type);
        List<EquipmentCategory> allCategories = list(wrapper);

        // 3. 构建ID->EquipmentCategory的映射
        Map<Long, EquipmentCategory> categoryMap = allCategories.stream()
                .collect(Collectors.toMap(EquipmentCategory::getId, c -> c));

        // 4. 收集需要包含的节点：权限节点及其所有父节点（非递归）
        Set<Long> permissionIds = new HashSet<>(categoryIds);
        Set<Long> includedIds = new HashSet<>();
        for (Long categoryId : categoryIds) {
            collectNodeAndAncestors(categoryId, categoryMap, includedIds);
        }

        // 5. 过滤并转换为 PermissionEquipmentCategoryTreeModel，同时标记权限
        List<PermissionEquipmentCategoryTreeModel> filteredModels = allCategories.stream()
                .filter(c -> includedIds.contains(c.getId()))
                .sorted(Comparator.comparing(EquipmentCategory::getSort))
                .map(category -> {
                    PermissionEquipmentCategoryTreeModel model = new PermissionEquipmentCategoryTreeModel();
                    model.setKey(category.getId().toString());
                    model.setTitle(category.getCategoryName());
                    model.setParentId(category.getPid().toString());
                    model.setValue(category.getFullName());
                    // 标记复选框状态：无权限时禁用
                    model.setDisableCheckbox(!permissionIds.contains(category.getId()));  // 取反
                    return model;
                })
                .collect(Collectors.toList());

        // 6. 按parentId分组
        Map<String, List<PermissionEquipmentCategoryTreeModel>> listMap = filteredModels.stream()
                .collect(Collectors.groupingBy(PermissionEquipmentCategoryTreeModel::getParentId, Collectors.toList()));

        // 7. 设置children（非递归）
        listMap.values().forEach(children -> {
            for (PermissionEquipmentCategoryTreeModel item : children) {
                @SuppressWarnings("unchecked")
                List<SelectTreeModel> itemChildren = (List<SelectTreeModel>) (List<?>) listMap.getOrDefault(item.getKey(), Collections.emptyList());
                item.setChildren(itemChildren);
            }
        });

        // 8. 返回根节点列表
        return listMap.getOrDefault("0", Collections.emptyList());
    }

    /**
     * 非递归收集节点及其所有祖先节点
     *
     * @param nodeId 起始节点id
     * @param categoryMap 所有节点的映射
     * @param includedIds 收集的结果集
     */
    private void collectNodeAndAncestors(Long nodeId, Map<Long, EquipmentCategory> categoryMap, Set<Long> includedIds) {
        Long currentId = nodeId;
        while (currentId != null && !IEquipmentCategoryService.ROOT_PID_VALUE.equals(currentId)) {
            if (includedIds.contains(currentId)) {
                // 如果已经包含该节点，说明其祖先节点也已包含，可以提前终止
                break;
            }
            includedIds.add(currentId);
            EquipmentCategory current = categoryMap.get(currentId);
            if (current == null) {
                break;
            }
            currentId = current.getPid();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpsertResult upsertByMasterId(String masterId, String name, String masterPid, String type, Integer sort) {
        // 1. 解析 pid（master uuid / "0" -> 本地 Long）
        Long localPid;
        if (masterPid == null || "0".equals(masterPid)) {
            localPid = IEquipmentCategoryService.ROOT_PID_VALUE;
        } else {
            EquipmentCategory parentByMaster = baseMapper.selectOne(
                    new QueryWrapper<EquipmentCategory>().eq("master_id", masterPid));
            if (parentByMaster == null) {
                // 父类别尚未同步：孤儿挂载到根（fullName 由本地算法重算为 name 自身），
                // 等 master 重发本类别时由 update 分支回挂到真实父下并级联重算 fullName
                localPid = IEquipmentCategoryService.ROOT_PID_VALUE;
            } else {
                localPid = parentByMaster.getId();
            }
        }

        // 2. 查本地是否已存在（按 master_id）
        EquipmentCategory exist = baseMapper.selectOne(
                new QueryWrapper<EquipmentCategory>().eq("master_id", masterId));

        // 3. 校验同级别下名称是否重复（重名不抛异常，返回 fail）
        QueryWrapper<EquipmentCategory> dupWrapper = new QueryWrapper<EquipmentCategory>()
                .eq("pid", localPid)
                .eq("category_name", name);
        if (exist != null) {
            dupWrapper.ne("id", exist.getId());
        }
        if (baseMapper.selectCount(dupWrapper) > 0) {
            return UpsertResult.fail("已存在相同名称的类别");
        }

        // 4. 计算父节点（用于全称重建）
        EquipmentCategory parent = localPid.equals(IEquipmentCategoryService.ROOT_PID_VALUE)
                ? null : baseMapper.selectById(localPid);

        if (exist == null) {
            // 5a. 新增（复用 addEquipmentCategory 的全称算法）
            EquipmentCategory cat = new EquipmentCategory();
            cat.setMasterId(masterId);
            cat.setCategoryName(name);
            cat.setPid(localPid);
            cat.setType(type);
            cat.setHasChild(IEquipmentCategoryService.NOCHILD);
            // sort：insert 空存 0
            cat.setSort(sort == null ? 0 : sort);
            // 全称：根节点为 name 自身；非根为 parent.getFullName() + name
            String fullName = name;
            String fullId = localPid.toString();
            if (parent != null) {
                fullName = parent.getFullName() + name;
                fullId = parent.getFullId() + IEquipmentCategoryService.connector + localPid;
                // 维护父节点 hasChild
                if (!IEquipmentCategoryService.HASCHILD.equals(parent.getHasChild())) {
                    baseMapper.updateTreeNodeStatus(parent.getId(), IEquipmentCategoryService.HASCHILD);
                }
            }
            cat.setFullName(fullName);
            cat.setFullId(fullId);
            baseMapper.insert(cat);
            return UpsertResult.ok(cat.getId());
        } else {
            // 5b. 更新（复用 updateEquipmentCategory 的全称算法）
            Long oldPid = exist.getPid();
            boolean pidChanged = oldPid == null || !localPid.equals(oldPid);
            if (pidChanged && oldPid != null && !oldPid.equals(IEquipmentCategoryService.ROOT_PID_VALUE)) {
                // 旧父若无其他子节点，置 NOCHILD
                updateOldParentNode(oldPid);
            }
            // 计算新全称
            String newFullName = parent == null ? name : parent.getFullName() + name;
            String newFullId = parent == null
                    ? localPid.toString()
                    : parent.getFullId() + IEquipmentCategoryService.connector + localPid;
            // 旧 fullId 前缀（用于级联更新子节点）
            String oldChildPrefix = exist.getFullId() + IEquipmentCategoryService.connector + exist.getId();
            String newChildPrefix = newFullId + IEquipmentCategoryService.connector + exist.getId();

            exist.setCategoryName(name);
            exist.setPid(localPid);
            exist.setType(type);
            // sort：非空才覆盖原值
            if (sort != null) {
                exist.setSort(sort);
            }
            exist.setFullName(newFullName);
            exist.setFullId(newFullId);
            // pid 变化时维护新父 hasChild
            if (pidChanged && parent != null) {
                baseMapper.updateTreeNodeStatus(parent.getId(), IEquipmentCategoryService.HASCHILD);
            }
            // 级联更新子节点 fullName/fullId（沿用现有 updateFullInfo 语义）
            baseMapper.updateFullInfo(oldChildPrefix, newFullName, newChildPrefix);
            baseMapper.updateById(exist);
            return UpsertResult.ok(exist.getId());
        }
    }

}
