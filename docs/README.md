# DSH 安卓客户端 · 规划文档索引

> 从零到「可开工」的全部规划都在这里。**执行时以标 ★ 的为准**，其余是论证与背景。

## 现在该做什么

| 顺序 | 文档 | 内容 |
|---|---|---|
| 1 | ★ [ROADMAP.md](ROADMAP.md) | 任务板：阶段 0.5（工具链）→ 1 → 2 → 3 → 3.5（i18n）→ 4 |
| 2 | ★ [research/10-version-lock.md](research/10-version-lock.md) | 版本锁：AGP 9.3.1 / Gradle 9.5.0 / Kotlin 2.4.21 / SDK 37 / miuix 0.9.4 |
| 3 | [PLAN.md](PLAN.md) | 总纲：fork 决策、协议事实、里程碑、风险 |

## 设计与实现

| 文档 | 内容 |
|---|---|
| [DESIGN.md](DESIGN.md) | 设计系统落地：信息架构、组件取舍、与 fork 的冲突点 |
| ★ [DESIGN-TOKENS.md](DESIGN-TOKENS.md) | 设计规范：实测刻度、`KsuTokens`、底栏行为、Miuix 分工 |
| [FORK-SURGERY.md](FORK-SURGERY.md) | fork 改造：改包名清单、模块拆分、MPL 义务 |
| [BUILD-AND-CI.md](BUILD-AND-CI.md) | 构建与 CI（本机 aarch64 不能打包） |
| [I18N.md](I18N.md) | 多语言：债务量化、20 条 DSH 术语表、抽取顺序 |

## 插件化（1.0 之后）

| 文档 | 内容 |
|---|---|
| [PLUGIN-ARCHITECTURE.md](PLUGIN-ARCHITECTURE.md) | 可行性研究：三道安卓坎、成本对比、独立性与安全 |
| [PLUGIN-PLAN.md](PLUGIN-PLAN.md) | 路线 v2（后端可改）：三段式架构、分期 P1–P3 |
| ★ [BLOCK-SCHEMA.md](BLOCK-SCHEMA.md) | 面板块协议 v1：14 种块、绑定、安全模型、Compose 骨架 |
| [research/11-dsh-plugin-authoring.md](research/11-dsh-plugin-authoring.md) | 写 DSH 插件的完整契约（manifest / Cordis / Remote / 构建） |

## 研究底账（论证与证据）

| 文档 | 内容 |
|---|---|
| [research/01-protocol.md](research/01-protocol.md) | 线协议逆向：认证、一元、WS、`$events` |
| [research/02-api-surface.md](research/02-api-surface.md) | 197 端点清单、P0 请求体、时序图 |
| [research/03-ui-feature-map.md](research/03-ui-feature-map.md) | Web GUI 功能盘点与安卓映射 |
| [research/04-android-stack.md](research/04-android-stack.md) | 技术栈对比、依赖版本 |
| [research/05-ksu-kit.md](research/05-ksu-kit.md) | KernelSU 风格盘点（GPL，只借观感） |
| [research/06-bqv-ui.md](research/06-bqv-ui.md) | fork 基线 UI 层盘点 |
| [research/07-miuix-api.md](research/07-miuix-api.md) | Miuix 组件面与覆盖矩阵 |
| [research/08-miuix-compat.md](research/08-miuix-compat.md) | 接入代价与工具链可行性（含事实纠正） |
| [research/09-toolchain-latest.md](research/09-toolchain-latest.md) | 最新版本一览与 AGP 9 DSL 改动 |
| [research/12-block-schema-evidence.md](research/12-block-schema-evidence.md) | 面板协议 27 条主张的证据底账 |
| [SPIKE.md](SPIKE.md) | 1–2 天开工验证清单 |
