# 设备模板默认标记 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在设备模板（`DeviceModel`）上增加「是否为默认模板」标记，保证一个设备类别下至多一个默认模板，并新增按类别查询默认模板的接口。

**Architecture:** 在 `device_model` 表新增 `is_default` 列；互斥逻辑（设新默认时自动取消同类别原默认）沉在 `DeviceModelServiceImpl` 的 `save()` / `updateById()` 中，Controller 无感知；新增一个按类别查询默认模板的 GET 接口。

**Tech Stack:** Java、Spring Boot、MyBatis-Plus（`ServiceImpl` / `LambdaQueryWrapper` / `LambdaUpdateWrapper`）、JeecgBoot（`Result` / `JeecgBootException` / `@AutoLog` / `@RequiresPermissions`）。

## Global Constraints

- 模块：`sgai-module-bems-biz`，根包 `org.jeecg.modules.bems`，主数据子包 `mdm`。
- `DeviceModel` 继承 `BaseEntity`（公共字段 `id`/`createBy`/`createTime`/`updateBy`/`updateTime`/`sysOrgCode`）。
- `isDefault` 字段类型统一用 `Integer`，取值 `0` / `1`，Java 字段 `isDefault` ↔ 数据库列 `is_default`（MyBatis-Plus 默认下划线驼峰映射，无需额外注解）。
- 不主动新增测试依赖：本项目无测试基础设施（biz pom 无 test 依赖、无测试配置），验证采用**手动接口验证**（Swagger / Postman），不写 JUnit。
- 不主动运行编译/打包/部署命令；每步改动后由用户按需验证。
- 代码风格贴合现有 `DeviceModelServiceImpl`（业务校验直接在 save/updateById 内用 `LambdaQueryWrapper` 完成）。

## File Structure

| 文件 | 责任 | 改动类型 |
|---|---|---|
| `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/entity/DeviceModel.java` | 实体，新增 `isDefault` 字段 | Modify |
| `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/service/IDeviceModelService.java` | Service 接口，新增 `queryDefaultByCategoryId` 签名 | Modify |
| `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/service/impl/DeviceModelServiceImpl.java` | 互斥逻辑 + 查询实现 | Modify |
| `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/controller/DeviceModelController.java` | 新增 `queryDefaultByCategoryId` 接口 | Modify |
| 数据库 `device_model` 表 | 新增 `is_default` 列 | DDL（手动执行） |

---

### Task 1: 实体新增 isDefault 字段 + DDL

**Files:**
- Modify: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/entity/DeviceModel.java`
- DDL: 数据库 `device_model` 表

**Interfaces:**
- Produces: `DeviceModel.isDefault`（`Integer`，getter `getIsDefault()`），供 Task 2 / Task 3 使用。

- [ ] **Step 1: 执行 DDL，新增列**

在数据库执行（由用户在目标库执行）：

```sql
ALTER TABLE `device_model`
ADD COLUMN `is_default` tinyint(1) DEFAULT 0 COMMENT '是否为默认模板（0=否，1=是）';
```

存量行自动补 `0`（非默认），符合「无默认模板」初始状态。

- [ ] **Step 2: 实体新增 isDefault 字段**

把 `DeviceModel.java` 的类体从：

```java
    /**
     * 设备类别id
     */
    private Long categoryId;
}
```

改为：

```java
    /**
     * 设备类别id
     */
    private Long categoryId;

    /**
     * 是否为默认模板（0=否，1=是）
     */
    private Integer isDefault;
}
```

- [ ] **Step 3: 提交**

```bash
git add sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/entity/DeviceModel.java
git commit -m "feat(deviceModel): 实体新增 isDefault 字段"
```

---

### Task 2: Service 层互斥逻辑

**Files:**
- Modify: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/service/impl/DeviceModelServiceImpl.java`

**Interfaces:**
- Consumes: `DeviceModel.isDefault`（Task 1 产出）。
- Produces: `save()` / `updateById()` 在 `isDefault=1` 时自动保证同类别唯一默认。

- [ ] **Step 1: 新增 import**

在 `DeviceModelServiceImpl.java` 顶部 import 区，现有 `LambdaQueryWrapper` import 下方新增：

