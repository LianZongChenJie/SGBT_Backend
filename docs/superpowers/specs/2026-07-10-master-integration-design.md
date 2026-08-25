# bems ↔ master 主数据对接设计（类别 / 空间 / 设备 的接收与推送）

- 日期：2026-07-10
- 模块：sgai-module-bems
- 对接对象：sgai-module-master（主数据管理，主源）
- 依据：`sgai-module-master/docs/integration-guide.md`

## 1. 目标

在 bems 侧实现与 master 的主数据对接：

- **接收**（master → bems）：bems 提供 HTTP 端点，接收 master 推送的**类别、空间、设备**，按 master uuid 落库到 bems 本地表。
- **推送**（bems → master）：bems 设备增删改时，把**设备**异步推送给 master（类别 / 空间按文档规定只出不进，bems 不反推）。

## 2. 关键决策

| 决策点 | 结论 |
|---|---|
| id 冲突 | 三张表各加 `master_id varchar(32)` 映射字段，本地 Long 自增主键不变；接收按 `master_id` upsert，推送回填 uuid |
| 推送边界 | 按文档：类别 / 空间只接收，设备双向 |
| 字段填充 | `deviceCode = deviceName`、`modelId = null`；`type / deviceType` 由接收端点固定 |
| 类型分流 | bems 提供 2 个端点（仪表 / 设备）；body 的 type(DEVICE/CATEGORY/SPACE) 区分落库对象，端点决定 device_type/type 取 1(仪表)/2(设备) |
| 推送模型 | 异步线程池 + `integration_push_log` 日志表，失败不自动重推 |
| 类别 / 空间全称 | **忽略** master 报文 `fullName`，按 bems 现有全称算法本地重建 |

## 3. 总体架构与端点结构

新建 `org.jeecg.modules.bems.integration` 子包，承担「接收端点」与「推送客户端」两类职责，通过 `master_id` 完成 **master uuid ↔ bems Long** 双向转换。

### 3.1 接收端点（master → bems，2 个，全部 `@IgnoreAuth` + token 校验）

| 端点 | 端点值 | 适用 |
|---|---|---|
| `POST /integration/receive/meter` | 仪表(1) | 仪表类别 / 仪表设备 |
| `POST /integration/receive/equipment` | 设备(2) | 设备类别 / 设备 |

- **两个端点处理逻辑完全相同**，唯一差异是落库赋值：类别 `type` 与设备 `deviceType` 在 meter 端点取 `1`、equipment 端点取 `2`。
- body 的 **`type` 字段（DEVICE / CATEGORY / SPACE）决定落库表**：DEVICE→device、CATEGORY→equipment_category、SPACE→space。
- 空间（SPACE）无仪表/设备之分，经任一端点接收均可，落 space 表时不赋 device_type。
- `op` 认 `UPSERT / DELETE / SNAPSHOT`，`SNAPSHOT` 当 `UPSERT` 处理。
- master 侧按对接系统配两条（仪表 / 设备）分流调用；类别与设备按其类型走对应端点，空间走任一端点。

### 3.2 推送（bems → master，仅设备）

- bems 设备增删改后，**异步线程池**调用 master 的 `POST {master-base}/master/integration/receive`，请求头 `X-Integration-Token` + `X-Source: sgai-bems`，body 为 `ReceivePayload`（`type=DEVICE`）。
- 每次推送写一条 `integration_push_log`，失败不自动重推。

### 3.3 双向数据流（id 转换是核心）

```
【接收 master→bems】 master(uuid) ──按 master_id 查本地──▶ bems(Long)：存在则更新 / 不存在则自增新增并记 master_id
【推送 bems→master】 bems(Long) ──查该行 master_id──▶ master(uuid)；本地 master_id 为空时（bems 自建设备）推送前懒生成 uuid 回填
```

## 4. 数据模型

### 4.1 三张表新增 `master_id` 字段

| 表 / 实体 | 新增字段 | 列定义 | 索引 |
|---|---|---|---|
| `device` / `Device` | `masterId` | `master_id varchar(32)` | 唯一索引 `uk_device_master_id` |
| `equipment_category` / `EquipmentCategory` | `masterId` | `master_id varchar(32)` | 唯一索引 `uk_category_master_id` |
| `space` / `Space` | `masterId` | `master_id varchar(32)` | 唯一索引 `uk_space_master_id` |

