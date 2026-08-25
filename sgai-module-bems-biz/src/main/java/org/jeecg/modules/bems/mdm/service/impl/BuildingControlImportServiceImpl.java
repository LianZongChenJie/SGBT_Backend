package org.jeecg.modules.bems.mdm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.bems.mdm.dto.BuildingControlImportDto;
import org.jeecg.modules.bems.mdm.entity.*;
import org.jeecg.modules.bems.mdm.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@AllArgsConstructor
public class BuildingControlImportServiceImpl implements IBuildingControlImportService {

    private final IDeviceService deviceService;
    private final IDeviceAttributeService deviceAttributeService;
    private final IDeviceModelService deviceModelService;
    private final IDeviceModelAttributeService deviceModelAttributeService;
    private final IEquipmentCategoryService equipmentCategoryService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> importBuildingControl(MultipartFile file, Long spaceId) {
        List<BuildingControlImportDto> rows = parseExcel(file);
        fillForward(rows);

        int categoryCount = processCategoriesAndModels(rows);
        int deviceCount = 0;
        int attributeCount = 0;

        // 按设备编码分组
        Map<String, List<BuildingControlImportDto>> deviceGroups = rows.stream()
                .filter(r -> r.getDeviceCode() != null)
                .collect(Collectors.groupingBy(BuildingControlImportDto::getDeviceCode, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<BuildingControlImportDto>> entry : deviceGroups.entrySet()) {
            String deviceCode = entry.getKey();
            List<BuildingControlImportDto> attrRows = entry.getValue();
            BuildingControlImportDto first = attrRows.get(0);

            // 查找类别和模型
            EquipmentCategory category = findCategory(first.getCategoryName());
            if (category == null) {
                throw new JeecgBootException("设备类别未找到: " + first.getCategoryName());
            }
            DeviceModel model = findModel(first.getCategoryName(), category.getId());
            if (model == null) {
                throw new JeecgBootException("设备模型未找到: " + first.getCategoryName());
            }

            // 删除已有设备和属性
            Device existing = deviceService.getByDeviceCode(deviceCode);
            if (existing != null) {
                deviceAttributeService.remove(new LambdaQueryWrapper<DeviceAttribute>()
                        .eq(DeviceAttribute::getDeviceId, existing.getId()));
                deviceService.removeById(existing.getId());
            }

            // 创建设备
            Device device = new Device();
            device.setDeviceCode(deviceCode);
            device.setDeviceName(first.getDeviceName());
            device.setDeviceType(Device.DEVICE_TYPE_EQUIPMENT);
            device.setCategoryId(category.getId());
            device.setModelId(model.getId());
            device.setSpaceId(spaceId);
            device.setSort(0);
            deviceService.save(device);

            deviceCount++;

            // 创建设备属性
            List<DeviceAttribute> attributes = new ArrayList<>();
            int sort = 1;
            for (BuildingControlImportDto attrRow : attrRows) {
                if (attrRow.getAttributeCode() == null || attrRow.getAttributeCode().trim().isEmpty()) {
                    continue;
                }
                DeviceAttribute attr = new DeviceAttribute();
                attr.setDeviceId(device.getId());
                attr.setAttributeName(attrRow.getAttributeName());
                attr.setAttributeCode(attrRow.getAttributeCode());
                attr.setUnit(attrRow.getUnit());
                attr.setAcquisitionCoding(attrRow.getAcquisitionCoding());
                attr.setValueConfig(attrRow.getValueConfig());
                attr.setReadwriteLevel(resolveReadwriteLevel(attrRow.getReadwriteLevel()));
                attr.setSort(sort++);
                attributes.add(attr);
            }

            if (!attributes.isEmpty()) {
                deviceAttributeService.saveBatch(attributes);
                attributeCount += attributes.size();
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("categoryCount", categoryCount);
        result.put("deviceCount", deviceCount);
        result.put("attributeCount", attributeCount);
        return result;
    }

    /**
     * 处理类别和模型（含模型属性）
     */
    private int processCategoriesAndModels(List<BuildingControlImportDto> rows) {
        // 按类别分组，收集每个类别的唯一属性
        Map<String, List<BuildingControlImportDto>> categoryGroups = rows.stream()
                .filter(r -> r.getCategoryName() != null)
                .collect(Collectors.groupingBy(BuildingControlImportDto::getCategoryName, LinkedHashMap::new, Collectors.toList()));

        int categoryCount = 0;

        for (Map.Entry<String, List<BuildingControlImportDto>> entry : categoryGroups.entrySet()) {
            String categoryName = entry.getKey();
            List<BuildingControlImportDto> categoryRows = entry.getValue();

            // 查找或创建类别
            EquipmentCategory category = findCategory(categoryName);
            if (category == null) {
                category = new EquipmentCategory();
                category.setCategoryName(categoryName);
                category.setType(EquipmentCategory.TYPE_EQUIPMENT);
                category.setPid(0L);
                category.setHasChild("0");
                category.setSort(0);
                equipmentCategoryService.save(category);
                categoryCount++;
            }

            // 查找或创建模型
            DeviceModel model = findModel(categoryName, category.getId());
            if (model == null) {
                model = new DeviceModel();
                model.setModelName(categoryName);
                model.setCategoryId(category.getId());
                deviceModelService.save(model);
            }

            // 收集唯一属性（按 attributeCode 去重）
            Map<String, BuildingControlImportDto> uniqueAttrs = new LinkedHashMap<>();
            for (BuildingControlImportDto row : categoryRows) {
                if (row.getAttributeCode() == null || row.getAttributeCode().trim().isEmpty()) {
                    continue;
                }
                uniqueAttrs.putIfAbsent(row.getAttributeCode(), row);
            }

            // 查找或创建模型属性
            int sort = 1;
            for (Map.Entry<String, BuildingControlImportDto> attrEntry : uniqueAttrs.entrySet()) {
                BuildingControlImportDto attrDto = attrEntry.getValue();
                DeviceModelAttribute existingAttr = deviceModelAttributeService.getOne(
                        new LambdaQueryWrapper<DeviceModelAttribute>()
                                .eq(DeviceModelAttribute::getModelId, model.getId())
                                .eq(DeviceModelAttribute::getAttributeCode, attrEntry.getKey()));
                if (existingAttr == null) {
                    DeviceModelAttribute modelAttr = new DeviceModelAttribute();
                    modelAttr.setModelId(model.getId());
                    modelAttr.setAttributeName(attrDto.getAttributeName());
                    modelAttr.setAttributeCode(attrDto.getAttributeCode());
                    modelAttr.setUnit(attrDto.getUnit());
                    modelAttr.setValueConfig(attrDto.getValueConfig());
                    modelAttr.setReadwriteLevel(resolveReadwriteLevel(attrDto.getReadwriteLevel()));
                    modelAttr.setSort(sort);
                    deviceModelAttributeService.save(modelAttr);
                }
                sort++;
            }
        }

        return categoryCount;
    }

    private EquipmentCategory findCategory(String categoryName) {
        return equipmentCategoryService.getOne(new LambdaQueryWrapper<EquipmentCategory>()
                .eq(EquipmentCategory::getCategoryName, categoryName)
                .eq(EquipmentCategory::getType, EquipmentCategory.TYPE_EQUIPMENT));
    }

    private DeviceModel findModel(String modelName, Long categoryId) {
        return deviceModelService.getOne(new LambdaQueryWrapper<DeviceModel>()
                .eq(DeviceModel::getModelName, modelName)
                .eq(DeviceModel::getCategoryId, categoryId));
    }

    private String resolveReadwriteLevel(String value) {
        if ("写".equals(value)) {
            return DeviceAttribute.READWRITE_LEVEL_WRITE;
        }
        return DeviceAttribute.READWRITE_LEVEL_READ;
    }

    /**
     * 解析 Excel 文件
     */
    private List<BuildingControlImportDto> parseExcel(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            List<BuildingControlImportDto> rows = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // 跳过表头
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                BuildingControlImportDto dto = new BuildingControlImportDto();
                dto.setCategoryName(getCellStringValue(row, 0));
                dto.setDeviceName(getCellStringValue(row, 1));
                dto.setDeviceCode(getCellStringValue(row, 2));
                dto.setReadwriteLevel(getCellStringValue(row, 3));
                dto.setAttributeName(getCellStringValue(row, 4));
                dto.setAttributeCode(getCellStringValue(row, 5));
                dto.setUnit(getCellStringValue(row, 6));
                dto.setAcquisitionCoding(getCellStringValue(row, 7));
                dto.setValueConfig(convertValueConfig(getCellStringValue(row, 8)));

                // 跳过全空行
                if (dto.getAttributeCode() == null && dto.getAttributeName() == null
                        && dto.getDeviceCode() == null && dto.getCategoryName() == null) {
                    continue;
                }
                rows.add(dto);
            }
            return rows;
        } catch (IOException e) {
            throw new JeecgBootException("解析 Excel 文件失败", e);
        }
    }

    /**
     * 向前填充空的设备信息（合并单元格场景）
     */
    private void fillForward(List<BuildingControlImportDto> rows) {
        String lastCategory = null;
        String lastDeviceName = null;
        String lastDeviceCode = null;

        for (BuildingControlImportDto row : rows) {
            if (row.getCategoryName() != null && !row.getCategoryName().trim().isEmpty()) {
                lastCategory = row.getCategoryName().trim();
            }
            row.setCategoryName(lastCategory);

            if (row.getDeviceName() != null && !row.getDeviceName().trim().isEmpty()) {
                lastDeviceName = row.getDeviceName().trim();
            }
            row.setDeviceName(lastDeviceName);

            if (row.getDeviceCode() != null && !row.getDeviceCode().trim().isEmpty()) {
                lastDeviceCode = row.getDeviceCode().trim();
            }
            row.setDeviceCode(lastDeviceCode);
        }
    }

    private static final Pattern VALUE_CONFIG_PATTERN = Pattern.compile("(\\S+?)\\s*=\\s*(.+)");

    private String convertValueConfig(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return raw;
        }
        String[] parts = raw.split(",");
        List<Map<String, String>> list = new ArrayList<>();
        for (String part : parts) {
            Matcher matcher = VALUE_CONFIG_PATTERN.matcher(part.trim());
            if (matcher.matches()) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("key", matcher.group(1));
                item.put("value", matcher.group(2).trim());
                list.add(item);
            }
        }
        if (list.isEmpty()) {
            return raw;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            Map<String, String> item = list.get(i);
            sb.append("{\"key\":\"").append(item.get("key")).append("\",\"value\":\"").append(item.get("value")).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String getCellStringValue(Row row, int cellIndex) {
        if (row.getCell(cellIndex) == null) {
            return null;
        }
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(cellIndex);
        switch (cell.getCellType()) {
            case STRING:
                String val = cell.getStringCellValue();
                return (val != null && val.trim().isEmpty()) ? null : val;
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }
}
