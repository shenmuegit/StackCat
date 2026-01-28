# StackCat

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**StackCat** 是一个开源的 Java 应用运行时调用链追踪与分析工具，采用 Java Agent 技术实现零侵入的方法调用监控，帮助开发者快速定位性能瓶颈、分析代码执行路径。

## ✨ 特性

### 🔍 深度追踪能力
- **零侵入监控**：基于 Java Instrumentation 和 ASM 字节码增强，无需修改业务代码
- **全链路追踪**：自动捕获 Controller、Service、Repository 等各层级方法调用
- **SQL 语句捕获**：支持 Spring Data JPA、MyBatis 和原生 JDBC 的 SQL 语句跟踪
- **调用树构建**：精确记录方法调用的父子关系和执行顺序

### 🚀 高性能设计
- **异步上报**：采用批量异步上报机制，最小化对业务性能的影响
- **灵活过滤**：支持包级别的 include/exclude 配置，精准控制监控范围
- **线程安全**：基于 ThreadLocal 实现线程隔离的调用链追踪

### 📊 可视化分析
- **实时仪表盘**：展示请求总数、活跃请求、方法调用统计等关键指标
- **调用链可视化**：树形结构展示完整的方法调用链，支持展开/折叠交互
- **请求搜索**：支持按请求 ID、时间范围等条件查询历史调用记录
- **SQL 关联展示**：在调用链中直接查看关联的 SQL 语句

### 🔗 可扩展性
- **OpenObserve 集成**：可结合 OpenObserve 实现链路数据的深度集中采集与分析
- **多框架支持**：内置对 Spring、MyBatis 等主流框架的适配
- **插件化设计**：支持自定义拦截器和数据上报策略

## 🏗️ 架构

```
┌─────────────────────────────────────────────────────────┐
│                   StackCat 架构                          │
└─────────────────────────────────────────────────────────┘

┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   应用进程    │  Agent   │  StackCat    │  上报   │  可视化界面   │
│              │ ────────►│   Agent      │ ──────►│              │
│ (被监控应用) │          │              │         │ (Web UI)     │
└──────────────┘          └──────────────┘         └──────────────┘
                                  │                        │
                                  │                        │
                                  ▼                        ▼
                          ┌──────────────┐         ┌──────────────┐
                          │  StackCat    │         │  PostgreSQL  │
                          │    App       │ ───────►│   数据库      │
                          │  (服务端)    │         │              │
                          └──────────────┘         └──────────────┘
                                  │
                                  │ (可选)
                                  ▼
                          ┌──────────────┐
                          │  OpenObserve │
                          │   (日志平台)  │
                          └──────────────┘
```

### 模块组成

```
stackcat-parent
├── stackcat-agent  # Java Instrumentation Agent，负责采集数据
└── stackcat-app    # Spring Boot 应用，负责接收、持久化与展示
```

- **stackcat-agent**：基于 `Instrumentation` 与 ASM，向目标 JVM 注入方法入口/出口、数据库调用等监控逻辑，并通过 `MethodTrackingBridge` 异步批量上报
- **stackcat-app**：Spring Boot 3 Web 应用，提供 REST API、Thymeleaf 前端以及 PostgreSQL 持久化存储

## 🚀 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- PostgreSQL 14+（或兼容版本）

### 构建项目

```bash
git clone <repository-url>
cd StackCat
mvn clean package
```

构建完成后：
- `stackcat-agent/target/stackcat-agent-<version>-agent.jar` - 带依赖的 Agent JAR
- `stackcat-app/target/stackcat-app-<version>.jar` - Spring Boot 可执行 JAR

### 数据库初始化

在 PostgreSQL 中执行初始化脚本：

```bash
psql -U postgres -d stackcat -f stackcat-app/src/main/resources/db/init.sql
```

或使用 psql 交互式执行：

```sql
\i stackcat-app/src/main/resources/db/init.sql
```

### 启动服务端

```bash
java -jar stackcat-app/target/stackcat-app-<version>.jar
```

默认监听 `http://localhost:8080`，访问 `http://localhost:8080` 查看仪表盘。

### 启动被监控应用

在启动你的 Java 应用时添加 Agent 参数：

```bash
java -javaagent:/path/to/stackcat-agent-<version>-agent.jar \
     -Dstackcat.filter.packages.include=com.yourcompany \
     -Dstackcat.agent.api.url=http://localhost:8080/api/tracking/batch \
     -jar your-application.jar
```

## ⚙️ 配置说明

### Agent 端系统属性

