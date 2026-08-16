<!--
  感谢你对 JORM 的贡献！
  请完成下方清单；这能加快评审速度，也让发布说明更可信。
  <!-- … --> 中的内容不会展示给用户。
-->

## 概要

<!-- 用一两句话说明本 PR 改了什么、为什么改。 -->

## 动机

<!-- 解决什么问题？用 "Closes #123" 关联 Issue。
     如果是新功能，请链接设计讨论 / RFC Issue。 -->

Closes #

## 变更内容

<!-- 列出用户可感知的变更以及内部重构。
     标出需要评审者重点查看的部分。 -->

-

## 破坏性变更

- [ ] 无
- [ ] 有 —— 已在 `CHANGELOG.md` 的 `Unreleased` 下列出，并附迁移说明。

## 检查清单

- [ ] 本地通过 `mvn spotless:apply` 和 `mvn checkstyle:check`。
- [ ] 通过 `mvn -pl jorm-core -am clean verify`（starter 改动则跑 starter 对应命令）。
- [ ] 新公开 API 已标注 `@API(...)`（见 CONTRIBUTING.md）。
- [ ] 为 bug 修复 / 新功能补充了新测试。
- [ ] 已更新 `CHANGELOG.md` 的 `Unreleased` 条目。
- [ ] 提交信息遵循 Conventional Commits。
- [ ] 未提交任何密钥、凭据或 GPG 私钥。

## 给评审者的提示

<!-- 需要评审者特别注意的地方：
     性能热点、复杂的并发逻辑、数据库方言细节等。 -->

## 发布说明

<!-- 供 GitHub Release 页面使用的一行摘要，例如：
     "feat(dialect): add OracleDialect with ROWNUM limit support" -->
