# StackCat 项目说明

## 概览
StackCat 是一套面向公司内部 yunlifang-java 脚手架的运行时调用追踪与分析解决方案，结合 OpenObserve 实现链路数据的深度集中采集与分析，由 Java Agent 与 Spring Boot 可视化应用两部分组成。借助 ASM 字节码增强，StackCat 能在运行时采集方法调用树、SQL 语句、HTTP 请求头等上下文信息，并异步上报到服务端进行存储、分析与可视化展示，帮助开发者完成性能分析、问题定位与接口治理。

```
stackcat-parent
├── stackcat-agent  # Java Instrumentation Agent，负责采集数据
└── stackcat-app    # Spring Boot 应用，负责接收、持久化与展示
```

## 模块说明

- `stackcat-parent`：统一管理 Maven 依赖版本与插件配置，为子模块提供基础构建环境。
- `stackcat-agent`：基于 `Instrumentation` 与 ASM，向目标 JVM 注入方法入口/出口、数据库调用等监控逻辑，并通过 `MethodTrackingBridge` 异步批量上报。
- `stackcat-app`：Spring Boot 3 Web 应用，提供 REST API、Thymeleaf 前端以及 PostgreSQL 持久化存储，用于展示请求历史、调用树与方法统计信息。

## 核心特性

### Agent 侧
- **多点织入**：自动拦截 Controller、Service、Repository、JDBC `prepareStatement`、MyBatis `SimpleExecutor#doQuery/doUpdate` 以及通用业务类。
- **请求上下文捕获**：从 `RequestContextHolder` 中解析 `requestId` 和请求头，构建完整请求维度的数据包。
- **深度控制**：通过栈深 ThreadLocal 精确记录方法调用链及父子关系。
- **SQL 跟踪**：支持 Spring Data JPA、MyBatis Mapper 及原生 JDBC 的 SQL 捕获。
- **异步上报**：利用内置发送线程按批量/时间阈值推送至 `stackcat-app`，避免阻塞业务线程。

### 应用侧
- **REST 接口**：`/api/tracking/batch` 接收 Agent 批量数据，`/api/tracking/*` 与 `/api/statistics/*` 提供查询能力。
- **数据持久化**：基于 MyBatis-Plus 与 PostgreSQL 存储请求、方法调用与统计信息（建表脚本见 `stackcat-app/src/main/resources/db`）。
- **前端可视化**：`index.html` 展示概览指标，`tracking.html` 支持请求搜索、线程维度展开及树形调用链渲染。
- **灵活过滤**：通过 `stackcat.filter.packages` 配置跟踪范围，避免外部依赖噪音。

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.9+
- PostgreSQL 14+（或兼容版本）

### 构建
```bash
mvn clean package
```
构建完成后：
- `stackcat-agent/target/stackcat-agent-<version>-agent.jar` 为带依赖的 Agent。
- `stackcat-app/target/stackcat-app-<version>.jar` 为 Spring Boot 可执行 JAR。

### 数据库初始化
在目标 PostgreSQL 实例中执行初始化脚本：
```sql
\i stackcat-app/src/main/resources/db/init.sql
```
如需迁移新增字段，可执行 `db/migration` 下增量脚本。

### 启动服务端
```bash
java -jar stackcat-app/target/stackcat-app-<version>.jar
```
默认监听 `http://localhost:8080`，配置项位于 `stackcat-app/src/main/resources/application.yml`。

### 启动被监控应用
为目标 JVM 添加 Agent：
```bash
java -javaagent:/path/to/stackcat-agent-<version>-agent.jar \
     -Dstackcat.filter.packages.include=com.example \
     -Dstackcat.agent.api.url=http://localhost:8080/api/tracking/batch \
     -jar your-application.jar
```

## 配置说明

### Agent 端系统属性
| 属性 | 说明 | 默认值 |
| ---- | ---- | ------ |
| `stackcat.filter.packages.include` | 仅跟踪指定包前缀，逗号分隔。为空时默认 `com.stackcat`。 | `com.stackcat` |
| `stackcat.filter.packages.exclude` | 排除特定包前缀，逗号分隔。 | 空 |
| `stackcat.agent.api.url` | 上报目标地址。 | `http://localhost:8080/api/tracking/batch` |
| `stackcat.agent.async.interval` | 异步发送时间间隔（毫秒）。 | `1000` |
| `stackcat.agent.async.batch-size` | 批量发送的最小请求数。 | `10` |

### 应用端配置（`application.yml`）
- `spring.datasource.*`：数据库连接池配置。
- `mybatis-plus.*`：别名与日志配置。
- `stackcat.filter.packages.exclude/include`：与 Agent 侧保持一致，供服务端展示时过滤。
- `stackcat.agent.*`：用于默认回写到 Agent 的配置示例。
- `logging.*`：日志输出与滚动策略。

## 数据模型

| 表 | 作用 | 关键字段 |
| --- | ---- | -------- |
| `request_trace` | 记录每一次请求（请求 ID、线程信息、时间区间、HTTP 头）。 | `request_id`, `thread_id`, `start_time`, `end_time`, `http_headers` |
| `method_call` | 存储方法调用树中的节点。 | `request_trace_id`, `parent_call_id`, `sequence`, `depth`, `query` |
| `method_statistics` | 聚合统计方法调用次数、线程数及最后调用时间。 | `method_name`, `call_count`, `statistics_date` |

## 前端功能
- **仪表盘（`/`）**：周期刷新请求总数、活跃请求、方法调用总数。
- **追踪页面（`/tracking`）**：请求条件搜索、按 `requestId` 分组、线程展开、树形调用链展示（含 SQL 摘要），支持异步加载与折叠交互。

## 日志与监控
- Agent 采用 `slf4j` 打印调试日志，默认集成宿主应用日志体系。
- 服务端日志输出至控制台与 `stackcat-app.log`，可通过 `application.yml` 调整级别与滚动策略。

## 未来迭代功能
- 集成 DeepSeek / ChatGPT 等大模型，为调用链、统计数据、SQL 语句和索引策略提供自动诊断与优化建议。
- 扩展对 gRPC、Dubbo、Spring Cloud Gateway 等框架的拦截，覆盖跨服务链路与异步任务。
- 引入调用耗时分布、瓶颈识别、异常模式告警以及 SLA 趋势分析。
- 支持实时拓扑可视化、报告导出、多租户隔离与访问控制，满足团队协作与合规需求。
- 深度融合 OpenObserve，实现链路数据的集中采集、长周期检索与多维度关联分析。
