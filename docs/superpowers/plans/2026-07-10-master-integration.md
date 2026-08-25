# bems ↔ master 主数据对接 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 bems 侧实现与 sgai-module-master 的主数据对接——2 个 HTTP 接收端点（按 body.type 分流收 类别/空间/设备）+ 设备增删改异步推送 master。

**Architecture:** 三张主数据表加 `master_id varchar(32)` 映射字段（本地 Long 主键不变）。接收端按 master_id upsert（类别/空间全称用 bems 现有算法本地重建）；推送经事务 `afterCommit` 异步线程池调用 master，写 `integration_push_log`，失败不重推。

**Tech Stack:** Spring Boot + Spring Cloud (JeecgBoot 3.7.0)、MyBatis-Plus、Apache Shiro、Lombok、RestTemplate、Hutool。

## Global Constraints

- **本次不执行任何 git 命令**（用户要求）；每个任务以「编译/手验」为检查点，不做 commit。
- **测试一律在 IDE 中运行**：父 pom 硬编码 `<skipTests>true</skipTests>`，`mvn test` 不可用。纯逻辑用 JUnit（IDE 跑），DB/Web 逻辑用启动应用 + curl 手验。
- **不主动执行数据库迁移**：DDL 以 SQL 文本形式给出，由用户/运维手动执行。
- 实体约定：`Device` / `EquipmentCategory` 继承 `BaseEntity`（id 为 Long 自增）；`Space` 独立（同 Long 自增 id）。均用 Lombok `@Data`。
- 树形常量（`IEquipmentCategoryService` / `ISpaceService`）：`ROOT_PID_VALUE = 0L`、`connector = "-"`、`HASCHILD = "1"`、`NOCHILD = "0"`。
- Mapper 级联方法：`updateFullInfo(oldFullId, fullName, fullId)`（按 fullId 前缀更新子节点全称）、`updateTreeNodeStatus(id, status)`（更新 hasChild）。
- 接收端点鉴权：`@IgnoreAuth` 免登录 + 自定义 `TokenAuthInterceptor` 校验 `X-Integration-Token`。
- 包根：`org.jeecg.modules.bems.integration`。
- 设备类型常量：`Device.DEVICE_TYPE_MEASURING="1"`（仪表）、`DEVICE_TYPE_EQUIPMENT="2"`（设备）；类别 `EquipmentCategory.TYPE_MEASURING="1"` / `TYPE_EQUIPMENT="2"`。

---

## 文件结构

**新建（`org.jeecg.modules.bems.integration` 包）**
- `config/IntegrationProperties` — yml 绑定
- `config/IntegrationConfig` — RestTemplate Bean + 推送线程池 Bean
- `config/TokenAuthInterceptor` — `/integration/receive/**` token 校验
- `dto/IntegrationPayload` — 通用报文（source/type/op/batchId/data）
- `dto/DevicePushItem`、`dto/CategoryPushItem`、`dto/SpacePushItem` — data 元素
- `dto/ReceiveResult`、`dto/RejectedItem` — 接收响应
- `entity/IntegrationPushLog` + `mapper/IntegrationPushLogMapper` + `service/IIntegrationPushLogService` + `service/impl/IntegrationPushLogServiceImpl`
- `service/IntegrationReceiveService` + `impl/IntegrationReceiveServiceImpl` — 接收落库
- `client/IntegrationPushClient` — RestTemplate 推送
- `service/IntegrationPushService` + `impl/IntegrationPushServiceImpl` — 推送编排
- `controller/IntegrationController` — 2 个接收端点

**修改**
- `mdm/entity/Device.java` / `EquipmentCategory.java` / `Space.java` — 各加 `masterId`
- `mdm/service/IEquipmentCategoryService.java` + `impl/EquipmentCategoryServiceImpl.java` — 新增 `upsertByMasterId`
- `mdm/service/ISpaceService.java` + `impl/SpaceServiceImpl.java` — 新增 `upsertByMasterId`
- `mdm/service/impl/DeviceServiceImpl.java` — add/update/remove 后触发推送
- `sgai-module-bems-start/src/main/resources/application.yml` — 加 `bems.integration.*`

---

## Task 1: DDL 与 master_id 字段

**Files:**
- Modify: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/entity/Device.java`
- Modify: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/entity/EquipmentCategory.java`
- Modify: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/mdm/entity/Space.java`
- Create: `docs/superpowers/sql/2026-07-10-master-integration.sql`（DDL，供手动执行）

**Interfaces:**
- Produces: 三实体新增字段 `private String masterId;`（列名 `master_id`）；后续任务依赖此字段做 upsert / id 转换。

- [ ] **Step 1: 三实体加 masterId 字段**

在 `Device.java` 字段区追加（紧挨 remark 之后即可）：
```java
@ApiModelProperty(value = "主数据平台 uuid")
private String masterId;
```
在 `EquipmentCategory.java` 字段区追加同样字段。`Space.java` 在 `fullId` 字段之后追加同样字段（注意 Space 不继承 BaseEntity，但仍用 `@ApiModelProperty`）。

- [ ] **Step 2: 写 DDL SQL 文件**

创建 `docs/superpowers/sql/2026-07-10-master-integration.sql`：
```sql
-- 三张主数据表加 master_id（可空 + 唯一索引）
ALTER TABLE device ADD COLUMN master_id VARCHAR(32) NULL COMMENT '主数据平台 uuid';
ALTER TABLE device ADD UNIQUE INDEX uk_device_master_id (master_id);

ALTER TABLE equipment_category ADD COLUMN master_id VARCHAR(32) NULL COMMENT '主数据平台 uuid';
ALTER TABLE equipment_category ADD UNIQUE INDEX uk_category_master_id (master_id);

