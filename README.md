# JORM — Java ORM 框架

[![Maven Central](https://img.shields.io/maven-central/v/io.github.jadendu/jorm-core.svg)](https://search.maven.org/artifact/io.github.jadendu/jorm-core)
[![Build](https://img.shields.io/github/actions/workflow/status/JadenDu/jorm/ci.yml?branch=main&label=ci)](https://github.com/JadenDu/jorm/actions/workflows/ci.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/JadenDu/jorm/codeql.yml?branch=main&label=codeql)](https://github.com/JadenDu/jorm/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt)
[![JDK](https://img.shields.io/badge/JDK-11%2B-orange.svg)](#版本要求)

JORM 是一个受 Go 语言 [GORM](https://gorm.io) 启发的轻量级 Java ORM 框架：框架中性的核心模块加上可选的 Spring Boot Starter。2.0 是一次面向生产级的完整重构，新增便携的 SQL 方言、类型化异常、分页查询、DI 友好的模板入口、查询统计，以及 Spring Boot Actuator 健康指标。

---

## 项目结构

单仓库聚合构建，三个构件（`jorm-parent` + `jorm-core` + `jorm-spring-boot-starter`）随同一个 release 流水线签名发布到 Maven Central：

```
jorm/
├── pom.xml                    # 聚合器 jorm-parent: 统一依赖/插件管理与发布
├── jorm-core/                 # 核心框架 (JDBC + SLF4J, 零运行期框架依赖)
├── jorm-spring-boot-starter/  # Spring Boot 2.7 自动装配 (HikariCP/事务/Redis 缓存)
└── build-tools/               # Checkstyle / SpotBugs 共享配置
```

## 快速开始

### 添加依赖

**核心框架**（`jdbc + SLF4J`，零运行期依赖）：

```xml
<dependency>
  <groupId>io.github.jadendu</groupId>
  <artifactId>jorm-core</artifactId>
  <version>2.0.0</version>
</dependency>
```

**Spring Boot 集成**（自动装配 HikariCP、方言、二级缓存）：

```xml
<dependency>
  <groupId>io.github.jadendu</groupId>
  <artifactId>jorm-spring-boot-starter</artifactId>
  <version>2.0.0</version>
</dependency>
```

### 定义实体

```java
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_name", nullable = false)
    private String name;

    private int age;
    private String status;

    @Transient  // 非持久化字段：承接 SELECT SUM/COUNT 的聚合结果
    private int totalAge;

    // 构造方法 / getter / setter
}
```

### 使用 camelCase API

（camelCase 是后续推荐风格；PascalCase 仍然兼容，但已 `@Deprecated`，计划在 3.0 移除。）

```java
try (FindSession s = new FindSession()) {
    List<User> users = s
        .select("id, user_name, age, status")
        .where("age", ">", 18)
        .where("status", "active")
        .orderBy("age DESC")
        .limit(10)
        .find(User.class);
}
```

`Jorm` 静态工厂同样可以创建各类 Session：

```java
try (SaveSession s = Jorm.saveSession()) {
    s.save(new User("Alice", 30, "active"));
}
```

### 或者用 JormTemplate（Spring 中注入）

```java
@Service
class UserService {
    private final JormTemplate jorm = new JormTemplate();

    public Page<User> page(Pageable pageable) {
        return jorm.findPage(User.class, pageable);
    }

    public Optional<User> findOne(Long id) {
        return jorm.findOne(s -> s.where("id", id).findOne(User.class));
    }

    @Transactional
    public void promote(Long id, int newAge) {
        jorm.update(s -> s.model(User.class).where("id", id).set("age", newAge));
    }
}
```

---

## 2.0 主要特性

### 跨数据库可移植性（`Dialect` SPI）
- `Dialect` 接口 + `MySqlDialect` / `PostgresDialect` / `H2Dialect` / `DefaultDialect` 覆盖 LIMIT/OFFSET 与主键冲突检测的差异；`Dialects.forUrl(jdbcUrl)` 按 URL 自动选择，Starter 装配时自动生效。
- 实体名/列名解析由可注入的 `NamingStrategy` 控制（`default` 蛇形命名 + 表名复数化，`identity` 原样透传，或自定义实现的全限定类名）。
- 主键生成策略扩展至 `GenerationType.SEQUENCE` / `TABLE` / `UUID`。

### 类型化异常
- 新增 `EmptyResultException`、`NonUniqueResultException`、`DuplicateKeyException`、`OptimisticLockingException`、`DataIntegrityException`、`CacheException`，均携带原有 `ErrorCode`。按用途 catch 而不是字符串匹配统一异常。

### 更丰富的查询类型
- `JormTemplate` — 可注入、try-with-resources 安全的入口。
- `Page<T>`、`Pageable`、`Sort` — 一行方法完成分页查询。
- `findOne` — 在零行时抛 `EmptyResultException`，多行时抛 `NonUniqueResultException`（语义对齐 Spring Data）。
- `findStream` — 游标式逐行流式读取，不 OOM。
- 每个 Session 都支持 `queryTimeout` / `fetchSize`。
- 批量插入默认按 `jorm.jdbc.batch-size`（默认 100）分块，避免突破 MySQL `max_allowed_packet` 边界。

### 缓存集成真正"提交后才失效"
- `AfterCommitHooks` 在 Spring 事务环境下通过 `TransactionSynchronizationManager.registerSynchronization` 注册回调，修复了 1.x 中"保存操作清缓存时机早于事务提交"的脏读缺陷；在 JORM 自管理事务（`TransactionTemplate` / `TransactionManager`）中通过本地回调队列实现同样的语义。
- 事务内的查询不读也不写共享 L2 缓存，避免未提交数据泄漏到缓存。
- 在 Spring `@Transactional` 中，裸 `new SaveSession()` 等无连接构造器会自动加入当前 Spring 事务（Starter 通过 `TransactionAwareDataSourceProxy` 装配）。

### 统计指标
- `StatisticsRegistry.query()` / `.cache()` 暴露原子计数与命中率；可选的 `JormHealthIndicator` 通过 Spring Boot Actuator 暴露（`/actuator/health/jorm`）。

### 工程级质量门
- `mvn verify` 强制执行 Spotless（Google Java Format, AOSP 风格）、Checkstyle、SpotBugs、JaCoCo 覆盖率门。
- CI 在 ubuntu + windows 上跑 JDK 11/17/21 矩阵构建；MySQL 集成测试由 CI 的服务容器提供；CodeQL 每周扫描；签名发布流水线在 `.github/workflows/` 中。

完整变更见 [CHANGELOG.md](CHANGELOG.md)。

---

## 配置

### 独立使用（无 Spring）

```java
// 1. 启动时注入一次 DataSource。
Jorm.setDataSource(myHikariDataSource);

// 2. 显式选方言，或依赖按 URL 自动检测。
Jorm.setDialect(Dialects.forUrl(myJdbcUrl));

// 3. 如有需要调整批量与采样参数。
Jorm.setBatchSize(200);

// 4. 用任何 Session 都通过 try-with-resources。
try (SaveSession s = new SaveSession()) {
    s.save(new User("Alice", 30, "active"));
}
```

### Spring Boot

```yaml
jorm:
  jdbc-url: jdbc:mysql://localhost:3306/orm
  username: root
  password: root
  driver-class-name: com.mysql.cj.jdbc.Driver
  maximum-pool-size: 10
  minimum-idle: 2
  batch-size: 200
  # 方言可省略；从 jdbc-url 自动识别
  dialect: MySQL            # MySQL / PostgreSQL / H2 / Default
  naming-strategy: default  # default | identity | 实现类全限定名

  cache:
    redis:
      enabled: true
      default-expiration: 3600
      key-prefix: "jorm:cache:"
      use-key-prefix: true
      cache-null-values: false
```

Redis 连接沿用 Spring Boot 标准的 `spring.redis.*` 配置。

---

## 事务样式

```java
// (1) 隐式：auto-managed 连接，在未加入事务时自动提交。
try (SaveSession s = new SaveSession()) {
    s.save(user1);
    s.save(user2);
}

// (2) 手动低级：用于核心模块。
//     注意：TransactionManager 维护自己的 ThreadLocal，不会发布到
//     CurrentTransactionConnection。因此必须把 conn 显式传入会话构造器，
//     否则裸 new SaveSession() 会拿到另一条自动提交的连接，无法加入事务。
Connection conn = TransactionManager.begin();
try (JormSession s = new JormSession(conn)) {
    s.saveSession().save(user1);
    s.saveSession().save(user2);
    TransactionManager.commit();
} catch (RuntimeException e) {
    TransactionManager.rollback();
    throw e;
} finally {
    TransactionManager.release();
}

// (3) 闭包 / 模板。
new TransactionTemplate().execute(() -> {
    try (SaveSession s = new SaveSession()) {
        s.save(user1);
        s.save(user2);
    }
    return null;
});

// (4) Spring 声明式。
@Transactional
public void batchOperation() {
    try (FindSession f = new FindSession();
         UpdateSession u = new UpdateSession()) {
        // 查询/更新都在同一事务内；回滚由 Spring 接管
    }
}
```

---

## 类型与列支持

`TypeHandler` 内置注册表式处理器覆盖：

`int/Long/Boolean/Short/Double/Byte/Float`（基本类型 + 包装类）、`String`、`BigDecimal`、`BigInteger`、`byte[]`、`Clob`/`Blob`/`Reader`/`InputStream`、`java.util.Date`、`java.sql.{Date,Time,Timestamp}`、`LocalDate`、`LocalDateTime`、`LocalTime`、`OffsetDateTime`、`UUID`、以及 `Enum`。

新增自定义类型只需一行：

```java
TypeHandler.register(Money.class, (rs, col, type) -> Money.of(rs.getBigDecimal(col)));
```

---

## 构建与本地开发

```bash
# 完整构建（含形式检查 + 覆盖率门）
mvn clean verify

# 只跑 core 测试（自包含，基于 H2，无需外部环境）
mvn -pl jorm-core -am test

# 跑 starter 集成测试（需要本地 MySQL 与 Redis，见下）
mvn -pl jorm-spring-boot-starter verify
```

starter 集成测试的环境要求：

- **MySQL**：`localhost:3306`，账号 `root/root`，库 `orm`。测试用的表结构：

  ```sql
  CREATE DATABASE IF NOT EXISTS orm DEFAULT CHARACTER SET utf8mb4;
  USE orm;
  CREATE TABLE IF NOT EXISTS users (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        user_name VARCHAR(255),
        age INT,
        status VARCHAR(255),
        department VARCHAR(255)
  );
  ```

- **Redis**：无需安装——测试框架会自动拉起嵌入式 Redis（Windows 环境自动附加 `maxheap` 配置）。

CI 上由 `integration` job 提供 MySQL 服务容器，无需任何本地资源。

---

## 版本要求

- Java 11+（CI 矩阵覆盖 11 / 17 / 21）
- Spring Boot 2.7.x（可选，仅使用 starter 需要）
- 数据库：MySQL 5.7+ / 8.x、PostgreSQL 10+、H2 2.x

## 从 1.x 迁移

完整变更与不兼容项见 [CHANGELOG.md](CHANGELOG.md)。TL;DR：

- PascalCase 链式方法在 2.x 已弃用。现有代码仍可工作；切换到 camelCase 等效方法时一并删除。
- `@Aggregation` → `@Transient`（原注解作为弃用别名保留至 3.0）。
- `SaveSession.save` 在**所有**方言下冲突时抛 `DuplicateKeyException`。如果你之前的代码 `try { s.save(...) } catch (JormException e)` 后比对 `ErrorCode.DUPLICATE_KEY` — 行为等价；改成 `catch (DuplicateKeyException e)` 更清晰。
- Maven 坐标：`io.github.foreverstr:jorm:1.0.8` → `io.github.jadendu:jorm-core:2.0.0`（groupId、artifactId 都变了，因为 GitHub 账户已更名；旧坐标不再维护）。1.x 时代 core 与 starter 分仓库分别发布，2.0 起合并到本仓库统一聚合发布。

## 社区

- Issues：https://github.com/JadenDu/jorm/issues
- Discussions：https://github.com/JadenDu/jorm/discussions
- 安全披露：见 [SECURITY.md](SECURITY.md)（请**勿**以公开 Issue 报告安全问题）。
- 贡献指南：[CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可证

Apache License 2.0 — 详见 [LICENSE](LICENSE)。

---

**JORM** — 让 Java 持久化更简单。
