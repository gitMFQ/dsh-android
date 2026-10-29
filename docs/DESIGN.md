# KernelSU 风格设计系统落地到 DSH 安卓客户端

> 依据：`chenaizhang/KernelSU-Style-UI-Kit`（**GPL-3.0**，Kotlin/Compose，105 个 .kt / 1.27 万行）+ 其依赖 `compose-miuix-ui/miuix`（**Apache-2.0**，1522★）+ fork 基线 `bqv/dsh-android`（MPL-2.0）。
> 结论先行：**“KernelSU 风格” = Miuix(HyperOS) 观感 × Material3 动态取色 × 浮动玻璃底栏 × 大圆角卡片式页面**。做法是**重新实现这套视觉语言**（不复制 GPL 代码），保留 fork 的信息架构与渲染逻辑。

## 1. 这套“风格”由什么构成（逐文件核实）

| 维度 | 事实 | 证据 |
|---|---|---|
| 双主题引擎 | 运行时二选一 `UiMode.Miuix`（默认）/ `Material`，经 `LocalUiMode` 分派 | `ui/UiMode.kt:8-14`、`ui/theme/Theme.kt` |
| 取色 | `ColorMode` 7 档：SYSTEM / LIGHT / DARK / MONET_SYSTEM / MONET_LIGHT / MONET_DARK / **DARK_AMOLED**；另有 `keyColor` + `PaletteStyle` + `ColorSpec` 由 **material-kolor** 生成 | `ui/theme/Theme.kt` 的 `ColorMode`/`ThemeController` |
| 色彩来源 | Monet 走系统取色（`dynamicLightColorScheme`/`dynamicDarkColorScheme`）；非 Monet 用 material-kolor 的 `rememberDynamicColorScheme` | `ui/theme/MaterialTheme.kt:30-31` |
| 色板页 | 自带 16 色 key color 选择器 + 独立取色预览屏 | `ui/theme/Colors.kt:7-21`、`ui/screen/colorpalette/` |
| 组件库 | `compose-miuix-ui 0.9.4`（包名 `top.yukonga.miuix.kmp`：`blur.Backdrop` / `LayerBackdrop` / `springAnimateToPage`） | `gradle/libs.versions.toml`、`component/bottombar/BottomBar.kt` |
| 标志性组件 | **FloatingBottomBar**（456 行，液态玻璃：`dropShadow` + `graphicsLayer` + `lens` + `innerShadow`；文件首行注明 “Adapted from compose-miuix-ui example (IosLiquidGlassNavigationBar) — **Apache 2.0**”） | `component/FloatingBottomBar.kt:1`、`component/liquid/*` |
| 底部导航 | `BottomBar`(112) / `BottomBarMiuix`(123) / `BottomBarMaterial`(63)，配 `HorizontalPager` + 侧栏 `NavigationRail*` 自适应 | `component/bottombar/*` |
| 搜索 | `SuperSearchBar`(338, Miuix) / `SearchBar`(239, Material) + `SearchStatus`(58) | `component/miuix/SuperSearchBar.kt` 等 |
| 对话框 | 统一 `Dialog.kt`(366) + `DialogMiuix`(127)/`DialogMaterial`(86) 两套皮肤；`ScaleDialog` 缩放动画 | `component/dialog/*` |
| 列表/设置 | `SegmentedList`(493)、`SettingsItem`(57)、`TonalCard`；Miuix 侧有 `DropdownItem` / `EditText`(194) / `SuperEditArrow`(127) | `component/material/*`、`component/miuix/*` |
| 反馈 | `WarningCard`(58)、`SnackBar`、`ExpressiveSwitch`(55)、`ScrollToTop`(90)、`KeyEventBlocker` | `component/*` |
| Markdown | **WebView + asset pipeline**：`markdown.js`(12KB) 解析、`markdown.css`(16KB)、明暗两套 `template*.html`、`syntax_*.css` 高亮、`suggested_changes.css` 展示 diff；`WebViewAssetLoader` 挂 /assets/ | `component/markdown/GithubMarkdown.kt:98,187-212`、`assets/webview/*` |
| 导航 | 类型安全 `Route : NavKey`（miuix-nav）+ `navigationevent` 返回手势 | `ui/navigation3/Routes.kt`、`MainActivity.kt` |