ALTER TABLE space ADD COLUMN master_id VARCHAR(32) NULL COMMENT '主数据平台 uuid';
ALTER TABLE space ADD UNIQUE INDEX uk_space_master_id (master_id);

-- 推送日志表
CREATE TABLE integration_push_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  batch_id    VARCHAR(64)  NOT NULL COMMENT '推送批次 uuid',
  op          VARCHAR(16)  NULL COMMENT 'UPSERT/DELETE/SNAPSHOT',
  type        VARCHAR(16)  NULL COMMENT '目前固定 DEVICE',
  data_count  INT          NULL COMMENT '推送条数',
  data_ids    VARCHAR(2000) NULL COMMENT '推送设备 master_id 列表',
  status      VARCHAR(16)  NULL COMMENT 'SUCCESS/FAIL',
  http_status INT          NULL COMMENT 'master 返回 HTTP 状态码',
  response_msg VARCHAR(500) NULL COMMENT 'master 返回 message 或异常',
  create_time DATETIME     NULL COMMENT '推送时间',
  PRIMARY KEY (id),
  KEY idx_push_batch (batch_id)
) COMMENT='主数据推送日志';
```
> 不自动执行；交运维手动执行。

- [ ] **Step 3: 编译验证**

IDE 中编译 `sgai-module-bems-biz`，确认三实体改动无误（无运行要求）。

---

## Task 2: 配置与基础设施

**Files:**
- Create: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/integration/config/IntegrationProperties.java`
- Create: `sgai-module-bems-biz/src/main/java/org/jeecg/modules/bems/integration/config/IntegrationConfig.java`
- Modify: `sgai-module-bems-start/src/main/resources/application.yml`

**Interfaces:**
- Produces: `IntegrationProperties`（`@ConfigurationProperties("bems.integration")`，字段 `enabled/token/source/master.baseUrl/master.receivePath/push.timeoutSeconds`）；`IntegrationConfig.integrationRestTemplate()` 与 `integrationPushExecutor()` 两个 Bean。

- [ ] **Step 1: IntegrationProperties**

```java
package org.jeecg.modules.bems.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bems.integration")
public class IntegrationProperties {
    private boolean enabled = false;
    private String token;
    private String source = "sgai-bems";
    private Master master = new Master();
    private Push push = new Push();

    @Data
    public static class Master {
        private String baseUrl;
        private String receivePath = "/master/integration/receive";
    }
    @Data
    public static class Push {
        private int timeoutSeconds = 5;
    }
}
```

- [ ] **Step 2: IntegrationConfig（RestTemplate + 线程池）**

```java
package org.jeecg.modules.bems.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
public class IntegrationConfig {

    @Bean("integrationRestTemplate")
    public RestTemplate integrationRestTemplate(IntegrationProperties props) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        int ms = props.getPush().getTimeoutSeconds() * 1000;
        f.setConnectTimeout(ms);
        f.setReadTimeout(ms);
        return new RestTemplate(f);
    }

    @Bean("integrationPushExecutor")
    public Executor integrationPushExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("integration-push-");
        ex.initialize();
        return ex;
    }
}
```

- [ ] **Step 3: application.yml 追加配置**

在 `sgai-module-bems-start/src/main/resources/application.yml` 顶层追加（具体地址部署时改）：
```yaml
bems:
  integration:
    enabled: true
    token: PLEASE_SET_TOKEN
    source: sgai-bems
    master:
      base-url: http://10.x.x.x:xxxx
      receive-path: /master/integration/receive
    push:
      timeout-seconds: 5
```

- [ ] **Step 4: 启动验证**

IDE 启动 `JeecgBemsCloudApplication`，确认无 Bean 绑定错误、应用正常启动。

---

## Task 3: 报文 DTO

**Files:**
- Create: `integration/dto/IntegrationPayload.java`
- Create: `integration/dto/DevicePushItem.java`
- Create: `integration/dto/CategoryPushItem.java`
- Create: `integration/dto/SpacePushItem.java`
- Create: `integration/dto/ReceiveResult.java`
- Create: `integration/dto/RejectedItem.java`

**Interfaces:**
- Produces: 报文模型，`IntegrationReceiveService`（Task 6）与 `IntegrationPushService`（Task 8）共用。
- `IntegrationPayload<T>`：`source / systemCode / type / op / batchId / data(List<T>)`，`data` 用泛型以承接不同 item；接收时用 `@JsonAlias` 或 `List<Map>` 解析后按 type 再转。

- [ ] **Step 1: 6 个 DTO 类**

`IntegrationPayload.java`：
```java
package org.jeecg.modules.bems.integration.dto;

import lombok.Data;
import java.util.List;

@Data
public class IntegrationPayload<T> {
    private String source;
    private String systemCode;
    private String type;       // DEVICE / CATEGORY / SPACE
    private String op;         // UPSERT / DELETE / SNAPSHOT
    private String batchId;
    private List<T> data;
}
```

`DevicePushItem.java`：
```java
package org.jeecg.modules.bems.integration.dto;

import lombok.Data;

@Data
public class DevicePushItem {
    private String id;
    private String name;
    private String categoryId;
    private String spaceId;
    private String remark;
}
```

`CategoryPushItem.java`：
```java
package org.jeecg.modules.bems.integration.dto;

import lombok.Data;

@Data
public class CategoryPushItem {
    private String id;
    private String name;
    private String fullName;   // bems 端忽略，仅接收
    private String pid;        // uuid 或 "0"
}
```

`SpacePushItem.java`：
```java
package org.jeecg.modules.bems.integration.dto;

import lombok.Data;

@Data
public class SpacePushItem {
    private String id;
    private String name;
    private String fullName;   // bems 端忽略
    private String pid;        // uuid 或 "0"
}
```

