package org.jeecg.modules.bems.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.bems.dto.DataAmendLogDto;
import org.jeecg.modules.bems.dto.DataAmendParamDto;
import org.jeecg.modules.bems.entity.DataAmendLog;
import org.jeecg.modules.bems.mapper.DataAmendLogMapper;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.service.IDeviceService;
import org.jeecg.modules.bems.permission.entity.RoleDataPermission;
import org.jeecg.modules.bems.permission.service.RoleDataPermissionService;
import org.jeecg.modules.bems.permission.vo.UserDataScope;
import org.jeecg.modules.bems.service.IDataAmendLogService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class DataAmendLogServiceImpl extends ServiceImpl<DataAmendLogMapper, DataAmendLog> implements IDataAmendLogService {

    private final IDeviceService deviceService;
    private final RoleDataPermissionService roleDataPermissionService;

    @Override
    public IPage<DataAmendLogDto> listPage(DataAmendParamDto param) {
        // 1. 获取当前登录用户
        LoginUser sysUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (sysUser == null) {
            return new Page<>(param.getPageNo(), param.getPageSize());
        }

        // 2. 获取当前用户的数据权限范围
        UserDataScope dataScope = roleDataPermissionService.getCurrentUserDataScope();
        if (dataScope == null || CollectionUtil.isEmpty(dataScope.getPermissionIds(RoleDataPermission.TYPE_CATEGORY)) || CollectionUtil.isEmpty(dataScope.getPermissionIds(RoleDataPermission.TYPE_SPACE))) {
            // 无任何权限，返回空结果
            return new Page<>(param.getPageNo(), param.getPageSize());
        }

        // 3. 创建分页对象
        Page<DataAmendLog> page = new Page<>(param.getPageNo(), param.getPageSize());

        // 4. 使用 EXISTS 子查询进行分页查询（只需一次数据库调用）
        IPage<DataAmendLog> resultPage = baseMapper.selectPageWithPermission(
            page,
            param.getDeviceId(),
            param.getDeviceName(),
            param.getDeviceCode(),
            param.getSpaceIdList(),
            dataScope.getPermissionIds(RoleDataPermission.TYPE_CATEGORY),  // 数据权限：专业ID集合
            dataScope.getPermissionIds(RoleDataPermission.TYPE_SPACE),      // 数据权限：空间ID集合
            param.getAmendType()
        );

        // 5. 转换为 DTO
        IPage<DataAmendLogDto> result = resultPage.convert(DataAmendLogDto::convert);

        // 6. 补充设备名称和编号信息
        if (CollectionUtil.isNotEmpty(result.getRecords())) {
            List<Device> devices = deviceService.findByDeviceIds(
                result.getRecords().stream()
                    .map(DataAmendLogDto::getDeviceId)
                    .toList()
            );
            Map<Long, Device> deviceMap = devices.stream()
                .collect(Collectors.toMap(Device::getId, Function.identity(), (k1, k2) -> k2));

            result.getRecords().forEach(log -> {
                Device device = deviceMap.get(log.getDeviceId());
                if (device != null) {
                    log.setDeviceName(device.getDeviceName());
                    log.setDeviceCode(device.getDeviceCode());
                }
            });
        }

        return result;
    }
}