- 字段加在各实体上，**不动 BaseEntity**。
- 可空：bems 自建、尚未推送的数据为空，首次推送前懒生成 uuid 回填。
- 唯一：有值必须唯一（多个 NULL 不冲突）。
- 三张表的 master_id 各自独立命名空间，各建唯一索引。
- DDL 在实现阶段提供，**不自动执行迁移**。

### 4.2 配置项（application.yml，部署时手动迁 Nacos）

```yaml
bems:
  integration:
    enabled: true                  # 推送总开关（接收端点不受此开关影响）
    token: ${INTEGRATION_TOKEN}    # 与 master 约定的共享令牌（接收校验 + 推送请求头共用）
    source: sgai-bems              # 推送时的 source / X-Source 值
    master:
      base-url: http://10.x.x.x   # master 对外地址
      receive-path: /master/integration/receive
    push:
      timeout-seconds: 5           # 与文档 5s 超时一致
```

对应配置类 `IntegrationProperties`（`@ConfigurationProperties("bems.integration")`）。

### 4.3 推送日志表 `integration_push_log`（新建）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | bigint PK | 自增 |
| `batch_id` | varchar(64) | 推送批次 uuid（每次推送新生成） |
| `op` | varchar(16) | UPSERT / DELETE / SNAPSHOT |
| `type` | varchar(16) | 目前固定 DEVICE |
| `data_count` | int | 推送条数 |
| `data_ids` | varchar(2000) | 推送的设备 master_id 列表（便于追溯，不存全量报文） |
| `status` | varchar(16) | SUCCESS / FAIL |
| `http_status` | int | master 返回的 HTTP 状态码 |
| `response_msg` | varchar(500) | master 返回 message 或异常信息 |
| `create_time` | datetime | 推送时间 |

接收方向不单独建日志表（接收响应已返回 `accepted / rejected`）。日志表只记录、不参与去重（去重由 master 侧按 id 幂等保证）。

## 5. 接收端点落库逻辑

2 个端点共享**完全相同**的处理骨架：读取 body.type（DEVICE / CATEGORY / SPACE）决定落库表与 data 元素类型，端点 URL（meter=1 / equipment=2）仅决定 device_type / type 赋值。统一**逐条尽力而为**，单条失败不影响其他条目。

### 5.1 type=CATEGORY 类别落库 `CategoryPushItem{id, name, fullName, pid}` → equipment_category

1. **pid 转换**：master pid 为 uuid 或 `"0"`（根）。
   - `pid == "0"` → 本地 `pid = ROOT_PID_VALUE`
   - `pid == uuid` → 按 master_id 查 equipment_category 得本地 Long；查不到 → reject（`父类别不存在`）
2. **忽略** master 报文 `fullName`。
3. **按 master_id upsert**：查到本地行 → 更新 `categoryName=name`、本地 pid、type(端点)；未查到 → insert 新行，写 `master_id=id`、`type(端点)`、`hasChild=0`。
4. **全称本地重建**：fullName / fullId / hasChild / 子节点级联一律按 bems 现有算法生成（见 5.4）。

### 5.2 type=SPACE 空间落库 `SpacePushItem{id, name, fullName, pid}` → space

逻辑同类别，落 space 表，`spaceName=name`，无 type。全称本地重建。

### 5.3 type=DEVICE 设备落库 `DevicePushItem{id, name, categoryId, spaceId, remark}` → device

1. **categoryId 转换**：master uuid → 按 master_id 查 equipment_category 得本地 Long；查不到 → reject（`类别不存在`）。
2. **spaceId 转换**：master uuid → 按 master_id 查 space 得本地 Long；查不到 → reject（`空间不存在`）。
3. **名称冲突**（设备名全局唯一）：upsert 前按 `deviceName` 查本地 device，若存在且其 `master_id != 当前条目 id` → reject（`设备名称冲突`）。
4. **按 master_id upsert**：查到 → 更新 `deviceName=name`、`deviceCode=name`、categoryId、spaceId、remark、`deviceType(端点)`；未查到 → insert，写 `master_id=id`、`deviceCode=name`、`deviceType(端点)`、`modelId=null`。