`RejectedItem.java`：
```java
package org.jeecg.modules.bems.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectedItem {
    private String id;
    private String reason;
}
```

`ReceiveResult.java`：
```java
package org.jeecg.modules.bems.integration.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReceiveResult {
    private String batchId;
    private int accepted;
    private List<RejectedItem> rejected = new ArrayList<>();

    public ReceiveResult(String batchId) { this.batchId = batchId; }
}
```

- [ ] **Step 2: 编译验证**

IDE 编译 integration.dto 包，确认无误。

---

## Task 4: 推送日志表实体 / Mapper / Service

**Files:**
- Create: `integration/entity/IntegrationPushLog.java`
- Create: `integration/mapper/IntegrationPushLogMapper.java`
- Create: `integration/service/IIntegrationPushLogService.java`
- Create: `integration/service/impl/IntegrationPushLogServiceImpl.java`

**Interfaces:**
- Produces: `IIntegrationPushLogService`（继承 `IService<IntegrationPushLog>`），供 Task 8 写日志。

- [ ] **Step 1: 实体**

```java
package org.jeecg.modules.bems.integration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("integration_push_log")
public class IntegrationPushLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchId;
    private String op;
    private String type;
    private Integer dataCount;
    private String dataIds;
    private String status;
    private Integer httpStatus;
    private String responseMsg;
    private Date createTime;
}
```

- [ ] **Step 2: Mapper**

```java
package org.jeecg.modules.bems.integration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.jeecg.modules.bems.integration.entity.IntegrationPushLog;

@Mapper
public interface IntegrationPushLogMapper extends BaseMapper<IntegrationPushLog> {
}
```

- [ ] **Step 3: Service 接口与实现**

`IIntegrationPushLogService.java`：
```java
package org.jeecg.modules.bems.integration.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.bems.integration.entity.IntegrationPushLog;

public interface IIntegrationPushLogService extends IService<IntegrationPushLog> {
}
```

`IntegrationPushLogServiceImpl.java`：
```java
package org.jeecg.modules.bems.integration.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.jeecg.modules.bems.integration.entity.IntegrationPushLog;
import org.jeecg.modules.bems.integration.mapper.IntegrationPushLogMapper;
import org.jeecg.modules.bems.integration.service.IIntegrationPushLogService;
import org.springframework.stereotype.Service;

@Service
public class IntegrationPushLogServiceImpl
        extends ServiceImpl<IntegrationPushLogMapper, IntegrationPushLog>
        implements IIntegrationPushLogService {
}
```

- [ ] **Step 4: 编译验证**

IDE 编译，确认无错（需先在 DB 执行 Task 1 的建表 SQL 后才能运行时使用，但编译不依赖表存在）。

---

## Task 5: 类别 / 空间 upsertByMasterId（复用全称算法）

**Files:**
- Modify: `mdm/service/IEquipmentCategoryService.java`
- Modify: `mdm/service/impl/EquipmentCategoryServiceImpl.java`
- Modify: `mdm/service/ISpaceService.java`
- Modify: `mdm/service/impl/SpaceServiceImpl.java`

**Interfaces:**
- Consumes: `ROOT_PID_VALUE / connector / HASCHILD / NOCHILD` 常量；mapper `updateFullInfo` / `updateTreeNodeStatus`。
- Produces: `upsertByMasterId(String masterId, String name, String masterPid, String type)`，返回 `UpsertResult{localId, ok, reason}`。供 Task 6 接收服务调用。设备落库不涉及全称，故仅类别/空间需要。

- [ ] **Step 1: 定义统一返回类型**

在 `integration/dto/` 新建 `UpsertResult.java`：
```java
package org.jeecg.modules.bems.integration.dto;

import lombok.Data;

@Data
public class UpsertResult {
    private Long localId;
    private boolean ok;
    private String reason;

    public static UpsertResult ok(Long id) { UpsertResult r = new UpsertResult(); r.ok = true; r.localId = id; return r; }
    public static UpsertResult fail(String reason) { UpsertResult r = new UpsertResult(); r.ok = false; r.reason = reason; return r; }
}
```

- [ ] **Step 2: IEquipmentCategoryService 增加方法签名**

在接口内追加：
```java
import org.jeecg.modules.bems.integration.dto.UpsertResult;
// ...
/**
 * 按 master_id upsert（对接接收用）；fullName/fullId 用本地算法重建。
 * @param masterId master uuid
 * @param name 类别名（categoryName）
 * @param masterPid master 的父 uuid，"0" 表示根
 * @param type 1仪表/2设备（由端点决定）
 */
UpsertResult upsertByMasterId(String masterId, String name, String masterPid, String type);
```

- [ ] **Step 3: EquipmentCategoryServiceImpl 实现 upsertByMasterId**

