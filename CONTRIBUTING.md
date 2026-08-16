# 贡献指南

首先——感谢你抽出时间为 JORM 做贡献！
JORM 是社区驱动的项目，每一个 Issue、PR 或反馈都算数。

本文说明如何搭建项目、我们遵循的约定，以及评审流程中可以期待什么。

## 1. 行为准则

参与本项目受[行为准则](CODE_OF_CONDUCT.md)约束。
参与即表示你同意遵守其条款。请将不可接受的行为报告到
`wy1903265502@163.com`。

## 2. 仓库结构

```
jorm-core/                  # 核心 ORM（纯 JDBC + SLF4J，零框架依赖）
jorm-spring-boot-starter/   # Spring Boot 自动装配、HikariCP、Redis 二级缓存
.github/                    # CI workflow、Issue 模板、dependabot 配置
build-tools/                # 共享的 Checkstyle / SpotBugs 配置
```

三个 Maven 构件（`jorm-parent` + `jorm-core` + `jorm-spring-boot-starter`）
通过根聚合 pom 一起构建、随同一个发布流水线签名发布到 Maven Central，
但它们是三个独立的 GAV，使用方可以按需单独引用。

## 3. 构建与测试

环境要求：**JDK 11+**，**Maven 3.8+**。

```bash
# 完整构建（含测试、形式检查、覆盖率门），从仓库根目录一次完成
mvn clean install -Dgpg.skip=true

# 只跑 core 测试（基于 H2，自包含，无需外部环境）
mvn -pl jorm-core -am test

# 跑 starter 集成测试（需要本地 MySQL，见下）
mvn -pl jorm-spring-boot-starter verify
```

starter 集成测试会自动拉起嵌入式 Redis（Windows 环境自动附加
`maxheap` 配置），但需要本地 MySQL：`localhost:3306`、账号 `root/root`、
库 `orm`（建表 SQL 见 `README.md`）。CI 上由 `integration` job 的
MySQL 服务容器提供，无需任何本地资源。

## 4. 推送之前

以下 CI 质量门在每次 push 和 PR 时运行。请先在本地跑一遍：

| 检查项 | 命令 |
|-------|------|
| 代码格式检查（Spotless） | `mvn spotless:check` |
| 代码格式自动修复 | `mvn spotless:apply` |
| 静态分析（Checkstyle） | `mvn checkstyle:check` |
| 缺陷检查（SpotBugs） | `mvn spotbugs:check` |
| 覆盖率门（JaCoCo） | `mvn jacoco:check` |
| 测试 | `mvn test` |

Spotless 使用 Google Java Format 规范（AOSP 风格，4 空格缩进）。

## 5. 编码约定

- 两个模块均为 **Java 11 语言级别**。不要引入面向 17/21 的 `var`、
  record、switch 表达式或文本块。
- 所有公开 API 必须标注 `@org.apiguardian.api.API(status=...)`。
  - `@API(status = STABLE)` — 稳定的公开契约。
  - `@API(status = EXPERIMENTAL)` — 不稳定，可能变化。
  - `@API(status = INTERNAL)` — 实现细节，不供使用。
- 新方法一律使用 **camelCase**。PascalCase 链式方法已 `@Deprecated`
  并将在 3.0 移除；不要再新增。
- 所有新异常继承 `JormException` 并配套一个 `ErrorCode` 条目。
  每条 `log.error(...)` 都要用 `[ErrorCode=xxxx]` 携带错误码。
- 字段命名遵循当前 `NamingStrategy`；实体必须在默认蛇形命名策略下
  无需配置即可工作。
- 避免对 `ResultSet` 读取做未检查的强转。任何不支持的类型应新增
  `TypeHandler.register(...)` 注册项。
- 不要在 `INFO` 级别打印完整 SQL。SQL 与参数绑定用 `DEBUG`；
  `INFO` 留给生命周期事件。
- **不要**在任何地方使用 `redisTemplate.keys(...)`。新代码必须使用
  基于 `SCAN` 的遍历。

## 6. 提交信息

我们使用 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(scope): <subject>

<body>

<footer>
```

- `type`：`feat`、`fix`、`docs`、`style`、`refactor`、`perf`、`test`、
  `build`、`ci`、`chore`、`deps`。
- `scope`：`core`、`starter`、`dialect`、`cache`、`tx`、`docs`、
  `build` 等。
- `subject`：祈使语气，≤ 72 字符，结尾不加句号。
- **破坏性变更**必须在 footer 用 `BREAKING CHANGE:` 标注，
  或在 scope 后加 `!`（如 `feat(tx)!: ...`）。

示例：

```
feat(dialect): add OracleDialect with ROWNUM limit clause
fix(cache): clear region after Spring transaction commit
docs(readme): document batch-size configuration property
```

## 7. Pull Request

- PR 目标分支为 `main`。
- 在 PR 描述中关联 Issue：`Closes #123`。
- 保持 PR 聚焦——一个 PR 一个逻辑变更。除非不可避免，
  不要把重构与功能混在一个 PR 里。
- 在 `CHANGELOG.md` 的 `Unreleased` 条目下补充说明。
- 新增公开 API ⇒ 新的 Javadoc，以及 `jorm-core/src/test`（H2）
  或 starter 测试源集下的新测试。
- 等待 `ci` 相关检查通过。合并前需要维护者批准
  （琐碎修复一人批准即可）。

## 8. 发布

发布仅由维护者执行。

1. 通过 release workflow 的版本号参数（或 `versions:set -N`）统一调整版本；
   子模块通过 `<parent>` 继承，自动跟随。
2. 将 `CHANGELOG.md` 的 `Unreleased` 块整理为带日期的 `[x.y.z]` 小节。
3. 创建签名 tag `vx.y.z`。
4. 流水线从根聚合 pom 一次性发布三个 GAV 到 Maven Central
   （`central-publishing-maven-plugin`，GPG 签名 + sources + javadoc）。

## 9. 获取帮助

- Issues：https://github.com/JadenDu/jorm/issues
- 邮箱：wy1903265502@163.com
- 回复可能较慢——请保持耐心与尊重。

— JORM 团队