## 2. 三种落地方式（按许可风险排序）

| 方案 | 做法 | 许可风险 | 工作量 | 适用 |
|---|---|---|---|---|
| **A. 引入 Miuix 依赖 + 自写壳**（推荐） | 加 `top.yukonga.miuix.kmp:miuix:0.9.4`（Apache-2.0，可用于 MPL 项目），在其组件上组合出 DSH 界面；FloatingBottomBar 按 compose-miuix-ui **官方 Apache-2.0 示例**重写 | **低** | 中 | 想要真 Miuix 观感 |
| **B. 只复刻视觉、不引依赖** | Material3 + 自绘 Shape/Shadow/Blur（卡片圆角 16–20dp、`surfaceContainer` 分层、底栏模糊+1dp 描边） | 最低（原创） | 中高（液态玻璃难） | 完全掌控、APK 体积敏感 |
| **C. 直接复制 GPL 代码** | 搬 `component/**` 与 `screen/**` | **高**：GPL-3.0 传染，整包必须 GPL | 低 | 只有愿意整包 GPL 才可行 |

> **推荐 A 的变体**：Miuix 走 Maven（Apache-2.0）拿观感；FloatingBottomBar 参照 compose-miuix-ui 官方示例重写；GPL 仓库只当“设计参考”，**不复制任何 .kt**。这样 fork 仍是 MPL-2.0。

## 3. 信息架构（移动端）

````
┌───────────────────────────────────────────────┐
│ 顶部：会话标题 + 状态胶囊（连接 / 运行中 / 待审批）  │  ← 轻量 TopAppBar，滚动折叠
├───────────────────────────────────────────────┤
│  会话抽屉（左滑出 / 全屏页）                       │  ← 卡片行 + 搜索栏 + 分组排序
│            ↓ 选中会话                            │
│  聊天页（唯一主舞台）                             │  ← 消息 + 工具卡片 + 输入区 + 审批 Sheet
├───────────────────────────────────────────────┤
│  浮动底栏 FloatingBottomBar                      │  ← 聊天 / 轨迹 / 文件 / 设置
└───────────────────────────────────────────────┘
````

| 区域 | 取用 KernelSU 风格中的 | 落到 DSH 的形态 |
|---|---|---|
| 底栏 | `FloatingBottomBar` + `MainPagerState`（spring 切页） | 4 目的地：Chat / Trajectory / Files / Settings；宽屏切 `NavigationRail` |
| 会话抽屉 | `SuperSearchBar` + `SegmentedList`/`DropdownItem` | 分组（工作区/列表）、排序（手动/更新）、归档过滤、置顶 |
| 设置 | `SettingsItem` + `TonalCard` | 实例、登录、模型、外观、通知、关于 |
| 审批/提问 | `Dialog`（Scale 动画）+ `WarningCard` | 默认底部 Sheet（拇指可达）；危险操作红卡 + 二次确认 |
| 工具卡片 | `TonalCard` + 折叠行 | 保留 fork 的 `ChatRows` 信息密度，只换外观 |
| Markdown | `GithubMarkdown` 的 WebView 方案 | 与 fork 现有 `ui/markdown` 二选一（见 §4） |
| 主题 | `ColorMode` 7 档 + `keyColor` + `PaletteStyle` | 设置页：跟随系统/亮/暗/AMOLED/Monet + 主色选择 |

## 4. 与 fork 基线的冲突点（提前定）

