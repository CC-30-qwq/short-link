# 短链接生成平台（short-link-platform）

一个基于 Spring Boot 3 的高性能短链接服务：接收长 URL，生成短链码，访问时 302 跳转到原始地址，并异步统计访问量。

## 技术栈

| 分类 | 技术 |
|---|---|
| 框架 | Spring Boot 3.2.5、Java 17 |
| 持久层 | MyBatis-Plus 3.5.5 + MySQL 8 |
| 缓存 / 分布式组件 | Redis + Redisson（布隆过滤器、令牌桶限流） |
| API 文档 | Knife4j / SpringDoc OpenAPI 3 |
| 工具 | Lombok、Hutool |
| 测试 | JUnit 5 + Mockito + AssertJ + JaCoCo（覆盖率门禁 50%） |

## 核心特性

- **短链生成**：雪花算法分布式 ID → Base62 编码生成短码，带碰撞重试兜底。
- **URL 防重**：先算 MD5，走「缓存 → 库 → 新建」三级判断，同一 URL 只生成一次。
- **跳转查询**：布隆过滤器 → Redis 缓存 → MySQL 三级查询，命中即 302 重定向，支持过期时间校验。
- **缓存降级**：Redis 不可用时自动回退到内存缓存，保证服务可用。
- **布隆过滤器启动预加载**：应用启动时把库中已有有效短码回填到布隆过滤器，避免重启后历史短链被误判 404。
- **限流**：Redisson 令牌桶，Redis 不可用或开关关闭时自动放行。
- **异步访问日志**：独立线程池异步记录 IP / UA / Referer，不阻塞跳转主流程。
- **访问统计**：查询某短码的访问总数、最近访问时间等。

## 项目结构

```
src/main/java/com/example/
├── shortlink/                 # 主体：短链接平台
│   ├── common/                # 常量、统一返回 Result
│   ├── config/                # MyBatis-Plus / Redis / Redisson / 异步 / Knife4j 配置
│   ├── controller/            # REST 接口
│   ├── dto/                   # 请求/响应对象
│   ├── entity/                # 数据库实体
│   ├── exception/             # 错误码、业务异常、全局异常处理
│   ├── manager/               # 缓存 / 布隆 / 限流管理器
│   ├── mapper/                # MyBatis-Plus Mapper
│   ├── service/               # 业务接口与实现
│   └── util/                  # Base62、雪花 ID
├── billing/                   # 演示：计费账户（幂等扣费 + 不超扣）
└── material/                  # 演示：素材秒传（并发去重）
```

仓库其余目录为独立的 Python / DevOps 训练与演示代码，与 Java 主体**无运行时关联**，可各自单独运行：

| 目录 | 内容 |
|---|---|
| `pytest_demo/` | pytest 单元测试示例：计费边界、用户存储、短链工具，含 `conftest.py` fixture 与 Dockerfile |
| `real_world_task/` | 贴近真实场景的测试练习：计费服务与文件上传服务的测试用例 |
| `celery_demo/` | Celery 异步任务：队列机制、队列管理、8 种常见操作演示、部署脚本 |
| `monitor_demo/` | 监控与告警：Prometheus 指标暴露、告警规则、Celery 队列监控、告警模拟与 webhook 接收 |
| `ci_examples/` | CI 配置示例：`Jenkinsfile`、GitLab CI 质量门禁 |
| `devops-training/` | DevOps 练习：健康检查脚本 |
| `scripts/` | 运维脚本合集：缓存演示、日志压缩与轮转、配置 API、HTTP 客户端、Mock 服务、端口探测、进程守护、发布脚本 |

## 快速开始

### 1. 环境准备

- JDK 17
- Maven 3.6+
- MySQL 8.0（默认 `root / 123456`）
- Redis（可选：`shortlink.redis.enabled=false` 时可离线运行，缓存/限流/布隆自动降级为内存实现）

### 2. 建库建表

连接 MySQL 后执行 [src/main/resources/sql/schema.sql](src/main/resources/sql/schema.sql)，或直接启动应用（连接串带 `createDatabaseIfNotExist=true` 会自动建库，但**建表仍需手动执行 schema.sql**）。

### 3. 配置

数据库、Redis、限流、布隆等参数都在 [src/main/resources/application.yml](src/main/resources/application.yml)，按需修改：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/short_link_db?...
    username: root
    password: 123456

shortlink:
  redis:
    enabled: true          # false 时缓存/限流/布隆全部降级为内存实现
  domain: http://localhost:8080
  rate-limit:
    enabled: true
    permits-per-second: 100
  bloom-filter:
    expected-insertions: 1000000
    false-probability: 0.01
```

### 4. 启动

```bash
mvn spring-boot:run
# 或打包后运行
mvn clean package -DskipTests
java -jar target/short-link-platform-1.0.0.jar
```

访问：

- 前端页面：http://localhost:8080/
- API 文档（Knife4j）：http://localhost:8080/doc.html
- API 文档（Swagger UI）：http://localhost:8080/swagger-ui.html

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/url/shorten` | 生成短链接，body：`{"originalUrl": "...", "expireTime": "可选"}` |
| GET | `/{shortCode}` | 短链跳转，302 重定向到原始 URL |
| GET | `/api/v1/url/statistics?shortCode=xxx` | 查询访问统计 |

## 测试与 CI

```bash
mvn clean verify   # 编译 + 单元测试 + JaCoCo 覆盖率门禁（< 50% 会失败）
mvn clean test     # 仅跑单元测试
```

GitHub Actions（[.github/workflows/ci.yml](.github/workflows/ci.yml)）在 push / PR 时自动执行 `mvn clean verify` 作为质量门禁。

## Docker 部署

- [Dockerfile](Dockerfile)：多阶段构建（Maven 编译 → JRE 运行）。
- [docker-compose.yml](docker-compose.yml)：应用 + Redis，通过 `APP_VERSION` 环境变量切换镜像版本实现发布/回滚。

```bash
APP_VERSION=v1 docker compose up --build
```
