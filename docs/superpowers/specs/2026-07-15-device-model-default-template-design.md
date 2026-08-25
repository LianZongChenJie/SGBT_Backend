# 设备模板默认标记 设计文档

- 日期：2026-07-15
- 范围：`sgai-module-bems-biz` 主数据管理（mdm）模块
- 涉及实体：`DeviceModel`（设备模板 / 设备模型，表 `device_model`）

## 1. 背景与目标

设备模板（`DeviceModel`）是根据设备类别（`EquipmentCategory`）建立的，一个设备类别可以建立多个设备模板。本次需求：

- 在设备模板上增加「是否为默认模板」选项。
- **一个设备类别只能有一个默认模板。**

### 现状

- 设备模板 = `DeviceModel`（表 `device_model`），字段：`modelName`、`categoryId`
- 设备类别 = `EquipmentCategory`（表 `equipment_category`，树形结构）
- 关联：`DeviceModel.categoryId` → `equipment_category.id`，一个类别下可有多个 `DeviceModel`
- 现有 Controller（`/bems/deviceModel`）已有：add / edit / delete / deleteBatch / queryPage / queryByCategoryId

## 2. 关键决策（已确认）

| 决策点 | 结论 |
|---|---|
| 互斥行为 | **自动切换**：设新默认时，系统自动取消该类别下原默认模板标记，保证类别内唯一 |
| 字段存储 | 在 `device_model` 表新增布尔标记列 `is_default`（0/1） |
| 类别为空 | 设默认时要求模板必须有 `categoryId`，否则报错 |
| 查询接口 | 新增「根据类别查询默认模板」的单独接口 |
| 实现方案 | **方案 A**：互斥逻辑沉在 Service 层 save/updateById，Controller 无感知 |
| 无默认状态 | 允许一个类别处于「无默认模板」状态（不强制每个类别都有默认） |

## 3. 数据模型

在 `device_model` 表新增一列：

- **列名**：`is_default`
- **类型**：`tinyint(1)`
- **默认值**：`0`（非默认）
- **注释**：是否为默认模板（0=否，1=是）

对应实体 `DeviceModel` 新增字段：

```java
/**
 * 是否为默认模板（0=否，1=是）
 */
private Integer isDefault;
```

MyBatis-Plus 默认下划线驼峰映射，`isDefault` ↔ `is_default`，无需额外注解。存量数据该列全部为 `0`，符合「无默认模板」的初始状态。

## 4. 业务规则

围绕「一个类别只有一个默认模板」的核心约束：

**规则 1：设置默认时的互斥切换（自动切换）**
当 add 或 edit 把某个模板的 `isDefault` 置为 `1` 时：
1. 先校验该模板的 `categoryId` 不为空，为空则抛 `JeecgBootException("请先选择设备类别")`。
2. 把同 `categoryId` 下、当前 `isDefault=1` 的其他模板批量更新为 `0`（排除当前模板自身）。
3. 再保存/更新当前模板 `isDefault=1`。

**规则 2：取消默认**
当 edit 把某模板的 `isDefault` 从 `1` 改为 `0` 时：直接置 `0`，不做任何额外操作。该类别此时可能处于「无默认模板」状态——这是允许的。

**规则 3：编辑时的 categoryId 兜底**
`updateById` 默认只更新非空字段。互斥判断需要 `categoryId`，但 edit 请求体可能没带 `categoryId`。因此：
- 若入参 `categoryId` 为空，先从库里查出当前模板的 `categoryId` 兜底。
- 若库里的 `categoryId` 也为空，抛「请先选择设备类别」。

**规则 4：删除与批量删除**
删除默认模板时**不报错**，直接删除。该类别此时进入「无默认」状态，无需特殊处理。

**规则 5：不设默认的合法性**
一个类别可以没有任何模板被标记为默认（全部 `isDefault=0`），合法状态，不做强制。

## 5. 接口设计

现有接口全部保留，互斥逻辑沉在 Service 层，Controller 的 add/edit 无感知。仅新增一个查询接口。

### 新增：根据设备类别查询其默认模板

```
GET /bems/deviceModel/queryDefaultByCategoryId?categoryId=xxx
```

- 入参：`categoryId`（Long）
- 逻辑：查询 `category_id = xxx AND is_default = 1` 的模板
- 返回：`Result<DeviceModel>`
  - 找到默认模板 → 返回该模板
  - 该类别无默认模板 → 返回 `Result.ok(null)`

## 6. Service 层改动细节

### 6.1 抽取私有方法 `handleDefaultSwitch`

把互斥逻辑抽成一个私有方法，供 save 和 updateById 复用：

