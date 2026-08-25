# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 在此代码库中工作时提供指导。

## 项目概述

**sgai-module-bems**（建筑能源管理系统）是一个用于建筑能耗监控、分析和控制的 Spring Boot 微服务。它是 JeecgBoot 3.7.0 低代码平台的一部分。

- **版本**: 3.7.3
- **服务名称**: sgai-bems-dev
- **端口**: 7010
- **主类**: `org.jeecg.JeecgBemsCloudApplication`

## 构建与开发命令

```bash
# 构建整个项目
mvn clean install

# 构建特定模块
mvn clean install -pl sgai-module-bems-biz

# 运行应用
cd sgai-module-bems-start
mvn spring-boot:run

# 运行测试
mvn test

# 运行特定测试类
mvn test -Dtest=CostCenterDataHourMapperTest

# 打包部署
mvn clean package
```

## 模块结构

多模块 Maven 项目，依赖关系如下：

```
sgai-module-bems-start（应用启动模块）
    └── 依赖 → sgai-module-bems-biz（业务逻辑）
              └── 依赖 → sgai-module-bems-api（Feign 客户端/API 契约）
```

- **sgai-module-bems-api**: Feign 客户端接口、DTO、降级实现
- **sgai-module-bems-biz**: 控制器、服务、映射器、实体（432 个 Java 文件）
- **sgai-module-bems-start**: 应用入口点、配置文件，无业务逻辑

## 技术栈

- **框架**: Spring Boot + Spring Cloud (JeecgBoot 3.7.0)
- **ORM**: MyBatis-Plus（62 个实体，使用 BaseMapper）
- **安全**: Apache Shiro
- **服务发现**: Nacos (10.168.47.25:8848)
- **消息队列**: RabbitMQ
- **分布式锁**: Redisson
- **任务调度**: Quartz
- **API 文档**: Swagger
- **工具库**: HuTool、Apache Commons JEXL3（动态公式）、Lombok

## 架构模式

**受 DDD 影响的分层架构：**

```
Controller 层（51 个 REST 控制器）
    ↓
Service 层（接口 + 实现模式）
    ↓
Mapper 层（MyBatis-Plus BaseMapper）
    ↓
Database
```

### Service 层模式

```java
// 接口
public interface IDeviceService extends IService<Device> {
    IPage<Device> listPage(DeviceDto params);
    void addDevice(Device device);
}

// 实现
@Service
@AllArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device>
    implements IDeviceService {
    // 首选构造器注入（Lombok @AllArgsConstructor）
}
```

### 实体约定

所有实体继承 `BaseEntity`，提供以下字段：
- `id`（Long，自增）
- `createBy`、`createTime`
- `updateBy`、`updateTime`
- `sysOrgCode`（多租户支持）
- `pageNo`、`pageSize`（分页）

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("table_name")
@ApiModel(value="描述", description="表描述")
public class MyEntity extends BaseEntity {
    @ApiModelProperty(value = "字段描述")
    private String fieldName;

    @TableField(exist = false)  // 非数据库字段
    private String transientField;
}
```

### Controller 约定

```java
@Api(tags="模块名称")
@RestController
@RequestMapping("/bems/module")
@AllArgsConstructor
public class MyController extends JeecgController<MyEntity, IMyService> {

    @ApiOperation(value="操作说明")
    @AutoLog(value="操作说明-操作类型")  // 审计日志
    @RequiresPermissions("bems:module:add")  // Shiro 权限控制
    @PostMapping("/add")
    public Result<String> add(@RequestBody MyEntity params) {
        service.save(params);
        return Result.OK("添加成功！");
    }
}
```

### 查询包装器模式

```java
LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<Device>()
    .eq(Device::getDeviceCode, deviceCode)
    .like(Device::getDeviceName, deviceName)
    .orderByDesc(Device::getCreateTime);
```

### 分页模式

```java
IPage<Device> page = new Page<>(params.getPageNo(), params.getPageSize());
return deviceService.page(page, wrapper);
```

## 业务域模块

`org.jeecg.modules.bems` 包下的结构：

- **mdm** - 主数据管理（设备、空间、设备类别、设备模型、属性）
- **energyAnalysis** - 能源分析、成本中心、碳排放、计量点
- **alarm** - 告警规则、记录、类别、级别
- **patterned** - 场景控制策略、联动策略
- **bc** - 楼控（控制点、控制历史）
- **project** - 节能项目管理
- **homePage** - 首页统计
- **dataBoard** - 数据可视化
- **lighting** - 照明控制
- **job** - 定时任务（Quartz）
- **mq** - 消息队列处理器（RabbitMQ）
- **dto** - 数据传输对象
- **vo** - 视图对象
- **constant** - 常量定义

## 关键开发模式

### 事务管理

```java
@Transactional
public void addDevice(Device device) {
    // 事务内的多步操作
}
```

### 依赖注入

- 首选构造器注入（Lombok `@AllArgsConstructor`）
- 循环依赖时使用 `@Lazy`

### 日志记录

```java
@Slf4j
public class MyService {
    log.info("处理中: {}", params);
    log.error("发生错误", e);
}
```

### 异常处理

```java
throw new JeecgBootException("错误信息");
```

### API 响应标准化

```java
Result<Device> result = Result.ok(device);
Result.error("未找到对应数据");
```

## 数据库设计

- **62 个实体**，按业务域组织
- **树形结构数据**：空间层级
- **时序数据**：多粒度计量数据（分钟/小时/天/月/年）
- **历史追踪**：DeviceAttributeHistory
- **多租户**：sysOrgCode 字段

## 命名约定

- **实体**：单数名词（Device、Space）
- **服务**：接口使用 `I` 前缀（IDeviceService）
- **控制器**：实体名 + Controller（DeviceController）
- **映射器**：实体名 + Mapper（DeviceMapper）
- **DTO**：实体名 + Dto（DeviceDto）
- **VO**：实体名 + Vo（DeviceDataVo）

## 配置管理

主要配置在 Nacos 中：
- `jeecg.yaml`
- `jeecg-dev.yaml`
- 服务器：10.168.47.25:8848
- 分组：DEFAULT_GROUP
- 命名空间：public

本地配置仅用于启动引导，位于 `application.yml`。

## 网关集成

应用启动时通过 Redis 发布/订阅自动刷新网关路由，解决网关先于服务启动时 Swagger 访问问题。

## 特殊功能

1. **消息队列**：RabbitMQ 用于异步处理（如 PatterningStrategyQueueService）
2. **分布式锁**：Redisson 用于并发操作
3. **表达式求值**：Apache Commons JEXL3 用于动态能耗计算公式
4. **数据导出**：使用自定义 DTO 和 JeecgEntityExcelView 导出 Excel
5. **延迟队列**：基于 Redis 的延迟队列用于定时任务

## 开发工作流程

添加新功能时，按以下顺序进行：

1. 创建实体（继承 BaseEntity）
2. 创建映射器（继承 BaseMapper<Entity>）
3. 创建服务接口（继承 IService<Entity>）
4. 创建服务实现（继承 ServiceImpl<Mapper, Entity>）
5. 创建控制器（继承 JeecgController<Entity, Service>）

始终优先使用 MyBatis-Plus 内置的 CRUD 方法，再考虑编写自定义 SQL。
