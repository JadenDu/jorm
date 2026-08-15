# Contributing to JORM

First of all — thank you for taking the time to contribute!
JORM is a community-driven project and every issue, PR, or word of
feedback counts.

This document explains how to set up the project, the conventions we
follow, and what to expect during review.

## 1. Code of Conduct

Participation in this project is governed by the
[Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to
abide by its terms. Please report unacceptable behaviour to
`wy1903265502@163.com`.

## 2. Repository layout

```
jorm-core/                  # Core ORM (plain JDBC + SLF4J, zero framework deps)
jorm-spring-boot-starter/   # Spring Boot auto-configuration, HikariCP, Redis L2
.github/                    # CI workflows, issue templates, dependabot config
build-tools/                # Shared Checkstyle / SpotBugs configuration
```

Both Maven modules are independently versioned and released to Maven
Central, but share CI standards and code-style rules.

## 3. Build & test

Requirements: **JDK 11+**, **Maven 3.8+**.

```bash
# Core module
mvn -f jorm-core clean install -DskipTests -Dgpg.skip=true

# Spring Boot starter (depends on the core jar above)
mvn -f jorm-spring-boot-starter clean install -DskipTests -Dgpg.skip=true

# Everything, with tests, style, and coverage gates
mvn -f jorm-core clean verify
mvn -f jorm-spring-boot-starter clean verify
```

The starter tests start an embedded Redis and (optionally) a MySQL
Testcontainer, so they require Docker on the host or `DOCKER_HOST` set.
The core H2 tests need nothing external.

## 4. Before you push

The CI gates below run on every push and PR. Run them locally first:

| Check | Command |
|-------|---------|
| Code style (Spotless) | `mvn spotless:check` |
| Code style auto-fix | `mvn spotless:apply` |
| Static analysis (Checkstyle) | `mvn checkstyle:check` |
| Bugs (SpotBugs) | `mvn spotbugs:check` |
| Coverage gate (JaCoCo ≥ 70%) | `mvn jacoco:check` |
| Tests | `mvn test` |

Spotless uses the Google Java Format conventions (4-space indent, two
characters of trailing column drift permitted for chained methods).

## 5. Coding conventions

- **Java 11 language level** in both modules. Do not introduce `var`,
  records, switch expressions, or text blocks that target 17/21.
- All public API must be annotated with `@org.apiguardian.api.API(status=...)`.
  - `@API(status = STABLE)` — safe public contract.
  - `@API(status = EXPERIMENTAL)` — unstable, may change.
  - `@API(status = INTERNAL)` — implementation detail, not for use.
- Prefer **camelCase** for all new methods. PascalCase chain methods are
  `@Deprecated` for removal in 3.0; do not add new ones.
- All new exceptions extend `JormException` and are accompanied by an
  `ErrorCode` entry. Include the error code in every `log.error(...)`
  string using `[ErrorCode=xxxx]`.
- Field naming follows the active `NamingStrategy`; entities must work
  under the default Snake-case strategy without configuration.
- Avoid unchecked casts on `ResultSet` reads. Add a new
  `TypeHandlerRegistry.register(...)` entry for any unsupported type.
- Do not log full SQL at `INFO`. Use `DEBUG` for SQL and tuple-value
  bindings; reserve `INFO` for lifecycle events.
- Do **not** use `redisTemplate.keys(...)` anywhere. New code must use
  `SCAN`-based iteration.

## 6. Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(scope): <subject>

<body>

<footer>
```

- `type`: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`,
  `build`, `ci`, `chore`, `deps`.
- `scope`: `core`, `starter`, `dialect`, `cache`, `tx`, `docs`,
  `build`, etc.
- `subject`: imperative, ≤ 72 chars, no period.
- **Breaking changes** must be flagged with `BREAKING CHANGE:` in the
  footer or `!` after the scope (`feat(tx)!: ...`).

Examples:

```
feat(dialect): add OracleDialect with ROWNUM limit clause
fix(cache): clear region after Spring transaction commit
docs(readme): document batch-size configuration property
```

## 7. Pull requests

- Open the PR against `main`.
- Reference the issue: `Closes #123` in the PR description.
- Keep PRs focused — one logical change per PR. Mix refactor & feature
  changes only when unavoidable.
- Update `CHANGELOG.md` under an `Unreleased` entry.
- New public API ⇒ new Javadoc, new test under `jorm/src/test` (H2) or
  the starter test source set.
- Wait for the `ci / build` and `ci / verify` checks to pass. Two
  maintainers must approve before merge (one for trivial fixes).

## 8. Releases

Releases are cut by maintainers only.

1. Bump version in `pom.xml` of the affected module.
2. Move `CHANGELOG.md` `Unreleased` block to a dated `[x.y.z]` section.
3. Create a signed tag `vx.y.z`.
4. Two-stream pipeline publishes to Maven Central via
   `central-publishing-maven-plugin` (GPG-signed, sources, javadoc).

## 9. Getting help

- Issues: https://github.com/JadenDu/jorm/issues
- Email: wy1903265502@163.com
- Slow responses happen — please be patient and respectful.

— The JORM team.