```java
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
```

- [ ] **Step 2: 新增私有方法 handleDefaultSwitch**

在 `DeviceModelServiceImpl` 类体内、`save()` 方法之前新增：

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

- [ ] **Step 3: 改 save()，新增模板设默认时触发互斥**

把现有 `save()` 从：

```java
    @Override
    public boolean save(DeviceModel entity) {
        // 校验名称是否存在
        if(baseMapper.selectCount(new LambdaQueryWrapper<DeviceModel>().eq(DeviceModel::getModelName, entity.getModelName()) ) > 0){
            throw new JeecgBootException("模型名称已存在");
        }
        return super.save(entity);
    }
```

改为：

```java
    @Override
    public boolean save(DeviceModel entity) {
        // 校验名称是否存在
        if(baseMapper.selectCount(new LambdaQueryWrapper<DeviceModel>().eq(DeviceModel::getModelName, entity.getModelName()) ) > 0){
            throw new JeecgBootException("模型名称已存在");
        }
        // 如果设为默认，先取消同类别下其他默认模板
        if (Integer.valueOf(1).equals(entity.getIsDefault())) {
            handleDefaultSwitch(entity.getCategoryId(), null);
        }
        return super.save(entity);
    }
```

- [ ] **Step 4: 改 updateById()，编辑模板设默认时触发互斥（含 categoryId 兜底）**

把现有 `updateById()` 从：

```java
    @Override
    public boolean updateById(DeviceModel entity) {
        // 校验名称是否存在
        if(baseMapper.selectCount(new LambdaQueryWrapper<DeviceModel>().eq(DeviceModel::getModelName, entity.getModelName()).ne(DeviceModel::getId, entity.getId())) > 0){
            throw new JeecgBootException("模型名称已存在");
        }
        return super.updateById(entity);
    }
```

改为：

```java
    @Override
    public boolean updateById(DeviceModel entity) {
        // 校验名称是否存在
        if(baseMapper.selectCount(new LambdaQueryWrapper<DeviceModel>().eq(DeviceModel::getModelName, entity.getModelName()).ne(DeviceModel::getId, entity.getId())) > 0){
            throw new JeecgBootException("模型名称已存在");
        }
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
        return super.updateById(entity);
    }
```

- [ ] **Step 5: 提交**

```bash
git add sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/service/impl/DeviceModelServiceImpl.java
git commit -m "feat(deviceModel): save/updateById 增加默认模板互斥切换"
```

- [ ] **Step 6: 手动接口验证互斥逻辑（由用户执行）**

启动服务后通过 Swagger/Postman 验证（路径前缀 `/bems/deviceModel`）：

1. **add 设默认**：`POST /add`，body `{ "modelName": "TPL-A", "categoryId": <某类别id>, "isDefault": 1 }` → 返回「添加成功」。
2. **add 同类别再设默认**：`POST /add`，body `{ "modelName": "TPL-B", "categoryId": <同一类别id>, "isDefault": 1 }` → 成功；查库确认 TPL-A 的 `is_default` 已被置 `0`，仅 TPL-B 为 `1`。
3. **add 无类别设默认**：`POST /add`，body `{ "modelName": "TPL-C", "isDefault": 1 }` → 报错「请先选择设备类别」。
4. **edit 设默认（带 categoryId）**：`POST /edit`，body `{ "id": <TPL-A id>, "categoryId": <同一类别id>, "isDefault": 1 }` → 成功；查库确认 TPL-B 被置 `0`，TPL-A 为 `1`。
5. **edit 设默认（不带 categoryId）**：`POST /edit`，body `{ "id": <TPL-A id>, "isDefault": 1 }` → 成功；验证用库里 categoryId 兜底生效，其他默认被取消。
6. **edit 取消默认**：`POST /edit`，body `{ "id": <TPL-A id>, "isDefault": 0 }` → 成功；该类别此时无默认模板（允许）。

---

### Task 3: 新增按类别查询默认模板接口

