# AGENTS.md

本文件为 ZCode 代理在此代码库中工作时提供项目专属指引。详细的架构与编码约定见同目录 `CLAUDE.md`（两者互补，本文件聚焦关键事实与边界，不重复展开）。

## 仓库性质

- **项目**：sgai-module-bems，建筑能源管理系统（BEMS），JeecgBoot 3.7.0 低代码平台下的 Spring Boot + Spring Cloud 微服务。
- **版本/服务**：artifact 3.7.3；`spring.application.name=sgai-bems-dev`；端口 `7010`。
- **主类**：`org.jeecg.JeecgBemsCloudApplication`（位于 `sgai-module-bems-start`）。
- **父 POM**：`sgai-boot-parent:3.7.0`（`org.jeecgframework.boot`），平台依赖统一在父级管理，本仓库 pom 不重复声明版本。
- **技术栈要点**：MyBatis-Plus（无 XML mapper，全注解）、Apache Shiro、Nacos 配置/注册中心（`10.168.47.25:8848`，`DEFAULT_GROUP`，`public`）、RabbitMQ、Redisson、Quartz、HuTool、JEXL3、Lombok。

## 模块结构

Maven 三子模块，依赖自下而上：

```
sgai-module-bems-start   启动入口、配置、数据权限/异步等全局 Bean（无业务逻辑）
    └── sgai-module-bems-biz    controller/service/mapper/entity/定时任务/MQ
            └── sgai-module-bems-api   Feign 接口、对外 DTO/Entity、fallback
```

- **api**：跨服务契约。新增对外暴露的实体/Feign 接口放这里（包：`org.jeecg.modules.bems.{api,entity,vo}`）。
- **biz**：业务实现，508 个 Java 文件，根包 `org.jeecg.modules.bems`。
- **start**：`application.yml` 仅做 Nacos 引导与 bems 集成配置；业务配置在 Nacos（`jeecg.yaml` / `jeecg-dev.yaml`）。

## biz 业务域（子包）

`mdm`(主数据：设备/空间/类别/模型/属性)、`energyAnalysis`(能耗/成本中心/碳排放/计量点)、`alarm`、`patterned`(场景/联动策略)、`bc`(楼控)、`project`(节能项目)、`homePage`、`dataBoard`、`lighting`、`job`(Quartz)、`mq`(RabbitMQ)、`dto`、`vo`、`constant`，以及：

- **`integration`**：主数据对外集成推送。`config/TokenAuthInterceptor` 按 token 鉴权（`bems.integration.token.*`），`config/IntegrationProperties` 读取 `bems.integration.master.base-url` 与推送超时；`dto/*PushItem` 为推送载荷。受 `bems.integration.enabled` 开关控制。
- **`permission`**：自研行级数据权限。通过 `@DataPermission` / `@DataPermissionField` 注解标记实体，由 `BemsDataPermissionHandler` + `DataPermissionSqlHandler` 改写 SQL，`DataPermissionCacheManager` 缓存角色权限。start 模块 `DataPermissionInterceptorConfig` 装配拦截器。改动权限相关逻辑需同时核对 start 与 biz 两端配置。

## 构建与运行（仅列出，未授权不主动执行）

```bash
mvn clean install                                   # 全量构建
mvn clean install -pl sgai-module-bems-biz -am      # 仅 biz 及其依赖
cd sgai-module-bems-start && mvn spring-boot:run    # 本地运行（依赖 Nacos 可达）
mvn test                                            # 全量测试
mvn test -Dtest=CostCenterDataHourMapperTest        # 单个测试类
mvn clean package                                   # 打包
```

## 编辑约束（重要）

- **分层顺序**：新增功能按 entity → mapper → IService/ServiceImpl → Controller 顺序，包路径放在对应业务域下；不要跨域乱放。
- **实体**：继承 `BaseEntity`（含 `id`/`createBy`/`createTime`/`updateBy`/`updateTime`/`sysOrgCode`/`pageNo`/`pageSize`），用 `@TableName` + `@ApiModel`，非数据库字段加 `@TableField(exist = false)`。
- **Mapper**：继承 `BaseMapper<Entity>`，**不写 XML**（项目无 mapper XML，统一 MyBatis-Plus 注解/Wrapper）。优先用内置 CRUD 与 `LambdaQueryWrapper`，再考虑自定义 SQL。
- **Service**：接口 `I` 前缀 + 继承 `IService<Entity>`；实现 `extends ServiceImpl<Mapper, Entity>`，用 Lombok `@AllArgsConstructor` 构造器注入。
- **Controller**：继承 `JeecgController<Entity, IService>`，路径前缀 `/bems/...`；写操作加 `@AutoLog` 审计 + `@RequiresPermissions`（Shiro 权限码 `bems:模块:操作`）。
- **返回值**：统一 `Result<T>`（`Result.ok(data)` / `Result.error("msg")`），不要自行构造 Map 响应。
- **异常**：抛 `JeecgBootException`，不要吞异常。
- **事务**：多步写操作加 `@Transactional`。
- **日志**：`@Slf4j`，占位符 `log.info("xxx: {}", val)`，敏感信息不落日志。
- **Feign/对外契约**：放 `*-api` 模块；biz 内部 DTO/VO 不上浮到 api。

## 集成与权限的敏感区

改动以下区域前，先读对应配置类与现有调用示例，不要凭假设改：

- `integration/config/IntegrationProperties`、`TokenAuthInterceptor`、`IntegrationConfig`：token 值与 master 推送地址来自 `application.yml` 的 `bems.integration.*`。
- `permission/**` 与 `start/.../DataPermissionInterceptorConfig`：数据权限 SQL 改写影响所有标注实体，改动需两端一致。

## 约定文件

- `CLAUDE.md`：完整架构、代码示例、命名约定、特殊功能说明——修改前先读。
- `application.yml`（start）：本地仅引导 + bems 集成参数，其余在 Nacos。