在类中新增方法（复用 `addEquipmentCategory` / `updateEquipmentCategory` 的全称算法，但避免其抛异常与 setType(null) 副作用）：
```java
@Override
public UpsertResult upsertByMasterId(String masterId, String name, String masterPid, String type) {
    // 1. 解析 pid（master uuid / "0" -> 本地 Long）
    Long localPid;
    if (masterPid == null || "0".equals(masterPid)) {
        localPid = IEquipmentCategoryService.ROOT_PID_VALUE;
    } else {
        EquipmentCategory parent = baseMapper.selectOne(
                new QueryWrapper<EquipmentCategory>().eq("master_id", masterPid));
        if (parent == null) {
            return UpsertResult.fail("父类别不存在");
        }
        localPid = parent.getId();
    }

    // 2. 查本地是否已存在（按 master_id）
    EquipmentCategory exist = baseMapper.selectOne(
            new QueryWrapper<EquipmentCategory>().eq("master_id", masterId));

    // 3. 算 fullName / fullId（本地算法：父 fullName + 自身 name；fullId：父 fullId + connector + 自身 pid）
    EquipmentCategory parent = localPid.equals(IEquipmentCategoryService.ROOT_PID_VALUE)
            ? null : baseMapper.selectById(localPid);
    String fullName = parent == null ? name : parent.getFullName() + name;
    String fullId = parent == null
            ? String.valueOf(localPid)
            : parent.getFullId() + IEquipmentCategoryService.connector + localPid;

    if (exist == null) {
        // 4a. 新增
        EquipmentCategory cat = new EquipmentCategory();
        cat.setMasterId(masterId);
        cat.setCategoryName(name);
        cat.setPid(localPid);
        cat.setType(type);
        cat.setHasChild(IEquipmentCategoryService.NOCHILD);
        cat.setFullName(fullName);
        cat.setFullId(fullId);
        baseMapper.insert(cat);
        // 维护父节点 hasChild
        if (parent != null && !IEquipmentCategoryService.HASCHILD.equals(parent.getHasChild())) {
            baseMapper.updateTreeNodeStatus(parent.getId(), IEquipmentCategoryService.HASCHILD);
        }
        return UpsertResult.ok(cat.getId());
    } else {
        // 4b. 更新（含 pid 变化时维护新旧父节点 hasChild + 级联子节点全称）
        Long oldPid = exist.getPid();
        boolean pidChanged = !localPid.equals(oldPid);
        if (pidChanged && oldPid != null && !oldPid.equals(IEquipmentCategoryService.ROOT_PID_VALUE)) {
            // 旧父若无其他子节点，置 NOCHILD（简化：交给现有 updateOldParentNode）
            updateOldParentNode(oldPid);
        }
        exist.setCategoryName(name);
        exist.setPid(localPid);
        exist.setType(type);
        exist.setFullName(fullName);
        exist.setFullId(fullId);
        if (parent != null) {
            baseMapper.updateTreeNodeStatus(parent.getId(), IEquipmentCategoryService.HASCHILD);
        }
        // 级联更新子节点 fullName/fullId（沿用现有 updateFullInfo 语义）
        baseMapper.updateFullInfo(
                exist.getFullId() + IEquipmentCategoryService.connector + exist.getId(),
                fullName,
                fullId + IEquipmentCategoryService.connector + exist.getId());
        baseMapper.updateById(exist);
        return UpsertResult.ok(exist.getId());
    }
}
```
> `updateOldParentNode(Long pid)` 是 `EquipmentCategoryServiceImpl` 现有 private 方法，已可在此直接调用。

- [ ] **Step 4: ISpaceService 与 SpaceServiceImpl 同样新增**

接口加：
```java
UpsertResult upsertByMasterId(String masterId, String name, String masterPid);
```
实现与类别完全同构，仅：字段用 `spaceName`、无 `type`、表为 `space`、常量取自 `ISpaceService`。

- [ ] **Step 5: 编译验证**

IDE 编译 mdm.service，确认 `upsertByMasterId` 两个实现无错。

---

## Task 6: IntegrationReceiveService（接收落库核心）

**Files:**
- Create: `integration/service/IntegrationReceiveService.java`
- Create: `integration/service/impl/IntegrationReceiveServiceImpl.java`

**Interfaces:**
- Consumes: `IEquipmentCategoryService.upsertByMasterId(...)`、`ISpaceService.upsertByMasterId(...)`；`DeviceMapper`（按 master_id / name 查）；`IntegrationPayload` + item DTO。
- Produces: `receive(IntegrationPayload payload, String deviceType)`，`deviceType` 由端点传入（"1" 仪表 / "2" 设备）；返回 `ReceiveResult`。供 Task 7 Controller 调用。

- [ ] **Step 1: 接口**

```java
package org.jeecg.modules.bems.integration.service;

import org.jeecg.modules.bems.integration.dto.IntegrationPayload;
import org.jeecg.modules.bems.integration.dto.ReceiveResult;

public interface IntegrationReceiveService {
    /**
     * @param deviceType "1" 仪表 / "2" 设备（由端点决定，仅 DEVICE/CATEGORY 落库赋值用；SPACE 忽略）
     */
    ReceiveResult receive(IntegrationPayload<?> payload, String deviceType);
}
```

- [ ] **Step 2: 实现（按 body.type 分流）**

