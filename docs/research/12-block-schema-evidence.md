# 面板块协议（BLOCK-SCHEMA）的证据底账

> 配套交付：`docs/BLOCK-SCHEMA.md`（设计正文）。本文只做一件事：把该设计里**每一条事实主张**落到 `路径:行号`，并显式标出哪些是宿主既有事实、哪些是本协议自定的设计值、哪些**没查到**。
> **证据根**（只读，未安装/未构建/未起服务）：
> - `$P/` = `/usr/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/`（发行包，仅 `lib/*.js` 与 `lib/types/*.d.ts`，无 TS 源码）
> - `F/` = `/tmp/dsh-android-prior`（fork 基线 `bqv/dsh-android@v0.6.6`，`git log -1` = `bd0848a Cut 0.6.6`；267 个 .kt）
> - `R/` = 本仓 `docs/research/`；`PL/` = 本仓 `docs/`
> **重要前提**：本仓 `/root/dsh-android` 当前**只有 docs 与 .git**（`git log` 11 条提交全为 docs），**没有 Android 源码树**；因此所有 Kotlin 行号一律指向 `F/` 基线副本，而不是本仓。移植时行号会漂移，需按符号名重定位。

## 1. 设计主张 → 证据 → 核实状态

| # | 设计主张 | 证据 | 状态 |
|---|---|---|---|
| 1 | 一元调用是 `POST /api/<ns>/<method>`，信封含 `type/rpcId/method/payload.args` | `R/01-protocol.md:94`；`F/app/src/main/java/uk/xa0/dsh/net/DshClient.kt:965-1021` | ✅ 两处一致 |
| 2 | args 键集必须**精确匹配**描述符，多键/缺键即 `gateway/arguments-invalid` | `$P/dsh-api-gateway/lib/types/index.js:1248-1265`（`assertExactArguments`），调用点 `:570` | ✅ 源码确认 |
| 3 | 端点段文法 `[A-Za-z0-9_$.-]+`，禁空段/`.`/`..` | `R/01-protocol.md:96`（引 `$P/dsh-client-connection/lib/index.js:690,926-931`） | ✅ 二手（未直读原 js） |
| 4 | 业务错误码原样过线，其余折叠 `gateway/internal` | `$P/dsh-api-gateway/lib/types/index.js:1166-1179`；`R/01-protocol.md:107` | ✅ 源码确认 |
| 5 | 网关**没有**描述/自省端点（不能运行时问「有哪些字段」） | `$P/dsh-api-gateway/lib/types/index.js:569`（只按调用 `resolveDescriptor`）；`R/02-api-surface.md:120-131` 的端点清点无 describe 面 | ⚠️ 负向证据（grep 归纳，非规范禁止） |
| 6 | 流式端点确实存在（`log` 块可指向它） | `$P/dsh-api-job-controller/lib/typert.remote-client.d.ts:27,29`（`job/follow`、`job/list` 均 `'stream'`）；`$P/dsh-api-workspace-files/lib/typert.remote-client.d.ts:33`；`$P/dsh-api-terminal-controller/lib/typert.remote-client.d.ts:46`（`attach` 还带上行） | ✅ 源码确认 |
| 7 | 流的客户端取消 = 释放注册，`cancel` 帧由既有 mux 发送 | `F/.../net/RemoteMux.kt:317-324`（`awaitClose` 里发 `cancel`）；开流 `:286-325` | ✅ 源码确认 |
| 8 | `$events` 的 emit **不重放**，只能「收到即重读」 | `R/01-protocol.md:188`；`$P/dsh-api-remotes/lib/index.js:139-141`（`schedule/changed` 是 emit） | ✅ 二手 + 事件表确认 |
| 9 | `interactions=none` 的连接拿不到 `$events`（`gateway/interactions-forbidden`） | `R/01-protocol.md:49`（引 `$P/dsh-api-gateway/lib/types/index.js:908-911`） | ✅ 二手 |
| 10 | 逻辑流不跨连接代重放，断线后须重开 | `R/01-protocol.md:155,190`；`F/.../net/RemoteMux.kt:227-236`（换代重发 open 的注释与实现） | ✅ 两处一致 |
| 11 | `schedule/catalog` 无参、返回带 `sessionId/status/lastDelivery` 的记录 | `$P/dsh-schedule/lib/types/types.d.ts:184-191`；方法表 `$P/dsh-schedule/lib/typert.remote-client.d.ts:11-16` | ✅ 源码确认 |
| 12 | `schedule/delete`、`schedule/history` 存在且形状已知 | `$P/dsh-schedule/lib/types/types.d.ts:285-292`、`:390-393`；`typert.remote-client.d.ts:11-16` | ✅ 源码确认 |
| 13 | **不存在** `schedule/setEnabled`、`schedule/create` | 方法表只有 `catalog/delete/deleteInactive/history/list/update`：`$P/dsh-schedule/lib/typert.remote-client.d.ts:11-16`；fork 注释同结论 `F/.../ui/AutomationSheet.kt:79-86` | ✅ 双向确认（源码 + fork 独立结论） |
| 14 | `schedule/update` 是「带 `expected` 整记录比较更新」，不能表达启用/停用 | `$P/dsh-schedule/lib/types/types.d.ts:368-374`（`expected` 必填）、`:376-380`（`schedule_ended`/`schedule_conflict`） | ✅ 源码确认 |
| 15 | 审批/提问是 `waterfall`（必须应答），回执走 `$events/result` | `$P/dsh-api-remotes/lib/index.js:27`（`approval/request`）、`:144`（`user-questions/request`）；`R/01-protocol.md:175-182` | ✅ 源码 + 二手 |
| 16 | 审批 UI 在 fork 里**只有一个**实现，座位固定在输入框正上方 | `F/.../ui/components/ChatRows.kt:1412`（`ApprovalCard`）；`F/.../ui/ChatScreen.kt:1418-1430`（唯一调用点 + 座位注释） | ✅ 源码确认 |
| 17 | fork **全无 WebView**（逃生舱是真实架构变更） | `F/.../ui/AboutScreen.kt:250`（"there is no WebView"）；`ui/markdown/MarkdownView.kt:66-68`；`ui/TerminalScreen.kt:85` | ✅ 三处一致 |
| 18 | fork 已有统一外链先例（`ACTION_VIEW`） | `F/.../ui/AboutScreen.kt:586` | ✅ 源码确认 |
| 19 | fork 有现成的 sheet 显隐状态管理（`rememberSaveable`） | `F/.../ui/ChatScreen.kt:466-479` | ✅ 源码确认 |
| 20 | fork 的 RPC 客户端能拿裸数组（`rpc` 会把数组读成空对象，故用 `rpcRaw`） | `F/.../net/DshClient.kt:954-963`（注释）、`:1019`（`value` 原样返回） | ✅ 源码确认 |
| 21 | 401/403 由客户端统一转成「会话过期」 | `F/.../net/DshClient.kt:983-985` | ✅ 源码确认 |
| 22 | 语义色令牌可选（面板壳复用它） | `F/.../ui/theme/DshTheme.kt:61-76`（`accent/success/error/warn`） | ✅ 源码确认 |
| 23 | 可复用的行件/分区件已存在 | `F/.../ui/SettingsContent.kt:766`(`SectionHeading`)、`:818`(`SettingsRow`)、`:841`(`SettingsGroup`)、`:926`(`SettingsSelector`) | ✅ 源码确认 |
| 24 | 可复用的状态胶囊/列表行/文件行 | `F/.../ui/AutomationSheet.kt:524`(`StatusChip`)、`:434`(`TaskRow`)、`:152`(`AutomationContent`)；`ui/components/StateDot.kt:46`；`components/FilesPanel.kt:167,782` | ✅ 源码确认 |
| 25 | `Switch` 在本仓已有用法 | `F/.../ui/SessionsDrawer.kt:1003` | ✅ 源码确认 |
| 26 | 测试规模与资源目录形态 | `F/app/src/test` 127 个 .kt / `grep -c '@Test'` = 1116；`app/src/test/resources/` 现仅 `multipart-real.bin` | ✅ 实测 |
| 27 | 计划里已排期 Roborazzi 截图测试 | `PL/PLAN.md:168` | ✅ 文档确认 |