```java
/**
 * 设置默认模板时的互斥处理：
 * 1. categoryId 为空则报错
 * 2. 把同类别下其他默认模板置为非默认
 */
private void handleDefaultSwitch(Long categoryId, Long excludeId) {
    if (categoryId == null) {
        throw new JeecgBootException("请先选择设备类别");
    }
    this.update(new LambdaUpdateWrapper<DeviceModel>()
            .eq(DeviceModel::getCategoryId, categoryId)
            .eq(DeviceModel::getIsDefault, 1)
            .ne(excludeId != null, DeviceModel::getId, excludeId)
            .set(DeviceModel::getIsDefault, 0));
}
```

### 6.2 改 `save()`

在现有名称校验之后、`super.save()` 之前加：

```java
// 如果设为默认，先取消同类别下其他默认模板
if (Integer.valueOf(1).equals(entity.getIsDefault())) {
    handleDefaultSwitch(entity.getCategoryId(), null);
}
```

新增模板 id 还没生成，`excludeId` 传 null（新模板自己还没入库，不会被误更新）。

### 6.3 改 `updateById()`

现有名称校验之后加互斥处理，关键是 categoryId 兜底：

```java
// 设为默认时的互斥处理
if (Integer.valueOf(1).equals(entity.getIsDefault())) {
    Long categoryId = entity.getCategoryId();
    if (categoryId == null) {
        // 入参没带 categoryId，从库里取当前值兜底
        DeviceModel current = this.getById(entity.getId());
        categoryId = current != null ? current.getCategoryId() : null;
    }
    handleDefaultSwitch(categoryId, entity.getId());
}
```

`excludeId` 传当前模板 id，避免把自己也置成 0。

### 6.4 新增查询方法

`IDeviceModelService` 加方法签名：

```java
DeviceModel queryDefaultByCategoryId(Long categoryId);
```

`DeviceModelServiceImpl` 实现：

```java
@Override
public DeviceModel queryDefaultByCategoryId(Long categoryId) {
    return getOne(new LambdaQueryWrapper<DeviceModel>()
            .eq(DeviceModel::getCategoryId, categoryId)
            .eq(DeviceModel::getIsDefault, 1)
            .last("LIMIT 1"));
}
```

用 `LIMIT 1` 防御性兜底，即便数据出现异常也不会抛 TooManyResultsException。

### 6.5 import 补充

ServiceImpl 需新增 import：`com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper`。

## 7. 数据库迁移与接口契约

### 7.1 DDL

```sql
ALTER TABLE `device_model`
ADD COLUMN `is_default` tinyint(1) DEFAULT 0 COMMENT '是否为默认模板（0=否，1=是）';
```

存量数据自动补 `0`（非默认），符合「无默认」初始状态。

### 7.2 接口契约汇总

| 接口 | 方法 | 改动 | 说明 |
|---|---|---|---|
| `/bems/deviceModel/add` | POST | 请求体新增 `isDefault` 字段（可选） | 设为 1 时触发互斥切换 |
| `/bems/deviceModel/edit` | POST | 请求体新增 `isDefault` 字段（可选） | 设为 1 时触发互斥切换；不传该字段则不影响现有默认状态 |
| `/bems/deviceModel/delete` | DELETE | 无变化 | 删除默认模板不报错 |
| `/bems/deviceModel/deleteBatch` | DELETE | 无变化 | 同上 |
| `/bems/deviceModel/queryPage` | GET | 响应体多返回 `isDefault` 字段 | 前端可据此高亮/标记默认 |
| `/bems/deviceModel/queryByCategoryId` | GET | 响应体多返回 `isDefault` 字段 | 同上 |
| `/bems/deviceModel/queryDefaultByCategoryId` | GET | **新增** | 入参 `categoryId`，返回该类别默认模板，无则 null |

### 7.3 字段说明

`isDefault` 字段统一用 `Integer` 类型，取值 `0` / `1`，前端按布尔勾选框处理即可。

## 8. 涉及文件清单

| 文件 | 改动类型 |
|---|---|
| `mdm/entity/DeviceModel.java` | 新增 `isDefault` 字段 |
| `mdm/service/IDeviceModelService.java` | 新增 `queryDefaultByCategoryId` 方法签名 |
| `mdm/service/impl/DeviceModelServiceImpl.java` | 改 save/updateById、抽 `handleDefaultSwitch`、新增查询实现、补 import |
| `mdm/controller/DeviceModelController.java` | 新增 `queryDefaultByCategoryId` 接口 |
| 数据库 | `device_model` 表新增 `is_default` 列 |