| 属性 | 说明 | 默认值 |
| ---- | ---- | ------ |
| `stackcat.filter.packages.include` | 仅跟踪指定包前缀，逗号分隔。为空时默认 `com.stackcat` | `com.stackcat` |
| `stackcat.filter.packages.exclude` | 排除特定包前缀，逗号分隔 | 空 |
| `stackcat.agent.api.url` | 上报目标地址 | `http://localhost:8080/api/tracking/batch` |
| `stackcat.agent.async.interval` | 异步发送时间间隔（毫秒） | `1000` |
| `stackcat.agent.async.batch-size` | 批量发送的最小请求数 | `10` |

### 应用端配置（`application.yml`）

主要配置项：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/stackcat
    username: postgres
    password: your-password

stackcat:
  filter:
    packages:
      include: ["com.yourcompany"]  # 与 Agent 侧保持一致
  agent:
    async:
      interval: 1000
      batch-size: 10
    api:
      url: http://localhost:8080/api/tracking/batch
```

完整的配置说明请参考 `stackcat-app/src/main/resources/application.yml`。

## 📊 数据模型

| 表 | 作用 | 关键字段 |
| --- | ---- | -------- |
| `request_trace` | 记录每一次请求（请求 ID、线程信息、时间区间、HTTP 头） | `request_id`, `thread_id`, `start_time`, `end_time`, `http_headers` |
| `method_call` | 存储方法调用树中的节点 | `request_trace_id`, `parent_call_id`, `sequence`, `depth`, `query` |
| `method_statistics` | 聚合统计方法调用次数、线程数及最后调用时间 | `method_name`, `call_count`, `statistics_date` |

详细的数据模型定义请参考 `stackcat-app/src/main/resources/db/init.sql`。

## 🎯 使用场景

- **性能分析**：识别慢查询、高频方法调用，定位性能瓶颈
- **问题排查**：通过调用链追踪快速定位异常发生的代码路径
- **代码理解**：可视化方法调用关系，帮助理解复杂的业务逻辑
- **接口治理**：统计接口调用情况，为接口优化提供数据支持
- **SQL 优化**：分析实际执行的 SQL 语句，发现潜在的数据库性能问题

## 📖 功能演示

### 仪表盘

访问 `http://localhost:8080` 查看实时统计：
- 总请求数
- 活跃请求数
- 总方法调用数

### 调用链追踪

访问 `http://localhost:8080/tracking` 进行调用链查询：
- 按请求 ID 搜索
- 按时间范围过滤
- 树形结构展示完整调用链
- 查看关联的 SQL 语句

## 🔧 高级功能

### 自定义包过滤

精确控制监控范围，避免外部依赖库的干扰：

```bash
-Dstackcat.filter.packages.include=com.example.service,com.example.controller
-Dstackcat.filter.packages.exclude=org.springframework,com.sun
```

### 批量上报调优

根据业务负载调整上报策略：

```bash
-Dstackcat.agent.async.interval=500    # 缩短上报间隔
-Dstackcat.agent.async.batch-size=20   # 增加批量大小
```

## 🤝 贡献指南

我们欢迎社区贡献！请遵循以下步骤：

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

### 开发环境设置

```bash
# 克隆仓库
git clone <repository-url>
cd StackCat

# 编译项目
mvn clean install

# 运行测试
mvn test
```

## 📝 License

本项目采用 MIT License。详见 [LICENSE](LICENSE) 文件。

## 🔮 未来规划

- **AI 智能分析**：集成 DeepSeek / ChatGPT 等大模型，为调用链、统计数据、SQL 语句和索引策略提供自动诊断与优化建议
- **多框架支持**：扩展对 gRPC、Dubbo、Spring Cloud Gateway 等框架的拦截，覆盖跨服务链路与异步任务
- **性能监控**：引入调用耗时分布、瓶颈识别、异常模式告警以及 SLA 趋势分析
- **企业功能**：支持实时拓扑可视化、报告导出、多租户隔离与访问控制
- **OpenObserve 集成**：深度融合 OpenObserve，实现链路数据的集中采集、长周期检索与多维度关联分析

## 💬 支持与反馈

- 提交 Issue：[GitHub Issues](https://github.com/your-repo/StackCat/issues)
- 讨论区：[GitHub Discussions](https://github.com/your-repo/StackCat/discussions)

## 🙏 致谢

感谢所有为本项目做出贡献的开发者和用户！

---

**StackCat** - 让 Java 调用链追踪变得简单 ✨
