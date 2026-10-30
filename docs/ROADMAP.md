# 工作流总表（fork → KernelSU 风格上线）

> 目的：把「fork + 换皮」拆成可并行、可验收的任务板。每一行都能独立交付，行内顺序即依赖顺序。
> 参考：[PLAN.md](PLAN.md) §5 里程碑 · [DESIGN-TOKENS.md](DESIGN-TOKENS.md) · [FORK-SURGERY.md](FORK-SURGERY.md) · [BUILD-AND-CI.md](BUILD-AND-CI.md)

## 阶段 0 · 接管（3–5 天）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 0.1 | 建私有仓库，`origin`=自己、`upstream`=bqv | 仓库 | `git remote -v` 正确 | — |
| 0.2 | 改包名/应用名/图标/About | 提交 | 全仓无旧包名；APK 安装显示新名 | 0.1 |
| 0.3 | `NOTICE.md` + 修改文件头标注 | 文档 | MPL 义务齐备 | 0.2 |
| 0.4 | CI：`build.yml` 出 APK + 新增 `verify.yml` 跑 1 116 个单测 | 工作流 | 两个 workflow 绿 | 0.2 |
| 0.5 | 首次真机冒烟（连本实例，聊天+审批+文件） | 记录 | 全链路通过 | 0.4 |

## 阶段 0.5 · 全量升级到最新（5–7 天，**必须先做**）

> 决定：**全部升到最新**。目标组合见 [research/09-toolchain-latest.md](research/09-toolchain-latest.md)：
> **Gradle 9.5.0 · AGP 9.3.1 · Kotlin 2.4.21 · Compose BOM 2026.09.00 · compileSdk/buildTools 37 · JDK 17 · miuix 0.9.4**
> 为什么不是「最新」的 AGP 9.5.0 / Gradle 9.8.2：**超出 Kotlin 2.4.21 官方矩阵上限**（AGP ≤9.3.1、Gradle ≤9.7.0）。论证见 [research/10-version-lock.md](research/10-version-lock.md)。

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| **L1** ✅ 完成 2026-10-30 | **只升 Kotlin**：1.9.24 → **2.4.21** + `org.jetbrains.kotlin.plugin.compose`；删 `composeOptions`；`kotlinOptions`→`compilerOptions`；Robolectric 4.13→4.17。**AGP / compileSdk 全不动** | 已合入 `master` | **CI 实测 `tests=1116 failures=0 errors=0 skipped=0`** + 19.8 MB APK | — |
| **L2** ✅ 完成 2026-10-30 | AGP 8.5.2 → **8.13.2**；Gradle 8.7 → **8.13**；compileSdk/buildTools/targetSdk **36**。**Kotlin 与 Compose BOM 不动**（单一变量） | 已合入 `master`（PR #1） | **CI `tests=1116 failures=0`** + 20 MB APK | L1 |
| **L3** ✅ 完成 2026-10-30 | AGP 9.3.1 + Gradle 9.5.0 + compileSdk/buildTools/targetSdk **37** + Compose BOM **2026.09.00**。AGP 9 内置 Kotlin：**移除 `org.jetbrains.kotlin.android`** | 已合入 `master`（PR #2） | **CI `tests=1116 failures=0`** + APK | L2 |
| **L4** ✅ 完成 2026-10-30 | 引入 miuix **0.9.4** 最小集（ui / preference / icons / squircle），**不含 blur**（模块 minSdk 33 vs 我们 26） | 已合入 `master`（PR #3） | **CI 全绿** + APK 构建成功 | L3 |

> **L1 实测记录（2026-10-30）**：仓库 <https://github.com/gitMFQ/dsh-android>；Verify 工作流 `tests=1116 failures=0 errors=0 skipped=0`；Build 工作流产出可安装 APK。
> 本机（aarch64 Debian）只能验证到 Gradle 配置阶段（`gradle help` 成功）；资源编译与测试必须在 CI —— 原因见 [BUILD-AND-CI.md](BUILD-AND-CI.md) §0。

