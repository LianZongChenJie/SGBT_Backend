# sgai-module-bems

建筑能源管理系统（Building Energy Management System，BEMS）后端微服务，基于 JeecgBoot 3.7.0 低代码平台（Spring Boot + Spring Cloud）构建，负责建筑用能设备的监控、能源数据的采集分析、告警与控制策略等业务。

- 项目版本：`3.7.3`
- 父 POM：`org.jeecgframework.boot:sgai-boot-parent:3.7.0`
- 服务入口：`sgai-module-bems-start`，主类 `org.jeecg.JeecgBemsCloudApplication`
- 服务名称 / 端口：以 `application.yml` 为准（当前仓库示例：`sgai-bems-baotou`，端口 `7021`）

---

## 技术栈

| 分类 | 选型 |
| ---- | ---- |
| 基础框架 | Spring Boot + Spring Cloud（JeecgBoot 3.7.0） |
| ORM | MyBatis-Plus（全注解，无 XML Mapper） |
| 安全 | Apache Shiro |
| 注册 / 配置中心 | Nacos（`DEFAULT_GROUP` / `public`） |
| 缓存 | Redis（缓存、延迟队列、网关路由刷新） |
| 消息队列 | RabbitMQ |
| 分布式锁 | Redisson |
| 任务调度 | Quartz |
| API 文档 | Swagger |
| 工具库 | HuTool、Apache Commons JEXL3（动态公式）、Lombok |
| 实时数据接入 | pSpace Java SDK（见 `docs/pSpace-javaSDK-2.2.1.jar`） |

> 数据库按现场可为 MySQL / 达梦等，数据源与连接信息在 Nacos 中维护；`docs/superpowers/sql` 下保存了部分表的建表样例（主数据集成、设备实时点/监控点等），字段以实体与现场库为准。

---

## 模块结构

Maven 多模块工程，依赖方向自下而上：

```
sgai-module-bems-start   应用启动入口、配置、网关刷新等全局 Bean（无业务代码）
    └── sgai-module-bems-biz   Controller / Service / Mapper / Entity / 定时任务 / MQ（业务实现）
            └── sgai-module-bems-api  Feign 接口与对外契约 DTO、fallback
```

- `sgai-module-bems-api`：跨服务接口契约（Feign）、对外 DTO / 实体。包 `org.jeecg.modules.bems.{api,entity,vo}`
- `sgai-module-bems-biz`：业务主体，根包 `org.jeecg.modules.bems`，按业务域分子包
- `sgai-module-bems-start`：入口与本地引导配置，业务配置位于 Nacos（`jeecg.yaml`、`bems-baotou-dev.yaml` 等）

### biz 业务域子包

| 包 | 说明 |
| ---- | ---- |
| `mdm` | 主数据：设备、空间、设备类别、设备模型、设备属性 |
| `dataRead` | 实时数据读取与回写（pSpace 对接、属性值/质量戳、设备状态、历史入库） |
| `energyAnalysis` | 能源分析：能耗、成本中心、碳排放、计量点 |
| `alarm` | 告警规则、记录、类别、级别 |
| `patterned` | 场景控制策略、联动策略、质量戳 |
| `bc` | 楼控：控制点、控制历史 |
| `project` | 节能项目管理 |
| `homePage` / `dataBoard` | 首页统计、数据可视化 |
| `lighting` | 照明控制 |
| `integration` | 主数据对外集成推送（token 鉴权 + 主数据平台回写） |
| `permission` | 行级数据权限（SQL 改写） |
| `job` | Quartz 定时任务 |
| `mq` | RabbitMQ 消息处理 |
| `dto` / `vo` / `entity` / `constant` | 通用 DTO / VO / 实体 / 常量 |

---

## 环境要求与前置依赖

- JDK 8+（与 JeecgBoot 平台兼容版本一致即可）
- Maven 3.x
- 可访问的 Nacos（注册 + 配置中心）、Redis、RabbitMQ、数据库
- 需要读取实时数据时，需可连通 pSpace 服务