## 2. 宿主里可借鉴的「声明式 UI」先例

| 先例 | 机制 | 证据 | 对本协议的用处 |
|---|---|---|---|
| 操作卡片（action cards） | 宿主只规定信封（`callId/turn/action/title/brief/details`），**每个 kind 自己校验自己的字段** | `$P/dsh-action-cards/lib/types/types.d.ts:7`(`ActionCardFields`)、`:9-21`(proposal)、`:27-33`(decision)、`:45-56`(result 四态) | 证明「固定信封 + 未知 kind 可降级」在 DSH 里已有先例；本协议的 `blocks[]`/`Unknown` 是其泛化 |
| typert Remote 面 | 一个类 + 装饰器即生成 `ns/method` 与 `unary/stream` 标记；客户端 d.ts 由生成器产出 | `$P/dsh-typert-protocol/lib/types/types.d.ts:455-458`(`mode`)、`:505-517`(`TypertRemoteMethodSpec`/`TypertRemoteSpec`)；`$P/dsh-schedule/lib/typert.remote-client.d.ts:1`（"Generated by dsh-typert-generator"） | `panels/list`/`panels/describe` 照此实现成本极低 |
| 插件清单 | 只读快照：`entries[{entryId,moduleName,enabled,fiberPhase,diagnostic}]` | `$P/dsh-host-plugin-inventory/lib/types/types.d.ts:8-18`、`:55-64`；端点 `pluginInventory/list` 于 `dsh-host-plugin-inventory/lib/typert.remote-client.d.ts:22` | 面板目录的 `requires` 门控可直接复用该快照，不必新增协议 |
| 定时任务 UI | 面板 = 目录读 + 详情读 + 硬删除 + 事件重读 | `$P/dsh-client-ui-schedule` 整包；README 描述见包内 `README.zh.md`（无行号，不作行级证据） | `§8` 的 schedule 示例面板就是按它裁剪出来的 |
| 既有「能力探测 + 动态面板」判断 | 明确建议先做数据驱动面板，别做插件运行时 | `PL/PLUGIN-ARCHITECTURE.md:99-112`（JSON 雏形）、`:114-119`（对比表）、`:135`（P3 WebView 排序） | 本协议正是在该建议上落地 |

