# Security Policy

## Supported Versions

JORM follows semantic versioning. Security fixes are applied to the
latest minor release of the current major line. Older major lines are
supported on a best-effort basis.

| Version | Supported |
|---------|-----------|
| 2.x     | Yes       |
| 1.x     | Best-effort (security fixes only) |
| < 1.0   | No        |

## Reporting a Vulnerability

**Please do NOT open public GitHub issues for security problems.**

Email security findings to **`wy1903265502@163.com`** with the subject
`[JORM SECURITY] <short summary>`. If possible, encrypt your message
using the maintainer's public GPG key — fingerprint available on the
Maven Central artefacts.

Please include:

1. Affected version(s) and the affected module (`jorm` or
   `jorm-spring-boot-starter`).
2. A minimal reproduction (stack trace, SQL, or a repo link).
3. Your assessment of impact and any suggested mitigation.
4. Whether the issue has been disclosed elsewhere.

### Response timeline

- **Acknowledgement**: within 72 hours.
- **Initial assessment**: within 7 days.
- **Fix ETA**: communicated based on severity. Critical issues are
  prioritized; a security release is usually cut within 14 days.

Please give us a reasonable window to issue a fix before any public
disclosure. We will credit reporters in the release notes and
`CHANGELOG.md` unless they prefer to remain anonymous.

## Scope

In scope:

- SQL injection via the chainable query API.
- Cache poisoning / deserialization issues in the Redis L2 layer.
- Connection / transaction leaks that lead to DoS.
- Privilege escalation via the Spring Boot starter auto-configuration.

Out of scope:

- Vulnerabilities in dependencies (please report upstream and pin via
  Dependabot PRs).
- Social engineering of maintainers.

## Hardening recommendations for users

- Run with the smallest possible HikariCP pool (`maximum-pool-size`)
  sized to your workload.
- If you enable the Redis L2 cache, isolate the Redis instance and
  enable TLS where possible.
- Restrict bound parameters to JDBC's `setObject` types via a custom
  `TypeHandler` if you ingest untrusted column data.
- Do not expose `JormTemplate` over a public API boundary without
  input validation on `Where(...)` column names — the framework
  whitelist-validates columns but cannot reason about your domain.