```java
package org.jeecg.modules.bems.integration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.integration.dto.*;
import org.jeecg.modules.bems.integration.service.IntegrationReceiveService;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.EquipmentCategory;
import org.jeecg.modules.bems.mdm.entity.Space;
import org.jeecg.modules.bems.mdm.mapper.DeviceMapper;
import org.jeecg.modules.bems.mdm.mapper.EquipmentCategoryMapper;
import org.jeecg.modules.bems.mdm.mapper.SpaceMapper;
import org.jeecg.modules.bems.mdm.service.IEquipmentCategoryService;
import org.jeecg.modules.bems.mdm.service.ISpaceService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class IntegrationReceiveServiceImpl implements IntegrationReceiveService {

    private IEquipmentCategoryService categoryService;
    private ISpaceService spaceService;
    private DeviceMapper deviceMapper;
    private EquipmentCategoryMapper categoryMapper;
    private SpaceMapper spaceMapper;

    @Override
    public ReceiveResult receive(IntegrationPayload<?> payload, String deviceType) {
        ReceiveResult result = new ReceiveResult(payload.getBatchId());
        String type = payload.getType();
        String op = payload.getOp();
        for (Object raw : payload.getData()) {
            String rejectReason = dispatch(type, op, deviceType, raw);
            if (rejectReason == null) {
                result.setAccepted(result.getAccepted() + 1);
            } else {
                String id = extractId(raw);
                result.getRejected().add(new RejectedItem(id, rejectReason));
            }
        }
        return result;
    }

    /** @return null 表示成功；非空为 reject 原因 */
    @SuppressWarnings("unchecked")
    private String dispatch(String type, String op, String deviceType, Object raw) {
        try {
            if ("CATEGORY".equals(type)) {
                return handleCategory(op, deviceType, (CategoryPushItem) convert(raw, CategoryPushItem.class));
            } else if ("SPACE".equals(type)) {
                return handleSpace(op, (SpacePushItem) convert(raw, SpacePushItem.class));
            } else if ("DEVICE".equals(type)) {
                return handleDevice(op, deviceType, (DevicePushItem) convert(raw, DevicePushItem.class));
            }
            return "不支持的类型:" + type;
        } catch (Exception e) {
            log.error("接收处理异常: {}", raw, e);
            return "处理异常:" + e.getMessage();
        }
    }

    private String handleCategory(String op, String type, CategoryPushItem item) {
        if ("DELETE".equals(op)) {
            categoryMapper.delete(new QueryWrapper<EquipmentCategory>().eq("master_id", item.getId()));
            return null;
        }
        UpsertResult r = categoryService.upsertByMasterId(item.getId(), item.getName(), item.getPid(), type);
        return r.isOk() ? null : r.getReason();
    }

    private String handleSpace(String op, SpacePushItem item) {
        if ("DELETE".equals(op)) {
            spaceMapper.delete(new QueryWrapper<Space>().eq("master_id", item.getId()));
            return null;
        }
        UpsertResult r = spaceService.upsertByMasterId(item.getId(), item.getName(), item.getPid());
        return r.isOk() ? null : r.getReason();
    }

    private String handleDevice(String op, String deviceType, DevicePushItem item) {
        Device exist = deviceMapper.selectOne(new QueryWrapper<Device>().eq("master_id", item.getId()));
        if ("DELETE".equals(op)) {
            if (exist != null) {
                deviceMapper.deleteById(exist.getId());
            }
            return null;
        }
        // UPSERT / SNAPSHOT
        // 1. 引用校验：categoryId / spaceId 必须在本地存在（按 master_id 查）
        Long categoryId = item.getCategoryId() == null ? null
                : toLocalId(categoryMapper, item.getCategoryId());
        if (item.getCategoryId() != null && categoryId == null) return "类别不存在";
        Long spaceId = item.getSpaceId() == null ? null
                : toLocalId(spaceMapper, item.getSpaceId());
        if (item.getSpaceId() != null && spaceId == null) return "空间不存在";
        // 2. 名称冲突（撞别的 master_id）
        Device nameOwner = deviceMapper.selectOne(
                new QueryWrapper<Device>().eq("device_name", item.getName()).last("limit 1"));
        if (nameOwner != null && (nameOwner.getMasterId() == null
                || !nameOwner.getMasterId().equals(item.getId()))) {
            return "设备名称冲突";
        }
        if (exist == null) {
            Device d = new Device();
            d.setMasterId(item.getId());
            d.setDeviceName(item.getName());
            d.setDeviceCode(item.getName());
            d.setDeviceType(deviceType);
            d.setCategoryId(categoryId);
            d.setSpaceId(spaceId);
            d.setRemark(item.getRemark());
            deviceMapper.insert(d);
        } else {
            exist.setDeviceName(item.getName());
            exist.setDeviceCode(item.getName());
            exist.setDeviceType(deviceType);
            exist.setCategoryId(categoryId);
            exist.setSpaceId(spaceId);
            exist.setRemark(item.getRemark());
            deviceMapper.updateById(exist);
        }
        return null;
    }

    /** 按 master_id 查本地类别 Long id；查不到返回 null */
    private Long toLocalId(EquipmentCategoryMapper mapper, String masterId) {
        EquipmentCategory c = mapper.selectOne(new QueryWrapper<EquipmentCategory>().eq("master_id", masterId));
        return c == null ? null : c.getId();
    }

    /** 按 master_id 查本地空间 Long id；查不到返回 null */
    private Long toLocalId(SpaceMapper mapper, String masterId) {
        Space s = mapper.selectOne(new QueryWrapper<Space>().eq("master_id", masterId));
        return s == null ? null : s.getId();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper OM =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private Object convert(Object raw, Class<?> clz) {
        // payload.data 经 Jackson 解析为 LinkedHashMap；用 ObjectMapper 转目标类型
        return OM.convertValue(raw, clz);
    }

    private String extractId(Object raw) {
        if (raw instanceof java.util.Map) {
            Object id = ((java.util.Map<?, ?>) raw).get("id");
            return id == null ? null : id.toString();
        }
        return null;
    }
}
```


- [ ] **Step 3: 编译验证**

IDE 编译 integration.service，修复 ObjectMapper 转换、重载调用后确认无错。

---

## Task 7: TokenAuthInterceptor + IntegrationController

**Files:**
- Create: `integration/config/TokenAuthInterceptor.java`
- Modify: `integration/config/IntegrationConfig.java`（注册拦截器）
- Create: `integration/controller/IntegrationController.java`

**Interfaces:**
- Consumes: `IntegrationReceiveService.receive(payload, deviceType)`；`IntegrationProperties.token`。
- Produces: `POST /integration/receive/meter`（deviceType="1"）、`POST /integration/receive/equipment`（deviceType="2"），`@IgnoreAuth` + token 校验，返回 jeecg `Result<ReceiveResult>`。