### 5.4 全称本地重建（复用现有算法）

bems 现有全称逻辑（`EquipmentCategoryServiceImpl` / `SpaceServiceImpl`）：

- **fullName**：根到节点各级名称**直接拼接（无分隔符）**，如父「建筑」+ 子「电气」→ `建筑电气`。
- **fullId**：根到节点的本地 Long id 用 `connector` 常量分隔。
- 根 `pid = ROOT_PID_VALUE`；新增 / 更新时维护父节点 `hasChild`、级联更新子节点（mapper `updateFullInfo`）、校验同级重名。

为避免与现有 `addEquipmentCategory` / `addSpace`（重名抛异常）和 `updateEquipmentCategory` / `updateSpace`（会主动 `setType(null)`）的副作用冲突，在 `IEquipmentCategoryService` / `ISpaceService` **新增 `upsertByMasterId(...)`**：按 master_id 判存，复用现有全称 / 级联算法，但适配接收语义——重名转 reject 而非抛异常、正确处理 master_id 与端点 type。

### 5.5 op 处理

- `UPSERT / SNAPSHOT` → 5.1–5.3 的 upsert（SNAPSHOT 当 UPSERT）。
- `DELETE` → 按 master_id 物理删除该行；本地不存在视为成功（幂等）。不级联删子节点（master 会逐条推送子节点删除）。

### 5.6 鉴权与响应

- 鉴权：请求头 `X-Integration-Token == 配置 token`，否则 **HTTP 401**（文档 §4.3 格式）。端点用 `@IgnoreAuth` 免平台登录，token 由 `TokenAuthInterceptor` 针对 `/integration/receive/**` 统一校验。
- 成功响应 **HTTP 200**（复用 jeecg `Result`）：

```jsonc
{ "success": true, "message": "操作成功", "code": 200,
  "result": { "batchId": "...", "accepted": 2,
    "rejected": [ { "id": "...", "reason": "类别不存在" } ] } }
```

- `reason` 取值：`父类别不存在` / `类别不存在` / `空间不存在` / `设备名称冲突`。
- 部分丢弃仍返回 200、`success=true`、`rejected` 非空（与文档 §4.5 一致）。

### 5.7 边界

- **引用顺序**：若 master 设备先于其类别 / 空间到达 → 该设备 reject（`类别不存在 / 空间不存在`）。可接受，依赖 master 保证「空间 → 类别 → 设备」顺序（SNAPSHOT 时本就分三次有序推送）。
- **幂等**：相同 batchId / 内容重复推送无副作用（按 master_id 幂等 upsert / delete）。

## 6. bems → master 推送细节

### 6.1 触发点与时机

- **触发点**：`DeviceServiceImpl` 的设备新增 / 编辑 / 删除（含批量导入、批量删除）成功后。
- **时机**：**事务提交后异步触发**（确保设备与 master_id 已落库可见）。用 `TransactionSynchronizationManager` 注册 `afterCommit` 回调，提交到专用线程池，避免阻塞主事务。
- **op 映射**：新增 / 编辑 → `UPSERT`；删除 → `DELETE`（删除前先取设备快照，带上原 categoryId / spaceId 供 master 过滤）。

### 6.2 字段转换（bems Device → DevicePushItem）

| DevicePushItem 字段 | 来源 |
|---|---|
| `id` | `device.master_id`；为空时推送前懒生成 uuid 回填本地再推 |
| `name` | `device.deviceName` |
| `categoryId` | 查 `equipment_category.master_id` where `id = device.categoryId`（本地 Long → master uuid） |
| `spaceId` | 查 `space.master_id` where `id = device.spaceId` |
| `remark` | `device.remark` |

### 6.3 批量聚合

批量导入 / 批量删除合并为**一个 batchId、一次推送**，避免报文风暴（文档 §5.4）。每次推送生成新 batchId。

### 6.4 推送客户端

新建 `RestTemplate` Bean（连接 / 读取超时各 5s），POST 到 `{master-base}{receive-path}`，请求头 `X-Integration-Token` + `X-Source: sgai-bems`，body 为 `ReceivePayload{source, type=DEVICE, op, batchId, data}`。