| fork 现状 | KernelSU 风格要求 | 处理 |
|---|---|---|
| `ui/theme/DshTheme.kt` 忠实复刻 Web 端 `--dsw-*` 令牌（`DshColors` 100+ 字段） | 换成 Miuix + Monet | **保留 `DshColors` 作“DSH 语义层”**，Miuix 只管外观；否则工具/审批状态色失真 |
| `ChatScreen.kt`(3267) / `SessionsDrawer.kt`(2283) / `ChatRows.kt`(1852) | 大圆角、浮动栏、新交互 | 只改**外框**（Scaffold/TopBar/BottomBar/Drawer/Sheets），不动行内渲染 |
| `ui/markdown/Markdown.kt`(591)+`MarkdownView.kt`(514)：原生 Compose | 参考项目用 WebView | **保持原生**（流式更平滑、无 WebView 内存峰值）；公式/复杂表格按需降级 |
| 自定义抽屉拖拽手势 | 浮动底栏 + 抽屉 | 保留手势只改视觉；底栏向下滚动时自动隐藏 |
| 已有 `ViewTabs`（Chat/Trajectory 页内标签） | 新增底部 pager 导航 | pager 管“页”，`ViewTabs` 管“页内视图”，不重复 |

## 5. 设计规范（可直接给实现）

**形状与间距**
- 卡片圆角 16–20dp；Sheet 顶部 24–28dp；底栏胶囊高 56–64dp 且圆角 = 高/2
- 页面水平内边距 16dp；卡片间距 12dp；列表行高 ≥48dp（触控目标 56dp）
- 卡片默认无描边，靠 `surfaceContainer` 与背景色差分层；仅浮层加 1dp 描边 + 模糊

**颜色**
- 语义色沿用 `DshColors`：`accent`（业务主色）、`success`/`warn`/`error`（工具与审批状态）、`labelPrimary/Secondary/Tertiary`
- 外观档位：跟随系统 / 亮 / 暗 / AMOLED（纯黑 #000 背景 + #0A0A0A 卡片）/ Monet 三档
- 主色：默认 DSH 品牌色，可切 16 色 key color

**动效**
- 底栏切页：弹簧（dampingRatio 0.75 / stiffness 400）
- 弹窗：scale 0.92→1.0 + fade，180ms
- 流式输出：不做逐字动画，仅在段落边界 120ms 淡入
- 危险操作：红卡 + `HapticFeedbackType.LongPress` 触觉

**排版**
- 正文 15–16sp / 行高 1.6；代码 13sp 等宽；标题 22/18/16sp
- 会话标题 15sp 单行省略；时间戳 12sp `labelTertiary`

## 6. 交付顺序（与里程碑对齐，每步可单独出包）

| 步骤 | 内容 | 依赖 | 验收 |
|---|---|---|---|
| D1 | 建 `:design` 模块：Miuix 依赖 + `DshTheme` 语义层适配 + `ColorMode` 7 档 | 无 | 设置页切外观并持久化 |
| D2 | Scaffold 换装：TopBar / FloatingBottomBar / 4 目的地 pager | D1 | 三页可切换，宽屏变 Rail |
| D3 | 列表与设置组件换装（SegmentedList / SettingsItem / TonalCard） | D1 | 设置页视觉对齐参考 |
| D4 | 抽屉 + 搜索栏换装 | D2,D3 | 分组/排序/归档可用 |
| D5 | Sheet/Dialog 统一（审批、提问、模型选择） | D1 | 审批走 Sheet，危险操作红卡 |
| D6 | 聊天行视觉微调（气泡、状态点、工具卡折叠行） | D1 | 语义色与 Web 端一致，无回归 |
| D7 | 动效与触觉打磨 | D2 | 切页/弹窗/危险操作动效到位 |

## 7. 风险

| 风险 | 说明 | 对策 |
|---|---|---|
| GPL 污染 | 参考仓库 GPL-3.0；误复制组件会让整个 fork 必须 GPL | 只读截图与思路；**禁止 cp 任何 .kt**；Miuix 走 Maven |
| 版本漂移 | Miuix 0.9.4 与 Compose BOM 2026.09 / material3 1.5.0-alpha28 强绑定 | `libs.versions.toml` 固定；升级走单独分支验证 |
| 双皮肤成本 | 参考项目维护 Miuix + Material 两套 | **我们只做一套**（Miuix 观感），不做 Material 皮肤 |
| 底栏遮挡输入区 | 浮动底栏与聊天滚动冲突 | 输入区抬升 = 底栏高 + 8dp；向下滚动自动隐藏 |
| 原生 vs WebView Markdown | 参考用 WebView、fork 用原生 | 先原生；公式/复杂表格按需降级，避免双实现 |