- [ ] **Step 1: TokenAuthInterceptor**

```java
package org.jeecg.modules.bems.integration.config;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.common.api.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class TokenAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private IntegrationProperties props;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object h) throws Exception {
        String token = req.getHeader("X-Integration-Token");
        if (token == null || !token.equals(props.getToken())) {
            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
            resp.setContentType("application/json;charset=UTF-8");
            Result<Void> body = new Result<>();
            body.setSuccess(false);
            body.setCode(HttpStatus.UNAUTHORIZED.value());
            body.setMessage("对接令牌无效或接收未启用");
            resp.getWriter().write(JSONObject.toJSONString(body));
            return false;
        }
        return true;
    }
}
```

- [ ] **Step 2: 在 IntegrationConfig 注册拦截器**

在 `IntegrationConfig` 增加 `implements WebMvcConfigurer` 与：
```java
@Autowired
private TokenAuthInterceptor tokenAuthInterceptor;

@Override
public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(tokenAuthInterceptor)
            .addPathPatterns("/integration/receive/**");
}
```
（补 import `org.springframework.web.servlet.config.annotation.*`）

- [ ] **Step 3: IntegrationController（2 端点）**

```java
package org.jeecg.modules.bems.integration.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.AllArgsConstructor;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.bems.integration.dto.IntegrationPayload;
import org.jeecg.modules.bems.integration.dto.ReceiveResult;
import org.jeecg.modules.bems.integration.service.IntegrationReceiveService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "主数据对接接收")
@RestController
@RequestMapping("/integration/receive")
@IgnoreAuth
@AllArgsConstructor
public class IntegrationController {

    private IntegrationReceiveService receiveService;

    @ApiOperation("接收仪表（类别/空间/设备，type=1）")
    @PostMapping("/meter")
    public Result<ReceiveResult> receiveMeter(@RequestBody IntegrationPayload<Object> payload) {
        return Result.OK("操作成功", receiveService.receive(payload, "1"));
    }

    @ApiOperation("接收设备（类别/空间/设备，type=2）")
    @PostMapping("/equipment")
    public Result<ReceiveResult> receiveEquipment(@RequestBody IntegrationPayload<Object> payload) {
        return Result.OK("操作成功", receiveService.receive(payload, "2"));
    }
}
```

- [ ] **Step 4: 启动 + 手验（鉴权与分流）**

启动应用。先验鉴权：
```bash
curl -i -X POST http://localhost:7010/integration/receive/meter \
  -H "Content-Type: application/json" \
  -d '{"type":"SPACE","op":"UPSERT","batchId":"t1","data":[{"id":"s-root","name":"测试楼","pid":"0"}]}'
# 预期：HTTP 401，body message=对接令牌无效或接收未启用
```
再带 token 验证空间落库（`X-Integration-Token` 取 application.yml 中 `PLEASE_SET_TOKEN` 改后的值）：
```bash
curl -i -X POST http://localhost:7010/integration/receive/meter \
  -H "Content-Type: application/json" -H "X-Integration-Token: <token>" \
  -d '{"type":"SPACE","op":"UPSERT","batchId":"t2","data":[{"id":"m-space-1","name":"一楼","pid":"0"}]}'
# 预期：HTTP 200，result.accepted=1；DB space 表新增一行 master_id=m-space-1，space_name=一楼，full_name=一楼（本地算法）
```

---

## Task 8: IntegrationPushClient + IntegrationPushService

**Files:**
- Create: `integration/client/IntegrationPushClient.java`
- Create: `integration/service/IntegrationPushService.java`
- Create: `integration/service/impl/IntegrationPushServiceImpl.java`

**Interfaces:**
- Consumes: `IntegrationProperties`、`@Qualifier("integrationRestTemplate") RestTemplate`、`@Qualifier("integrationPushExecutor") Executor`、`IIntegrationPushLogService`、`DeviceMapper` / `EquipmentCategoryMapper` / `SpaceMapper`（id 反查与 master_id 懒生成）。
- Produces: `pushDevices(List<Device> devices, String op)` —— 由 Task 9 在设备增删改 afterCommit 调用；内部聚合为一个 batchId、异步推送、写日志。

- [ ] **Step 1: IntegrationPushClient（RestTemplate 调 master）**

```java
package org.jeecg.modules.bems.integration.client;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.integration.config.IntegrationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class IntegrationPushClient {

    private final RestTemplate restTemplate;
    private final IntegrationProperties props;

    public IntegrationPushClient(@Qualifier("integrationRestTemplate") RestTemplate restTemplate,
                                 IntegrationProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    /** @return {httpStatus, body} */
    public Map<String, Object> postReceive(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Integration-Token", props.getToken());
        headers.set("X-Source", props.getSource());
        String url = props.getMaster().getBaseUrl() + props.getMaster().getReceivePath();
        ResponseEntity<Map> resp = restTemplate.postForEntity(url,
                new HttpEntity<>(payload, headers), Map.class);
        return Map.of(
                "httpStatus", resp.getStatusCodeValue(),
                "body", resp.getBody() == null ? Map.of() : resp.getBody()
        );
    }
}
```

- [ ] **Step 2: IntegrationPushService 接口**

```java
package org.jeecg.modules.bems.integration.service;

import org.jeecg.modules.bems.mdm.entity.Device;
import java.util.List;

public interface IntegrationPushService {
    /** 设备增删改后调用；op = UPSERT / DELETE */
    void pushDevices(List<Device> devices, String op);
}
```

- [ ] **Step 3: IntegrationPushServiceImpl（转换 + 懒生成 + 聚合 + 异步 + 日志）**