> 两个 **仍然不要**：**不引 `miuix-blur`**（模块 minSdk 33，我们 26 —— 除非把 minSdk 抬到 33，那会丢掉 Android 8–12 用户）；**不引 `miuix-nav`**（虽然 0.9.4 有，但它要求把所有路由改成 `@Serializable NavKey`）。
> **JDK**：AGP 9 需要 JDK 17+，miuix 自身用 21。本机目前只有 17，若 CI/本地报错需 `apt install openjdk-21-jdk-headless`。

## 阶段 1 · 结构（1 周）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 1.1 | 抽 `:core:protocol`（`net/`） | 模块 | 独立编译 + net 单测绿 | 0.4 |
| 1.2 | 抽 `:core:model`（`model/`） | 模块 | 47 个模型单测绿 | 0.4 |
| 1.3 | 新建 `:design`：`KsuTokens` + `KsuTheme`（7 档外观） | 模块 | 设置页可切并持久化 | 1.1 |
| 1.4 | Roborazzi 截图测试接入（外壳 + 设置页先覆盖） | 测试 | CI 跑出基线图 | 1.3 |

## 阶段 2 · 外壳换装（1.5 周）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 2.1 | 浮动底栏：`MiuixIcons` + 自绘玻璃层（`Modifier.blur` API31+/描边高光；API<31 纯色）**或** 直接用 `FloatingNavigationBar` | 组件 | 三档设备均正常 | 0.5.3 |
| 2.2 | 4 目的地 pager（Chat/Trajectory/Files/Settings），宽屏切 `NavigationRail` | 导航 | 切页弹簧流畅，旋转后状态保持 | 2.1 |
| 2.3 | TopBar 折叠 + 状态胶囊（连接/运行/待审批） | 组件 | 滚动折叠无跳变 | 2.1 |
| 2.4 | 抽屉换装（搜索栏/分段列表/分组排序/归档） | 界面 | 分组排序可用，截图无回归 | 2.2 |
| 2.5 | 设置页 + 关于页换装 | 界面 | 视觉对齐参考；项全部可达 | 1.3 |
| 2.6 | Sheet/Dialog 统一：`OverlayBottomSheet` / `OverlayDialog`（**必须挂在 Miuix `Scaffold.popupHost` 下**）；危险操作红卡 + 二次确认 | 组件 | 审批走 Sheet + 二次确认 | 2.5 |

## 阶段 3 · 聊天精修（1 周）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 3.1 | 消息气泡/状态点/工具卡折叠行/统计胶囊换皮 | 界面 | 语义色不变；`DshTheme.colors` 调用点不变 | 2.6 |
| 3.2 | 动效：切页弹簧、弹窗 scale、流式段落淡入、危险操作触觉 | 打磨 | 60fps；无卡顿 | 3.1 |
| 3.3 | 深色/AMOLED/大字体/无障碍回归 | 测试 | 无 P0 视觉缺陷 | 3.2 |

## 阶段 3.5 · 多语言（1.5–2 周，与阶段 2–3 交错进行）

> 现状：`strings.xml` 只有 6 条、`stringResource()` 调用 **0 次**、约 1 000–1 200 条硬编码界面文案。规划见 [I18N.md](I18N.md)。
> **原则：换哪个文件就抽哪个文件**，不要先全换皮再统一抽（那是同一批文件改两遍）。

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 3.5.1 | 建 `strings.xml` 骨架 + 语言切换 API（appcompat backport）+ 设置页「语言」项 | 骨架 | API 26 真机切换生效 | 0.5.5 |
| 3.5.2 | 抽 `SettingsContent.kt` / `AboutScreen.kt`（低风险，随 D3/D4 一起做） | 资源 | 两页 0 硬编码 | 3.5.1 |
| 3.5.3 | 抽 `SessionsDrawer.kt` / `ChatScreen.kt` / `Sheets.kt`（随 D2/D3/D5 一起做） | 资源 | 聊天链路 0 硬编码 | 3.5.2 |
| 3.5.4 | 抽 `ui/components/**`（工具卡片） | 资源 | 全量 | 3.5.3 |
| 3.5.5 | 出 zh 翻译 + 抽检；CI 加「无硬编码 UI 文案」门禁 | 翻译 | 中文界面无残留英文 | 3.5.4 |

