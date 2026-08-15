# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-08-15

A major release focused on production-grade correctness, cross-database
support, and open-source engineering standards. Includes several
**breaking changes** documented below.

### Added

#### Cross-database abstraction
- New `Dialect` SPI and `DefaultDialect`, `MySqlDialect`, `PostgresDialect`,
  `H2Dialect` implementations. `LIMIT/OFFSET` and primary-key-conflict
  detection are now dialect-aware.
- `NamingStrategy` SPI (`DefaultNamingStrategy` / `IdentityNamingStrategy`)
  producing consistent physical column names across builders and mappers.
- `EntityModel` / `EntityModelRegistry` cache entity metadata once and
  resolve fields across the full inheritance hierarchy.
- `GenerationType.SEQUENCE`, `GenerationType.TABLE`, `GenerationType.UUID`
  added (previously only `AUTO` / `IDENTITY`).

#### Extensible type system
- `TypeHandler` is now a registrable registry
  (`TypeHandlerRegistry.register(...)`). Built-in handlers extended to
  `BigDecimal`, `BigInteger`, `float`, `byte[]`, `LocalDate`,
  `LocalDateTime`, `LocalTime`, `OffsetDateTime`, `UUID`, and `Enum`.

#### Transactions
- `SpringTransactionSynchronization` routes `doAfterCommit` callbacks
  to Spring's `TransactionSynchronizationManager` when running under
  Spring, eliminating cache inconsistency under `@Transactional`.
- Unified transaction-propagation semantics across
  `TransactionManager`, `TransactionTemplate`, and Spring.

#### New API surface
- `JormTemplate`: an injectable, DI-friendly entry point that replaces
  the static `new FindSession()` anti-pattern.
- `Page<T>` / `Pageable` / `Sort` for paginated queries.
- DTO / projection mapping via `find(Class<D> dto, ...)`.
- `findStream(Class<T>)` for streaming large result sets without OOM.
- Configuration of `queryTimeoutSeconds` and `fetchSize` per session.
- `QueryStatistics` / `CacheStatistics` with hit/miss/time counters and
  an optional Spring Boot `HealthIndicator`.

#### Errors
- Typed exception hierarchy: `EmptyResultException`,
  `NonUniqueResultException`, `DuplicateKeyException`,
  `OptimisticLockingException`, `DataIntegrityException`,
  `CacheException` (each carrying the existing `ErrorCode`).

#### Java conventions
- camelCase chainable aliases (`where`, `find`, `orderBy`, `groupBy`, ...)
  across all sessions alongside the legacy PascalCase methods, which are
  now `@Deprecated` for removal in 3.0.

#### Community / engineering
- `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`,
  `MAINTAINERS.md` shipped at the repository root.
- CI builds via GitHub Actions, Dependabot, issue templates, PR template.
- Checkstyle, Spotless, JaCoCo, and SpotBugs enforced in the build.
- `@API Guardian` annotations mark each public type as `STABLE`,
  `EXPERIMENTAL`, or `INTERNAL`.

### Changed

- `Jorm.dataSource` is now `volatile`; configuration errors raise a
  `JormException` instead of an unspecified `NullPointerException`.
- `Limit(n)` now emits `LIMIT ? OFFSET ?` when an offset is set, via
  the active `Dialect`.
- Each session exposes `QueryOptions` (timeout, fetchSize) instead of
  silently relying on JDBC defaults.
- `SaveSession.batchSave` chunks inserts at `jorm.jdbc.batch-size`
  (default 100) to avoid exceeding `max_allowed_packet`.
- `RedisSecondLevelCache.clearRegion` / `clearAll` now use `SCAN`
  (delete-by-cursor) instead of `KEYS`.
- Default Dialect under the starter is selected from the JDBC URL when
  `jorm.dialect` is not explicitly set.

### Fixed

- Column-name case strategy is now consistent across
  `FindBuilder`, `SaveBuilder`, `UpdateBuilder`, `DeleteBuilder`,
  `ResultSetMapper`, and `EntityHelper` — driven by `NamingStrategy`.
- `UpdateBuilder` and `DeleteBuilder` now validate column names and
  operators against the entity whitelist exactly like `FindBuilder`.
- `Main.java` "Hello world" placeholder removed from the published jar.
- `application.yml` Chinese comments no longer garbled (encoded as UTF-8).
- Test logback file name normalized from `tlias-*.log` to `jorm-test-*.log`.
- `junit` / `junit-jupiter` no longer use the deprecated `RELEASE` version.

### Deprecated

- PascalCase Session methods (`Where`, `Find`, `Save`, `Update`, `Delete`,
  `Select`, `Having`, `Group`, `Order`, `Limit`, `Model`). Removed in 3.0.
- `@Aggregation` renamed to `@Transient` (the standard JPA term);
  old annotation retained as a deprecated alias.
- Static `new FindSession()` constructor pattern; prefer `JormTemplate`
  or `Jorm.findSession()`.

### Removed

- `Main.java` placeholder class.
- Unused `firstLevelCache` field on `BaseSession` (replaced by
  `EntityModelRegistry`-backed caching in `JormTemplate` later in 2.x).

### Breaking Changes

- **Maven coordinates changed**: the namespace moved from
  `io.github.foreverstr:jorm:1.0.8` to `io.github.jadendu:jorm-core:2.0.0`
  (groupId *and* artifactId changed) because the GitHub account moved to
  `JadenDu/jorm` and the Sonatype Central Portal namespace is verified
  under `io.github.jadendu`. Users upgrading from 1.x must update both
  `<groupId>` and `<artifactId>` in their POMs; there is no automatic
  drop-in upgrade since 1.x is no longer published under the new
  namespace.
- Java package moved from `io.github.foreverstr.*` to
  `io.github.jadendu.*` to align with the new Maven coordinates. Any
  `import io.github.foreverstr.*` statements in user code must be
  renamed to `io.github.jadendu.*`. The starter's
  `@EnableConfigurationProperties` / auto-configuration class is
  re-registered in `META-INF/spring/...AutoConfiguration.imports` and
  picked up automatically; no user action is required beyond updating
  the starter dependency.
- `SaveSession.save` now throws `DuplicateKeyException` (subclass of
  `JormException`) on primary-key conflicts across **all** dialects.
- `FindSession.findOne` throws `NonUniqueResultException` and
  `EmptyResultException` instead of returning `null`/silently taking
  the first row.
- `DeleteBuilder.batchDelete` parameter type changed from `List<Object>`
  to `List<?>` to reflect intent.
- `jorm.jdbc.batch-size` default lowered to 100 (was unbounded).
- Logger names consolidated under `io.github.jadendu.*`.

## [1.0.8] - 2024-

Initial Maven Central release. See the original `README.md` for the
legacy feature set.

[2.0.0]: https://github.com/JadenDu/jorm/releases/tag/v2.0.0