```java
package org.jeecg.modules.bems.integration.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.integration.client.IntegrationPushClient;
import org.jeecg.modules.bems.integration.config.IntegrationProperties;
import org.jeecg.modules.bems.integration.dto.DevicePushItem;
import org.jeecg.modules.bems.integration.entity.IntegrationPushLog;
import org.jeecg.modules.bems.integration.service.IIntegrationPushLogService;
import org.jeecg.modules.bems.integration.service.IntegrationPushService;
import org.jeecg.modules.bems.mdm.entity.Device;
import org.jeecg.modules.bems.mdm.entity.EquipmentCategory;
import org.jeecg.modules.bems.mdm.entity.Space;
import org.jeecg.modules.bems.mdm.mapper.DeviceMapper;
import org.jeecg.modules.bems.mdm.mapper.EquipmentCategoryMapper;
import org.jeecg.modules.bems.mdm.mapper.SpaceMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IntegrationPushServiceImpl implements IntegrationPushService {

    private final IntegrationProperties props;
    private final IntegrationPushClient client;
    private final IIntegrationPushLogService pushLogService;
    private final DeviceMapper deviceMapper;
    private final EquipmentCategoryMapper categoryMapper;
    private final SpaceMapper spaceMapper;
    private final java.util.concurrent.Executor executor;

    public IntegrationPushServiceImpl(IntegrationProperties props,
                                      IntegrationPushClient client,
                                      IIntegrationPushLogService pushLogService,
                                      DeviceMapper deviceMapper,
                                      EquipmentCategoryMapper categoryMapper,
                                      SpaceMapper spaceMapper,
                                      @Qualifier("integrationPushExecutor") java.util.concurrent.Executor executor) {
        this.props = props; this.client = client; this.pushLogService = pushLogService;
        this.deviceMapper = deviceMapper; this.categoryMapper = categoryMapper; this.spaceMapper = spaceMapper;
        this.executor = executor;
    }

    @Override
    public void pushDevices(List<Device> devices, String op) {
        if (!props.isEnabled() || CollectionUtil.isEmpty(devices)) return;
        // 转换 + master_id 懒生成
        List<DevicePushItem> items = new ArrayList<>();
        for (Device d : devices) {
            items.add(toItem(d));
        }
        executor.execute(() -> doPush(items, op));
    }

    private DevicePushItem toItem(Device d) {
        DevicePushItem item = new DevicePushItem();
        // master_id 懒生成
        if (d.getMasterId() == null || d.getMasterId().isEmpty()) {
            String uuid = java.util.UUID.randomUUID().toString().replace("-", "");
            d.setMasterId(uuid);
            Device patch = new Device();
            patch.setId(d.getId());
            patch.setMasterId(uuid);
            deviceMapper.updateById(patch);
        }
        item.setId(d.getMasterId());
        item.setName(d.getDeviceName());
        item.setRemark(d.getRemark());
        item.setCategoryId(toMasterId(d.getCategoryId(), true));
        item.setSpaceId(toMasterId(d.getSpaceId(), false));
        return item;
    }

    /** 本地 Long -> master uuid；isCategory 区分类别/空间 */
    private String toMasterId(Long localId, boolean isCategory) {
        if (localId == null) return null;
        if (isCategory) {
            EquipmentCategory c = categoryMapper.selectById(localId);
            return c == null ? null : c.getMasterId();
        } else {
            Space s = spaceMapper.selectById(localId);
            return s == null ? null : s.getMasterId();
        }
    }

    private void doPush(List<DevicePushItem> items, String op) {
        String batchId = java.util.UUID.randomUUID().toString().replace("-", "");
        // 先写一条日志（FAIL 待补）
        IntegrationPushLog log0 = new IntegrationPushLog();
        log0.setBatchId(batchId);
        log0.setOp(op);
        log0.setType("DEVICE");
        log0.setDataCount(items.size());
        log0.setDataIds(items.stream().map(DevicePushItem::getId)
                .collect(Collectors.joining(",")));
        log0.setCreateTime(new Date());

        Map<String, Object> payload = new HashMap<>();
        payload.put("source", props.getSource());
        payload.put("type", "DEVICE");
        payload.put("op", op);
        payload.put("batchId", batchId);
        payload.put("data", items);

        try {
            Map<String, Object> resp = client.postReceive(payload);
            int httpStatus = (Integer) resp.get("httpStatus");
            log0.setHttpStatus(httpStatus);
            log0.setStatus(httpStatus >= 200 && httpStatus < 300 ? "SUCCESS" : "FAIL");
            log0.setResponseMsg(String.valueOf(resp.get("body")));
        } catch (Exception e) {
            log.error("推送 master 失败 batchId={}", batchId, e);
            log0.setStatus("FAIL");
            log0.setResponseMsg(e.getClass().getSimpleName() + ":" + e.getMessage());
        }
        pushLogService.save(log0);
    }
}
```

- [ ] **Step 4: 编译验证**

IDE 编译 integration 全包，确认无错。

---

## Task 9: 设备增删改触发推送

**Files:**
- Modify: `mdm/service/impl/DeviceServiceImpl.java`

**Interfaces:**
- Consumes: `IntegrationPushService.pushDevices(List<Device>, String op)`；`TransactionSynchronizationManager`（afterCommit 异步）。
- Produces: `addDevice` / `updateById` / `removeById` 在事务提交后触发推送。

- [ ] **Step 1: 注入 PushService（用 @Lazy 避免潜在循环依赖）**

在 `DeviceServiceImpl` 字段区加：
```java
private final org.jeecg.modules.bems.integration.service.IntegrationPushService integrationPushService;
```
（构造器注入：该类若用 `@AllArgsConstructor` 则自动注入；若手写构造器需补形参。确认注入方式后用 `@Lazy` 修饰该字段对应的构造器参数以避免循环依赖。）