**Files:**
- Modify: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/service/IDeviceModelService.java`
- Modify: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/service/impl/DeviceModelServiceImpl.java`
- Modify: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/controller/DeviceModelController.java`

**Interfaces:**
- Consumes: `DeviceModel.isDefault`（Task 1）、Controller 现有依赖注入风格（`@AllArgsConstructor` + `final IDeviceModelService service`）。
- Produces: `IDeviceModelService.queryDefaultByCategoryId(Long categoryId): DeviceModel`、`GET /bems/deviceModel/queryDefaultByCategoryId`。

- [ ] **Step 1: Service 接口新增方法签名**

在 `IDeviceModelService.java` 接口体内，现有方法之后新增：

```java
    DeviceModel queryDefaultByCategoryId(Long categoryId);
```

完整接口应为：

```java
public interface IDeviceModelService extends IService<DeviceModel> {

    IPage<DeviceModel> queryPage(DeviceModel params);
    List<DeviceModel> queryByCategoryId(Long categoryId);
    DeviceModel queryDefaultByCategoryId(Long categoryId);
}
```

- [ ] **Step 2: ServiceImpl 新增实现**

在 `DeviceModelServiceImpl` 类体内、`queryByCategoryId` 方法之后新增：

```java
    @Override
    public DeviceModel queryDefaultByCategoryId(Long categoryId) {
        return getOne(new LambdaQueryWrapper<DeviceModel>()
                .eq(DeviceModel::getCategoryId, categoryId)
                .eq(DeviceModel::getIsDefault, 1)
                .last("LIMIT 1"));
    }
```

- [ ] **Step 3: Controller 新增接口**

在 `DeviceModelController.java` 类体内、`queryByCategoryId` 方法之后新增（沿用现有风格：`@ApiOperation`、`@GetMapping`、返回 `Result`）：

```java
    @ApiOperation(value = "设备模型-根据类别id查询默认模板", notes = "设备模型-根据类别id查询默认模板")
    @GetMapping("/queryDefaultByCategoryId")
    public Result<DeviceModel> queryDefaultByCategoryId(@RequestParam(name = "categoryId") Long categoryId){
        return Result.ok(service.queryDefaultByCategoryId(categoryId));
    }
```

- [ ] **Step 4: 提交**

```bash
git add sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/service/IDeviceModelService.java sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/service/impl/DeviceModelServiceImpl.java sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/controller/DeviceModelController.java
git commit -m "feat(deviceModel): 新增按类别查询默认模板接口"
```

- [ ] **Step 5: 手动接口验证（由用户执行）**

启动服务后验证：

1. **有默认模板**：`GET /bems/deviceModel/queryDefaultByCategoryId?categoryId=<有默认的类别id>` → 返回 `result` 为该默认模板对象，`isDefault=1`。
2. **无默认模板**：`GET /bems/deviceModel/queryDefaultByCategoryId?categoryId=<无默认的类别id>` → 返回 `result` 为 `null`。

---

## Self-Review

**1. Spec coverage：**
- 数据模型（`is_default` 列 + `isDefault` 字段）→ Task 1 ✓
- 规则 1 互斥自动切换 → Task 2（save/updateById + handleDefaultSwitch）✓
- 规则 2 取消默认直接置 0 → Task 2（updateById 中 `isDefault=0` 不进 if 分支，直接走 super.updateById）✓
- 规则 3 categoryId 兜底 → Task 2 Step 4 ✓
- 规则 4 删除不报错 → 现有 delete/removeById 不涉及 isDefault，天然满足，无需改动 ✓
- 规则 5 无默认合法性 → 查询返回 null 体现，Task 3 ✓
- 新增查询接口 → Task 3 ✓
- DDL → Task 1 Step 1 ✓

**2. Placeholder scan：** 无 TBD/TODO；所有代码步骤均含完整代码块；验证步骤含具体请求与预期；git add 路径已核对正确。

**3. Type consistency：** `isDefault` 全程 `Integer`、`getIsDefault()`；`handleDefaultSwitch(Long categoryId, Long excludeId)`；`queryDefaultByCategoryId(Long categoryId): DeviceModel` 在 Service 接口、实现、Controller 三处签名一致；`is_default` 列名与字段映射一致。