> 截图测试要**先固定 locale**（`@Config(qualifiers = "en")`）再抽，否则基线天天漂移。

## 阶段 4 · 差异化与发布（1–2 周）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 4.1 | 本地通知（任务完成/待审批，点击直达会话/审批） | 功能 | 后台 30 分钟内可送达 | 3.3 |
| 4.2 | 多实例切换（数据模型预留 `instanceId`） | 功能 | 两台实例可切换不串数据 | 3.3 |
| 4.3 | 平板/折叠屏自适应（双栏或 Rail 阈值） | 功能 | 展开/折叠无布局错乱 | 3.3 |
| 4.4 | 弱网与断线重连压测 + 导出诊断包 | 质量 | 断网 5 分钟后恢复不丢消息 | 3.3 |
| 4.5 | 签名 keystore 固化 + Release + Obtainium + 变更日志 | 发布 | 可侧载安装并订阅更新 | 4.4 |

## 关键路径与并行建议

```
0.1 → 0.2 → 0.4 → 1.1/1.2（并行） → 1.3 → 2.1 → 2.2 → 2.4 → 2.6 → 3.1 → 3.2 → 4.1 → 4.5
```

- 阶段 2 内部：2.3 / 2.5 可与 2.2 并行（不同文件）。
- 阶段 4 的 4.2 / 4.3 可与 4.1 并行。
- **单人全职约 7–9 周**（含阶段 0.5 全量升级与 3.5 多语言）；两人（一人外壳/一人聊天精修+i18n）可压缩到 5–6 周。

## 每周节奏建议

| 周 | 主题 | 周末应能演示 |
|---|---|---|
| W1 | 接管 + 结构 | 新名字的 APK 能跑，设置页能切外观 |
| W2–W3 | 外壳换装 | 底栏 + 四页 + 抽屉 + 设置全部新皮 |
| W4 | 聊天精修 + 动效 | 完整聊天链路视觉达标 |
| W5 | 通知/多实例/平板 + 发布 | 可对外发内测包 |

## 风险闸门（任一触发就停下来复盘）

| 闸门 | 触发条件 | 处理 |
|---|---|---|
| **Kotlin 2.x 迁移卡住** | 0.5.1 测试红且 2 天内无法收敛 | 先只升 Kotlin 不加 miuix，逐测试修复；若仍不行 → 走"方案 B"：不引 miuix，纯自绘外观（工期 +1 周，但零工具链风险） |
| 换皮回归失控 | 截图测试差异文件 > 20 个/次 | 缩小改动粒度，先补测试 |
| ChatScreen 改不动 | 单文件改动 > 300 行仍无法通过编译/测试 | 拆文件（把 Scaffold 段抽成 `ChatScaffold.kt`） |
| 全量升级连环失败 | AGP 9 / Kotlin 2.4 / Compose 1.12 任一步炸且 3 天内收敛不了 | **没有可比这更低、又能装 miuix 的路**（所有 miuix 版本都要 compileSdk 37）。只能二选一：① 继续修（优先）② **放弃 miuix、外观全自绘**，工具链停在 L1（AGP 8.13 / compileSdk 36） |
| Sauce 依赖断供 | miuix 停更或 Maven 撤包 | 源码已 Apache-2.0，可 vendored（`/tmp/miuix`）自建 |
| 上游停更/大改 | upstream 3 个月无提交或有破坏性改动 | 冻结 fork，自持；不再 cherry-pick |