- [ ] **Step 2: addDevice 末尾触发 UPSERT**

在 `addDevice(Device device)` 方法末尾、insert 完成后追加：
```java
registerAfterCommitPush(java.util.Collections.singletonList(device), "UPSERT");
```

- [ ] **Step 3: updateById 末尾触发 UPSERT**

在 `updateById(Device device)` 末尾追加同样：
```java
registerAfterCommitPush(java.util.Collections.singletonList(device), "UPSERT");
```

- [ ] **Step 4: 重写 removeById，删除前快照、删除后触发 DELETE**

在类中新增：
```java
@Override
public boolean removeById(java.io.Serializable id) {
    Device snapshot = this.getById((Long) id);   // 删除前快照（带原 categoryId/spaceId/master_id）
    boolean ok = super.removeById(id);
    if (ok && snapshot != null) {
        registerAfterCommitPush(java.util.Collections.singletonList(snapshot), "DELETE");
    }
    return ok;
}
```

- [ ] **Step 5: afterCommit 异步辅助方法**

在类中新增私有方法：
```java
private void registerAfterCommitPush(java.util.List<Device> devices, String op) {
    if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    integrationPushService.pushDevices(devices, op);
                }
            });
    } else {
        // 无事务上下文（如批量/异步调用）直接推
        integrationPushService.pushDevices(devices, op);
    }
}
```

- [ ] **Step 6: 手验（推送链路）**

1. 应用已执行 Task 1 建表 SQL。
2. 在 bems 新增一台设备（调用 `/bems/device/measuring/add` 或界面）。
3. 查 `integration_push_log`：应新增一条 `op=UPSERT, type=DEVICE, status=SUCCESS/FAIL`。
4. 若 master 可达且 token 正确 → status=SUCCESS；否则 FAIL（符合预期，不重推）。
5. 删除该设备 → 日志新增 `op=DELETE` 一条。
6. 核对 `device` 表中该行 `master_id` 已被懒生成（32 位无横线 uuid）。

---

## Task 10: 端到端手验清单

**Files:** 无代码，仅验收。

- [ ] **Step 1: 接收 · 类别（type=CATEGORY）**
```bash
curl -X POST http://localhost:7010/integration/receive/meter \
  -H "Content-Type: application/json" -H "X-Integration-Token: <token>" \
  -d '{"source":"sgai-master","type":"CATEGORY","op":"UPSERT","batchId":"c1","data":[{"id":"m-cat-1","name":"电气","pid":"0"}]}'
```
核对：`equipment_category` 新增行 `master_id=m-cat-1, category_name=电气, type=1, full_name=电气`。

- [ ] **Step 2: 接收 · 空间（type=SPACE，经 meter 端点）**
```bash
curl -X POST http://localhost:7010/integration/receive/meter \
  -H "Content-Type: application/json" -H "X-Integration-Token: <token>" \
  -d '{"source":"sgai-master","type":"SPACE","op":"UPSERT","batchId":"s1","data":[{"id":"m-space-1","name":"一楼","pid":"0"}]}'
```
核对：`space` 新增 `master_id=m-space-1, space_name=一楼, full_name=一楼`（space 无 type 字段，端点不影响）。

- [ ] **Step 3: 接收 · 设备（type=DEVICE，经 equipment 端点，deviceType=2）**
```bash
curl -X POST http://localhost:7010/integration/receive/equipment \
  -H "Content-Type: application/json" -H "X-Integration-Token: <token>" \
  -d '{"source":"sgai-master","type":"DEVICE","op":"UPSERT","batchId":"d1","data":[{"id":"m-dev-1","name":"1号冷水机组","categoryId":"m-cat-1","spaceId":"m-space-1","remark":""}]}'
```
核对：`device` 新增 `master_id=m-dev-1, device_name=1号冷水机组, device_code=1号冷水机组, device_type=2, category_id=<本地id>, space_id=<本地id>`。

- [ ] **Step 4: 接收 · 引用缺失 reject**
将上例 `categoryId` 改为不存在的 `m-cat-x` → 响应 `result.rejected=[{id:"m-dev-1",reason:"类别不存在"}], accepted=0`。

- [ ] **Step 5: 接收 · 名称冲突**
再用相同 name 但不同 `id`（如 `m-dev-2`）经 meter 端点推送 → `rejected` 原因 `设备名称冲突`。

- [ ] **Step 6: 推送链路**（Task 9 Step 6 已覆盖，复核 `integration_push_log` 与 master 调用）。

- [ ] **Step 7: 幂等**
对同一 batchId/内容重复推送 → 数据无重复、无副作用。

---

## 自查（计划 vs spec 覆盖）

- spec §2 决策 → Global Constraints + 各 Task 体现 ✅
- spec §3 端点结构（2 端点 + body.type 分流）→ Task 7 ✅
- spec §4 数据模型（master_id / 配置 / 日志表）→ Task 1/2/4 ✅
- spec §5 接收落库（pid 转换/引用校验/名称冲突/全称本地重建/op）→ Task 5/6 ✅
- spec §6 推送（afterCommit 异步/字段转换/懒生成/聚合/日志/失败不重推）→ Task 8/9 ✅
- spec §7 鉴权（@IgnoreAuth + 拦截器）→ Task 7 ✅
- spec §8 文件清单 → 文件结构 + 各 Task ✅
- spec §9 测试 → 每个 Task 的「编译/手验」检查点 + Task 10 端到端 ✅

无占位符（`toLocalId` 已给确切重载实现）；类型/方法名前后一致（`upsertByMasterId`、`receive(payload, deviceType)`、`pushDevices(devices, op)`、`registerAfterCommitPush`）。
