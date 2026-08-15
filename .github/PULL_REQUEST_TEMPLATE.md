<!--
  Thanks for your contribution to JORM!
  Please complete the checklist below; it speeds up review and keeps the
  release notes trustworthy. Lines in <!-- … -> are not shown to users.
-->

## Summary

<!-- One or two sentences describing what this PR changes and why. -->

## Motivation

<!-- What problem does this solve? Reference issues with "Closes #123".
     If this is a new feature, link the design discussion / RFC issue. -->

Closes #

## Changes

<!-- Bullet list of the user-observable changes plus internal refactors.
     Call out anything that requires reviewers to look closely. -->

-

## Breaking changes

- [ ] None
- [ ] Yes — listed in `CHANGELOG.md` under `Unreleased` with a migration note.

## Checklist

- [ ] `mvn spotless:apply` and `mvn checkstyle:check` pass locally.
- [ ] `mvn -pl jorm clean verify` passes (or starter if the change is there).
- [ ] New public API is annotated with `@API(...)` (see CONTRIBUTING.md).
- [ ] New tests added for bug fixes / new features.
- [ ] `CHANGELOG.md` `Unreleased` entry updated.
- [ ] Commit messages follow Conventional Commits.
- [ ] No secrets, credentials, or GPG private keys committed.

## Reviewer notes

<!-- Anything reviewers should pay special attention to:
     performance hot-spots, tricky concurrency, DB-dialect subtleties, etc. -->

## Release note

<!-- One-line summary for the GitHub Release page, e.g.
     "feat(dialect): add OracleDialect with ROWNUM limit support" -->