## 3. 负向结论（**没查到 / 不存在**，实现前必须知道）

| 结论 | 依据 | 影响 |
|---|---|---|
| 今天**没有任何** `panels/*` 端点 | 39 namespace 清点见 `R/01-protocol.md:21`；逐包检查带 remote 的 35 个包的 `*/lib/typert.remote-client.d.ts`，无 panel 面 | `panels/list`+`panels/describe` 必须新写（约 200 行，照 schedule 模板） |
| 没有「客户端插件注册」通道（App 无法告诉宿主"我支持 schema v1"） | `PL/PLUGIN-ARCHITECTURE.md:144`、`:60` | 版本协商只能塞进 `panels/list` 的 args（文档 §7 的做法） |
| 没有可用于「面板属于哪个插件」的现成字段 | `$P/dsh-host-plugin-inventory/lib/types/types.d.ts:8-18` 只有 `entryId/moduleName/meta/enabled/fiberPhase/diagnostic` | 面板目录需自带 `owner.plugin`，并与该快照的 `moduleName` 对齐 |
| `schedule` 域无法表达「启用/停用」 | 同 §1 第 13、14 条 | 示例面板的 toggle 要么由新插件补端点，要么按 §7 永久禁用；**不能**用 `update` 硬凑 |
| 宿主 ready 帧不带版本（无法据此做协议版本判断） | `F/.../model/HostVersion.kt:22-23,59-67`（`host` 只有 `{home}`，版本读作 `NotReported`） | 版本协商**不能**依赖 host 版本号，只能靠 `schemaVersion` 自述 |
| 本仓尚无 Android 源码 | `/root/dsh-android` 仅 `docs/`+`.git`，`git log` 全为 docs 提交 | 落地前先完成 `PL/PLAN.md:159-163` 的 M0 仓库接管 |

## 4. 被本协议**自定**的量（不是宿主事实，勿引用为证据）

| 设计值 | 出处 | 依据强弱 |
|---|---|---|
| 整包 ≤ 256 KB、≤ 200 块、嵌套 ≤ 3、≤ 40 个 op、≤ 16 条 refresher | 设计文档 §2 | 纯经验值；仅「下行帧 ≥1024 字符才压缩」(`R/01-protocol.md:20`) 与「上行 inbox 262144 B/流」(`R/01-protocol.md:19`) 是真实约束 |
| 渲染 ≤ 200 行 / 图表点 ≤ 500 / 日志 ≤ 2000 行 | 同上 | 手机端体验估计，无宿主依据 |
| `poll` 钳制 [5s, 300s] | 同上 | 与心跳 2s(`R/01-protocol.md:18`) 同量级偏保守 |
| 固定黑名单（`$events/result`、`credentials/*`、`settings/mutate`…） | 设计文档 §6 | 名单本身是设计选择；「审批走 `$events/result`」是事实（§1 第 15 条） |
| 14 种块类型与各块字段名 | 设计文档 §3 | 自定；映射到的 Compose 组件是真实存在的（§1 第 23-25 条） |

## 5. 给实现者的三条提醒

| 提醒 | 说明 | 证据 |
|---|---|---|
| 行号会漂移 | 本文 Kotlin 行号来自 `/tmp/dsh-android-prior`（v0.6.6），M0 改包名后全部偏移；请按**符号名**检索 | `F` 与 `PL/PLAN.md:161`（改包名任务） |
| 面板数据别进日志 | fork 对 `schedule/catalog` 失败有「每 episode 一行」的控噪策略，面板可照抄 | `F/.../DshViewModel.kt:2535` |
| 别复用审批组件 | 渲染器一旦 import `ApprovalCard`/`AskQuestionRow`/`QuestionCard`，冒充风险立刻成立 | `F/.../ui/components/ChatRows.kt:1412`、`components/AskQuestionRow.kt`、`components/QuestionCard.kt` |