### 6.5 日志与失败处理

- 每次推送写一条 `integration_push_log`：推送前 insert（batchId/op/data_count/data_ids），返回后 update `status / http_status / response_msg`。
- master 返回 2xx → `SUCCESS`；非 2xx / 超时 / 连接异常 → `FAIL`。**失败不自动重推**，留待运维手动全量补齐。

### 6.6 边界（固有限制）

- bems 自建设备若挂在 **bems 自建类别**下（该类别无 master 认识的 master_id），推给 master 时 categoryId 是 master 不认识的 uuid → master 会 reject（`类别不存在`）。这是文档模型决定的（类别为 master 主源）。推送客户端照推、记日志，不阻塞。
- `spaceId` 同理。

## 7. 鉴权收口

- 5 个接收端点复用现有 `@IgnoreAuth`（`DeviceApiController` 等已在用），无需改 Shiro 配置。
- 新增 `TokenAuthInterceptor` 针对 `/integration/receive/**` 校验 `X-Integration-Token == 配置 token`，不等返回 401。
- 推送方向无需鉴权拦截。

## 8. 文件清单（最小改动集）

**A. 新建（`org.jeecg.modules.bems.integration` 包）**

| 类 | 职责 |
|---|---|
| `IntegrationController` | 2 个接收端点 meter/equipment（`@IgnoreAuth`），内部按 body.type 分流 |
| `IntegrationReceiveService(Impl)` | 接收落库（id 转换、校验、调 mdm service upsert） |
| `IntegrationPushService(Impl)` | 推送（字段转换、批量聚合、写日志） |
| `IntegrationPushClient` | RestTemplate 封装（POST master、5s 超时） |
| `IntegrationPushLog` + `Mapper` + `I(Impl)Service` | 推送日志表 CRUD |
| `IntegrationProperties` | `@ConfigurationProperties("bems.integration")` |
| `IntegrationConfig` | RestTemplate Bean + 专用线程池 Bean + 注册拦截器 |
| `TokenAuthInterceptor` | `/integration/receive/**` token 校验 |
| DTO：`IntegrationPayload`、`DevicePushItem`、`CategoryPushItem`、`SpacePushItem`、`ReceiveResult`、`RejectedItem` | 报文结构（与文档一致） |

**B. 修改现有**

| 文件 | 改动 |
|---|---|
| `Device.java` / `EquipmentCategory.java` / `Space.java` | 各加 `masterId` 字段 |
| `IEquipmentCategoryService(Impl)` / `ISpaceService(Impl)` | 新增 `upsertByMasterId(...)`（复用现有全称算法） |
| `DeviceServiceImpl` | 设备增删改（含批量）事务 `afterCommit` 触发推送 |
| `application.yml` | 加 `bems.integration.*` 配置 |

**C. DDL（实现阶段提供 SQL，不自动执行迁移）**

- `device` / `equipment_category` / `space` 各加 `master_id varchar(32)` + 唯一索引。
- 新建 `integration_push_log` 表（字段见 4.3）。

## 9. 测试策略

- 依据项目现状：**父 pom 硬编码 `skipTests`，mvn 跑不了后端测试，用 IDE 跑**。
- **单元测试（IDE）**：`IntegrationReceiveServiceImpl` 的 pid 转换、名称冲突判定、引用校验、upsert 判存；`IntegrationPushServiceImpl` 的字段转换、master_id 懒生成。
- **Mapper 测试**：参照项目现有 `CostCenterDataHourMapperTest` 模式，验证 `upsertByMasterId` 的全称 / 级联。
- **端到端手验**：用 curl 模拟 master 推送报文打 2 个端点（meter / equipment），覆盖 body.type = DEVICE / CATEGORY / SPACE 三类，核对落库与 fullName 本地重建；触发 bems 设备增删改，核对 `integration_push_log` 与对 master 的调用。

## 10. 不在本期范围

- 接收方向日志表（如需追溯再补）。
- 类别 / 空间反推 master（文档不支持）。
- 推送失败自动重推（文档约定不重推，靠运维手动全量补齐）。
- 历史数据批量迁移 / master_id 回填（一次性运维操作，不在代码范围）。
