# 变更日志

本文件记录本项目的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，
版本号遵循[语义化版本](https://semver.org/spec/v2.0.0.html)。

## [2.0.0] - 2026-08-15

一次以生产级正确性、跨数据库支持和开源工程标准为主题的大版本，
包含若干下方单独列出的**破坏性变更**。

### 新增

#### 跨数据库抽象
- 新增 `Dialect` SPI 以及 `DefaultDialect`、`MySqlDialect`、`PostgresDialect`、
  `H2Dialect` 实现。`LIMIT/OFFSET` 与主键冲突检测现在具备方言感知能力。
- 新增 `NamingStrategy` SPI（`DefaultNamingStrategy` / `IdentityNamingStrategy`），
  在各 Builder 与 Mapper 之间产生一致的物理列名。
- `EntityModel` / `EntityModelRegistry` 只缓存一次实体元数据，
  并可在完整继承层级上解析字段。
- 新增 `GenerationType.SEQUENCE`、`GenerationType.TABLE`、`GenerationType.UUID`
  （此前仅有 `AUTO` / `IDENTITY`）。

#### 可扩展的类型系统
- `TypeHandler` 改为可注册的注册表（`TypeHandler.register(...)`）。
  内置处理器扩展到 `BigDecimal`、`BigInteger`、`float`、`byte[]`、
  `LocalDate`、`LocalDateTime`、`LocalTime`、`OffsetDateTime`、`UUID` 与 `Enum`。

#### 事务
- `SpringAfterCommitSupport` 将 after-commit 回调路由到 Spring 的
  `TransactionSynchronizationManager`，消除了 `@Transactional` 下的缓存不一致。
- `TransactionManager`、`TransactionTemplate` 与 Spring 之间统一了事务传播语义。

#### 新的 API 面
- `JormTemplate`：可注入、DI 友好的入口，取代静态 `new FindSession()` 反模式。
- `Page<T>` / `Pageable` / `Sort` 用于分页查询。
- 通过 `find(Class<D> dto, ...)` 支持 DTO / 投影映射。
- `findStream(Class<T>)` 流式读取大结果集而不 OOM。
- 每个 Session 可配置 `queryTimeoutSeconds` 与 `fetchSize`。
- `QueryStatistics` / `CacheStatistics` 提供命中/未命中/耗时计数器，
  并提供可选的 Spring Boot `HealthIndicator`。

#### 错误
- 类型化异常层级：`EmptyResultException`、`NonUniqueResultException`、
  `DuplicateKeyException`、`OptimisticLockingException`、
  `DataIntegrityException`、`CacheException`（均携带对应的 `ErrorCode`）。

#### Java 规范
- 所有 Session 提供 camelCase 链式别名（`where`、`find`、`orderBy`、`groupBy`...），
  旧 PascalCase 方法保留但标记 `@Deprecated`，将在 3.0 移除。

#### 社区 / 工程
- 仓库根目录提供 `LICENSE`、`CONTRIBUTING.md`、`CODE_OF_CONDUCT.md`、
  `SECURITY.md`、`MAINTAINERS.md`。
- CI 基于 GitHub Actions，配合 Dependabot、Issue 模板、PR 模板。
- 构建强制执行 Checkstyle、Spotless、JaCoCo、SpotBugs。
- 使用 `@API Guardian` 注解将每个公开类型标记为 `STABLE`、
  `EXPERIMENTAL` 或 `INTERNAL`。

### 变更

- `Jorm.dataSource` 改为 `volatile`；配置错误抛出 `JormException`
  而不是未定义的 `NullPointerException`。
- `Limit(n)` 在设置了偏移量时通过当前 `Dialect` 生成 `LIMIT ? OFFSET ?`。
- 各 Session 显式暴露 `QueryOptions`（timeout、fetchSize），
  不再静默依赖 JDBC 默认值。
- `SaveSession.batchSave` 按 `jorm.jdbc.batch-size`（默认 100）分块插入，
  避免超过 `max_allowed_packet`。
- `RedisSecondLevelCache.clearRegion` / `clearAll` 改用 `SCAN`
  （游标删除）代替 `KEYS`。
- Starter 在未显式设置 `jorm.dialect` 时，根据 JDBC URL 自动选择默认方言。

### 修复

- 列名大小写策略现在在 `FindBuilder`、`SaveBuilder`、`UpdateBuilder`、
  `DeleteBuilder`、`ResultSetMapper` 与 `EntityHelper` 之间保持一致——
  统一由 `NamingStrategy` 驱动。
- `UpdateBuilder` 与 `DeleteBuilder` 现在与 `FindBuilder` 一样，
  按实体白名单校验列名与操作符。
- 从发布的 jar 中移除了 `Main.java` 的 "Hello world" 占位类。
- `application.yml` 中文注释不再乱码（改以 UTF-8 编码）。
- 测试 logback 日志文件名由 `tlias-*.log` 规范化为 `jorm-test-*.log`。
- `junit` / `junit-jupiter` 不再使用已弃用的 `RELEASE` 版本号。

### 弃用

- PascalCase Session 方法（`Where`、`Find`、`Save`、`Update`、`Delete`、
  `Select`、`Having`、`Group`、`Order`、`Limit`、`Model`）。3.0 移除。
- `@Aggregation` 更名为 `@Transient`（JPA 标准术语）；
  旧注解作为弃用别名保留。
- 静态 `new FindSession()` 构造模式；推荐改用 `JormTemplate`
  或 `Jorm.findSession()`。

### 移除

- `Main.java` 占位类。
- `BaseSession` 上未使用的 `firstLevelCache` 字段（后续 2.x 由
  `EntityModelRegistry` 支撑的缓存取代）。

### 破坏性变更

- **Maven 坐标变更**：命名空间由 `io.github.foreverstr:jorm:1.0.8`
  迁移到 `io.github.jadendu:jorm-core:2.0.0`（groupId *和* artifactId 均变更），
  原因是 GitHub 账户迁移到 `JadenDu/jorm`，且 Sonatype Central Portal 的
  命名空间在 `io.github.jadendu` 下验证。从 1.x 升级的用户必须同时更新
  POM 中的 `<groupId>` 与 `<artifactId>`；由于 1.x 未在新命名空间下发布，
  不存在自动的平滑升级路径。
- Java 包名由 `io.github.foreverstr.*` 迁移到 `io.github.jadendu.*`，
  与新的 Maven 坐标对齐。用户代码中的 `import io.github.foreverstr.*`
  语句必须改名为 `io.github.jadendu.*`。Starter 的自动装配类已在
  `META-INF/spring/...AutoConfiguration.imports` 重新注册并自动生效；
  用户只需更新 starter 依赖，无需其他操作。
- `SaveSession.save` 在**所有**方言下遇到主键冲突均抛出
  `DuplicateKeyException`（`JormException` 的子类）。
- `FindSession.findOne` 抛出 `NonUniqueResultException` 和
  `EmptyResultException`，而不是返回 `null`/静默取第一行。
- `DeleteBuilder.batchDelete` 参数类型由 `List<Object>` 改为 `List<?>`，
  以反映真实意图。
- `jorm.jdbc.batch-size` 默认值降到 100（此前不设上限）。
- Logger 名称统一收敛到 `io.github.jadendu.*`。

### 2.0.0 发布后补丁（未发布，随下一个版本归档）

- 修复 CI（Windows runner）：workflow 中 `-D` 参数在 PowerShell 下被按
  `=` 拆分导致 `Unknown lifecycle phase` 错误，所有 `-D` 参数加双引号。
- 修复 CI（Linux runner）：嵌入式 Redis 在非 Windows 平台不再下发
  Windows 专有的 `maxheap` 配置指令。
- 修复 CI：integration job 增加 MySQL 服务容器与建表步骤；
  CodeQL job 改为从根聚合 pom 构建（`-f` 子模块方式导致
  `jorm-parent` 无法进入本地仓库而被依赖解析拒绝）。
- 修复 Spring 事务集成：裸 `new FindSession()` 等自动管理连接的 Session
  不再在 Spring `@Transactional` 内强制设置 `autoCommit=true`，
  回滚得以正确执行（此前报 `Can't call rollback when autocommit=true`）。
- 修复事务管理器绑定：Starter 的 `DataSourceTransactionManager` 显式
  绑定原始 DataSource（而非 `TransactionAwareDataSourceProxy`），
  使 Session 连接能真正加入 Spring 事务。
- 修复 L2 缓存一致性：事务内的查询不再读取或写入共享二级缓存，
  避免未提交数据泄漏到缓存。
- 修复测试隔离：`MySQLJormTest` 不再污染全局 `Jorm` DataSource 与缓存开关。
- 发布链路：`jorm-parent`（packaging=pom）根 pom 补充 GPG 签名插件，
  修复 Central 校验报 `Missing signature for jorm-parent-2.0.0.pom`；
  `central-publishing-maven-plugin` 升级到 0.11.0
  （0.4.0 解析 Central API 新增的 `warnings` 字段时直接崩溃）。

## [1.0.8] - 2024-

首个 Maven Central 发布版本。旧功能集见当时的 `README.md`。

[2.0.0]: https://github.com/JadenDu/jorm/releases/tag/v2.0.0