> 本地配置 `sgai-module-bems-start/src/main/resources/application.yml` 仅做启动引导与 bems 集成参数，业务/数据源配置在 Nacos。当前示例指向 `192.168.204.51:8848`（`DEFAULT_GROUP` / `public`），并加载 `jeecg.yaml`、`bems-baotou-dev.yaml`。

---

## 构建与运行

```bash
# 全量构建（先于首次运行执行）
mvn clean install

# 仅构建某模块及其依赖
mvn clean install -pl sgai-module-bems-biz -am

# 本地启动（需 Nacos 等前置可达）
cd sgai-module-bems-start
mvn spring-boot:run

# 运行测试 / 指定测试类
mvn test
mvn test -Dtest=CostCenterDataHourMapperTest

# 打包部署
mvn clean package
```

打包产物：`sgai-module-bems-start/target/sgai-module-bems-start.jar`（spring-boot repackage，`finalName=sgai-module-bems-start`）。

服务启动后会自动通过 Redis 发布订阅刷新 Gateway 网关路由（解决网关先启动导致 Swagger 文档不通的问题）。

---

## 关键业务说明

### 1. 实时数据读取（pSpace）

`org.jeecg.modules.bems.dataRead` 负责对接 pSpace 平台：

- 从 `device_attribute` 查询采集编码（`acquisition_coding`）为纯数字的属性，以其作为 `tagId` 批量读取实时数据；
- 按返回 `tagId` 回写 `value`（BOOL 转 `0/1`，其余转字符串），同时回写质量戳 `quality` → `quality_stamp`；
- 同步更新所属设备运行状态与最后采集时间；
- 将本次刷新到值的属性点写入设备属性历史表 `device_attribute_history`（含质量戳），用于时序趋势；
- pSpace 连接参数（`pspace.host/port/username/password/mock`）与各采集分区 tagid 段在 `application.yml` / Nacos 中配置（如 `bems.integration.gas`、`hydrogen`、`eldb` 等，均指向现场实时数据代理服务）。

### 2. 主数据集成推送

`integration` 模块负责设备等主数据对外推送，受 `bems.integration.enabled` 开关控制：

- 携带 token 请求现场主数据平台（`bems.integration.token.meter` / `equipment`，URL 前缀 `bems.integration.master.base-url`）；
- 数据权限相关改动需同时核对 biz 与 start 两端的拦截器配置。

### 3. 行级数据权限

`permission` 模块基于 `@DataPermission` / `@DataPermissionField` 注解由拦截器改写 SQL，实现按角色/部门的数据行级过滤。

### 4. 其它

- `job`：Quartz 定时任务（如周期性采集/分析）；
- `mq`：RabbitMQ 异步处理；
- `patterned`：JEXL3 动态表达式求值、控制策略执行与执行记录。

---

## 开发规范（摘要）

新增功能建议按以下顺序进行，完整约定见 `CLAUDE.md` 与 `AGENTS.md`：

1. 实体（继承 `BaseEntity`，`@TableName` + `@ApiModel`，非库字段加 `@TableField(exist = false)`）
2. Mapper（继承 `BaseMapper<Entity>`，不写 XML，优先内置 CRUD 与 `LambdaQueryWrapper`）
3. Service 接口（`I` 前缀 + `IService<Entity>`）与实现（`ServiceImpl<Mapper, Entity>`，构造器注入）
4. Controller（继承 `JeecgController<Entity, IService>`，路径前缀 `/bems/...`，写操作加 `@AutoLog` + `@RequiresPermissions`）

通用约束：

- 返回值统一 `Result<T>`（`Result.ok(data)` / `Result.error("msg")`）
- 异常抛 `JeecgBootException`，不吞异常
- 多步写操作加 `@Transactional`
- 日志使用 `@Slf4j` 占位符写法
- Feign / 对外契约放 `*-api` 模块，业务内部 DTO / VO 不上浮

---

## 文档索引

- `CLAUDE.md` / `AGENTS.md`：面向 AI 编码助手的项目指引（架构、编码约定、敏感改动区）
- `docs/superpowers/sql/`：部分表建表样例
- `docs/superpowers/plans/`、`specs/`：设计文档与计划
- `docs/pSpace-javaSDK-2.2.1.jar`：pSpace 实时数据接入 SDK
