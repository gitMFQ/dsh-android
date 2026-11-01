# 阶段 2 计划：外壳换装（可执行）

> 写于 2026-10-31，阶段 1 完成之后。**替代 [ROADMAP.md](ROADMAP.md) 阶段 2 的六行表格**——那六行是「要什么」，本文是「怎么做、按什么顺序、每步怎么验证」。
> 依据全部来自实测（`path:line` 可查），标注了「**未验证**」的是开工时必须先确认的假设。

## 0. 一句话

阶段 1 把代码拆成了五个模块（`:core:model` / `:core:term` / `:core:config` / `:core:protocol` / `:design`），**像素一个没动**。阶段 2 才开始真正改外观，而它和阶段 1 是**完全不同的风险类型**：阶段 1 错了是编译不过，阶段 2 错了是「看起来不对」——只有截图基线能发现。

## 1. 开工前必须先知道的六件事

### 1.1 截图基线是唯一的验收手段，而它只能走 CI

| 事实 | 后果 |
|---|---|
| 本机 aarch64 没有可用 aapt2（[BUILD-AND-CI.md](BUILD-AND-CI.md) §0） | `recordRoborazziDebug` 跑不了，**基线只能由 `screenshots.yml`（workflow_dispatch）在 CI 录** |
| `verify.yml` 的 `Screenshot gate` 在 PR 上跑 `verifyRoborazziDebug -Pscreenshots` | 换皮 PR 只要动了界面，**门禁必红**，除非先录新基线 |
| 录基线 ≈ 8–10 分钟一轮 CI | 每屏换皮的反馈循环成本 = 一轮 CI |

> **这是阶段 2 最重要的流程约束**：不能「改完一屏就等着看效果」。必须先改完一批、录一次基线、看 diff 图，否则会在一轮 CI 上反复烧时间。
>
> **基线只在手动录制时更新，永不随 push 自动走**（[HANDOFF-NEXT.md](HANDOFF-NEXT.md) §2.1）。自动化了就等于「自己和自己比」，门禁失效。

### 1.2 基线现在只覆盖 4 屏，阶段 2 要动的屏大多没覆盖

> **2026-11-01 更新：这一段已过时，见 §2 的 2.2a / 2.2c。** 现在有 **8 张**基线：原 4 张 + `composer.png` / `files.png` / `permission.png` / `terminal.png`。

| 已有基线 | 阶段 2 会动它吗 |
|---|---|
| `about.png` | 会（设置/关于换装） |
| `startup.png` | 会（启动页视觉） |
| `drawer.png` / `drawer-empty.png` | 会（抽屉换装） |
| `composer.png`（2.2a） | 会（底栏/pager 会挪它） |
| `files.png`（2.2a） | 会（容器换装） |
| `permission.png`（2.2a） | 会（2.9 Sheet 统一） |
| `terminal.png`（2.2c） | 会（2.4/2.5 外壳换装会挪它） |

**仍然没有基线覆盖**的：**聊天主界面**（`ChatScreen`，见 §2 修正第 3 条：需要一次独立重构才抓得到）、Trajectory、Router，以及 11 个 Sheet 里的另外 10 个。

> **所以阶段 2 的第 1 个任务不是换皮，是补基线**。没有基线保护就换皮 = 改坏了没人知道。
>
> 补充（2026-11-01）：补基线这件事已经做到「**能抓的都抓了**」。剩下的三个屏不是没来得及，而是**当前入口就是 view model**，抓它们要先把屏拆成无状态部分 —— 那是生产代码变更，不能混进一个验收标准为「只增加截图」的 PR。聊天主界面尤其值得单独排期：它是读者停留最久的屏，也是 2.4/2.5/2.6 改动最大的屏。

### 1.3 当前导航结构不是「底栏 + 4 目的地」

实测 `ui/ChatScreen.kt:2265`：

```kotlin
private enum class ChatView(val label: String) {
    CHAT("Chat"), TRAJECTORY("Trajectory"), TERMINAL("Shell"), ROUTER("Router"), ...
}
```

- 现在是**抽屉 + 顶部 `ViewTabs` 分段标签**（`ChatScreen.kt:2304`），不是底栏。
- 有 **5 个值**，不是 ROADMAP 写的 4 个。`ROUTER` 是**条件性**的：只在选中模型属于本机回环 provider 时出现（`ChatScreen.kt:2277-2279`）。
- `Files` 不是 `ChatView` 的一员，是独立的 `showFiles` 面板。

> **后果**：「4 目的地 pager」这个说法与实际不符。底栏要么放 4 项（Chat/Trajectory/Shell/设置），要么把条件性的 `ROUTER` 也塞进去——后者会让底栏项数在运行中变化，是明确的坏设计。**开工第一件事是定这个。**

> **2026-11-01 更新：这一节的问题已经回答了，见 §2.2 的结论表。答案是可以共存，代价是测试 JVM 要 ≥21、以及每个 miuix 屏都要先写调色板适配器。**下面保留原始取证，因为「为什么当时必须做实验」本身是方法论。

### 1.4 miuix 已依赖但是零使用

`app/build.gradle.kts` 有四个 miuix 依赖（ui / preference / icons / squircle），而全仓 `MiuixTheme` / `top.yukonga.miuix` 的**使用处 = 0**（实测 grep）。

- miuix 的 `MiuixTheme` API 已确认存在（`top.yukonga.miuix.kmp.theme.MiuixTheme`，有 `getColorScheme` / `getTextStyles`），`Colors` 是 53 角色。
- 但**没有任何组件在用它**，所以「miuix 能不能和现有 M3 同栈共存」这件事**尚未被验证**。

> **这是一个必须先做实验的假设**，不是可以边做边看的事：如果 miuix 的 `OverlayDialog` 要求挂在 `MiuixTheme` 下、而 `MiuixTheme` 与现有 `DshTheme` 的 `LocalDshColors` 冲突，那整个「用 miuix 做设置行件」的策略要改。

### 1.5 首帧闪白现在有 7 档外观，静态资源色跟不上

| 位置 | 值 |
|---|---|
| `res/values/colors.xml:4` | `dsh_window_bg = #0B0F14` |
| `res/values/themes.xml:9` | `android:windowBackground = @color/dsh_window_bg` |
| Compose 暗色 `bgBase` | `#151517`（`DshTheme.kt:109`） |

三者**本来就不一致**。阶段 1 之前是个小瑕疵；现在有 7 档外观（含 AMOLED 纯黑、Monet 动态色），**冷启动那一闪会非常明显**，而且亮色和 Monet 用户看到的是深色。

> 这条**不依赖任何换皮工作**，可以独立先修，且能用现有 4 张基线验证。

### 1.6 两套间距刻度语义冲突（1.4b 引入的债）

见 [DESIGN-TOKENS.md](DESIGN-TOKENS.md) §4.2：`DshSpacing.xxl=24/xxxl=32` vs `KsuTokens.Space.xxl=20/xxxl=24`。

当前无害（`KsuTokens.Space` 零调用点），但阶段 2 一写新代码就会同屏两个间距且不报错。**一行删除即可偿还**，应作为第一个 PR。

## 2. PR 序列（单变量，每步可验证）

> 每步一个 PR，遵守阶段 1 的方法论。**与阶段 1 的区别**：这里的验收是「基线 diff 符合预期」，不是「测试数不变」。

| # | PR | 内容 | 验收 | 风险 |
|---|---|---|---|---|
| **2.0** ✅ | `fix/window-background` | **已交付（PR #11）**。窗口底色跟随外观。原计划的「`MainActivity` 已持有 `ui.themeMode`」只在 `setContent` 之内成立 —— 那时窗口已经按静态资源画过一遍，等于**没修**。实际做法：把 `DshTheme` 里的 `when` 提成纯函数 `dshPalette(themeMode, systemDark, monet)`，`toMonet` 去掉 `@Composable`（它只需要 `Context`）；`WindowBackground.apply` 在 `onCreate` 里、`setContent` 之前画 `decorView`；`ConfigStore` 记住上一次读到/写入的外观，避免在冷启动关键路径上开 `EncryptedSharedPreferences`。静态资源 `#0B0F14 → #151517`（对齐 `bgBase`） | 4 张基线**不变**（实测）；`tests=1136`（+8 `DshPaletteTest` / +5 `WindowBackgroundTest`） | 低 |
| **2.1** ✅ | `refactor/drop-ksu-spacing` | **已交付（PR #12）**。删掉 `KsuTokens.Space`（调用点 = 0），间距统一到 `DshSpacing`；理由写进 `KsuTokens` 类注释 + DESIGN-TOKENS §4.2 | `tests=1136` / `screenshots=4` 均不变（实测） | 极低 |
| **2.2a** ✅ | `test/baseline-expand` | **已交付（PR #13）**。补基线 **Composer / Files / Permission sheet** 三张。**聊天主界面没做**：`ChatScreen(vm)` 单文件 100+ 处 `ui.` 读取 + `LaunchedEffect` 里发网络 + 1 秒走时器，要抓它得先拆「无状态屏」，属改生产代码 | `screenshots=7`（实测），原有 4 张 sha256 逐字节相同 | 低（纯测试） |
| **2.2b** ✅ | `refactor/terminal-stateless` | **已交付（PR #14）**。`TerminalScreen` 的树抽成 `internal fun TerminalPanel(state, emulator, …)`，8 处 `vm::x` 换成参数；`TerminalScreen` 保留 `StateFlow`/RPC/离场 hook，成为唯一调用方。**纯搬运**，不加测试 | `tests=1136` / `screenshots=7` 均不变（实测 = 搬运没改树） | 低 |
| **2.2c** ✅ | `test/baseline-terminal` | **已交付（PR #15）**。给 `TerminalPanel` 录 `terminal.png`（80×24、running、十几行输出） | `screenshots=8`（实测）；原有 7 张 sha256 逐字节相同 | 低（纯测试） |
| **2.3** ✅ | `spike/miuix-adoption` | **已交付（PR #16）—— 结论：行。** 一个 `SwitchPreference` 套在 `DshTheme` 里，实测能画。两个必须带进后续 PR 的结论见 §2.2 下方 | `tests=1136`（不变）/ `screenshots=9` | 已完成 |
| **2.4** | `feat/floating-bottom-bar` | 浮动底栏（自绘玻璃层，**不引 miuix-blur**——它 minSdk 33）：API 31+ `Modifier.blur`，<31 纯色 + 描边。替换顶部 `ViewTabs`。**调色板适配器先落地**（`design/.../MiuixPalette.kt` + `DshTheme` 根部提供 `MiuixTheme`），**状态栏跟随 7 档外观**（`SystemBars.kt`），**ROUTER 移出目的地列表**（见 §2.4 结论）。底栏 **3 项固定**：Chat / Trajectory / Shell | 基线新增底栏 2 张；原有 9 张不变；`tests` 原数不减 + 新增 | 中 |
| **2.5a** ✅ | `refactor/chat-stateless` | **已交付**。抽 `ChatPanel(state, actions, terminal)` 无状态壳 + `ChatUiState` / `ChatActions`；`ChatScreen` 退化为取 `StateFlow` 与拼参数的外壳（3 284 → 226 行）。见 §2.5a | `tests` 原数不减；11 张基线逐字节不变 | 中 |
| **2.5b** ✅ | `test/baseline-chat` | **已交付（PR #19）**。`chat-hero` + `chat-transcript` 两张，原有 **11 张 sha256 逐字节相同**。**录制过程中 CI 抓到两个真缺陷**，见 §2.5b | `screenshots=13` | 低 |
| **2.5c** ✅ | `feat/pager-navigation` | **已交付（PR #21）**。`HorizontalPager` 接管三个目的地；`pagerState` 是**唯一可写状态**，`view` 由 `currentPage` 派生。**13 张基线逐字节不变** | `screenshots=13`（不变） | 中 |
| **2.5d** ✅ | `feat/nav-rail` | **已交付（PR #22）**。`WIDE_LAYOUT = 600.dp` 之上底栏换 `NavigationRail`；用 `BoxWithConstraints` 而非 `material3-window-size-class`（见 §2.5c） | `screenshots=14`；原有 13 张不变 | 中 |
| **2.6** | `feat/topbar-collapse` | TopBar 折叠 + 状态胶囊 | 滚动折叠无跳变 | 中 |
| **2.7** | `feat/drawer-reskin` | 抽屉换装（搜索栏 / 分段列表 / 分组排序） | 基线 diff 符合预期 | 中 |
| **2.8** | `feat/settings-reskin` | 设置页 + 关于页换装 | 项全部可达 | 中 |
| **2.9** | `feat/sheet-unify` | Sheet/Dialog 统一（11 个）；危险操作红卡 + 二次确认 | 审批走 Sheet + 二次确认 | 中 |

> **2.3 是一道闸门**：如果 miuix 实验失败，2.5 / 2.7 / 2.8 / 2.9 全部改为**自绘**（工期 +1 周，但零依赖风险）。这个判断必须在 2.4 之前做完。

### 2.2 闸门的结论（2026-11-01，PR #16）—— **miuix 可行**

三道问题全部实测过，答案是「能，但有两个前提」：

| 问题 | 答案 | 证据 |
|---|---|---|
| miuix 能画在 `DshTheme` 里吗 | **能** | `SwitchPreference` 用 miuix 自己的文字样式、开关、行度量画出来了；基线 `miuix-probe.png` 在 master |
| 前置条件 | **测试 JVM 必须 ≥ 21** | miuix 0.9.4 发的是 **Java 21 字节码（class file 65）**。D8 能塞进 APK（debug 包 4868 处 miuix 引用，Build 绿），但 Robolectric 的 JVM 要能**加载**它：17 上 `UnsupportedClassVersionError`，21 上正常。`verify.yml` / `screenshots.yml` 已升到 21 |
| 调色板会自动传过去吗 | **不会** | 不给参数时开关是 **`#3482FF`（miuix 默认强调色）**，本 app 亮色强调色是 `#4176E6`。同屏两个强调色正是 7 档外观要防的事 |

> **对 2.5 / 2.7 / 2.8 / 2.9 的直接影响**：每个 PR 的**第一件事是写调色板适配器**（用 `DshTheme.colors` 构造 miuix 的 `Colors`，`Colors` 有 `copy`，可行），而不是先摆组件。谁跳过这一步，谁就会做出「配色不跟外观走」的 miuix 屏 —— 而且**不会报错**。
>
> 另：JDK 17→21 升级本身已验证是**版本下限调整、不是渲染变更** —— 升完重录，另外 8 张基线 sha256 逐字节相同。

> **流程坑（已踩）**：`Screenshots` workflow **用的是被 checkout 那个分支上的 workflow 文件**。`gh workflow run screenshots.yml --ref master -f branch=feat/x` 跑的是 **master 的**那个文件（JDK 17），不是 feat/x 的。要录一个改了 workflow 的分支，必须 `--ref feat/x`。

### 2.4 的三个结论（2026-11-01）

#### (1) 调色板适配器：先写它，而且它抓到了两个真 bug

`design/src/main/java/uk/xa0/dsh/ui/theme/MiuixPalette.kt` —— `DshColors.toMiuixColors()`，53 个角色。`DshTheme` 在根部 `remember(palette)` 后套 `MiuixTheme(colors = …)`，于是**任何 miuix 组件不传参数也拿到本 app 的配色**，「两个强调色同屏」在结构上不可能再发生。

两个只有写出来才会暴露的问题（都由测试抓到，不是肉眼）：

| 问题 | 现象 | 修法 |
|---|---|---|
| `disabledSecondary` 用 `borderL3.copy(alpha = 0.5f)` | `borderL3` 本身是半透明（12%），`copy` 是**替换** alpha 而非相乘 → 禁用轨道比启用轨道还重 3~4 倍（亮 #7F7F7F vs #E0E0E0） | 先压平再减半：`opaque(opaque(borderL3, base).copy(alpha = 0.5f), base)` |
| `onError` 借用 `sendGlyph` | 该 token 跟着强调色走，**Monet 下跟着壁纸**，实测 3.99:1 | 改成按对比度在「亮墨 / 纯黑」里选（`inkOn()`），七档外观全部 ≥ 4.5:1 |

> 顺带记一条**关于 token 本身**的事实：亮色调色板的 `error = #EC1313` 对**白字**只有 **4.4976:1**，够不到 4.5。这不是适配器的错，是 `LightColors.error` 的天花板；动它等于重刷全 app 的错误面，不属本 PR。

#### (2) 状态栏：`enableEdgeToEdge()` 跟的是**系统**夜间模式，不是外观

这是本次发现的一个真缺陷。`enableEdgeToEdge()` 无参调用的 `SystemBarStyle.auto` 用 `Configuration.UI_MODE_NIGHT_MASK` 决定图标明暗，而 app 有 7 档外观。四档是错的：

| 外观 | 系统为亮时 | 结果 |
|---|---|---|
| AMOLED / Dark | 亮 | **深色图标画在 #151517 上** |
| Light / Monet light | 暗 | **浅色图标画在白色上** |

修法：`design/.../SystemBars.kt`，用同一个 `dshPalette` 推出 `iconsAreLight`，经 `WindowCompat` 写 `isAppearanceLightStatusBars` / `…NavigationBars`；`MainActivity` 在 `onCreate`（Compose 之前）和 `LaunchedEffect(themeMode, systemDark)` 里各调一次。**颜色不用设**：API 35+ `setStatusBarColor` 已是 no-op，而 app 根部 `Box` 本来就铺 `bgBase` + `statusBarsPadding()`，状态栏显示的已经是调色板底色。


> **补记（审计后追加）**：上面这条在 API 26–28 上**不止图标**，导航栏的**底色**也一样错 —— 那三档平台用 `auto(DefaultLightScrim, DefaultDarkScrim)` 按**系统**夜间设置算 scrim，于是强制 Dark + 系统亮 → 近白条（`0xE6FFFFFF`）配浅色图标，还是同一个 bug 换了个位置。29+ 上 `nightMode == 0` 让 `getScrimWithEnforcedContrast` 直接返回 0，所以上面那几档是透明的、图标标志才是唯一权威。修法：`SystemBars` 在 `SDK_INT < Q` 时显式写 `window.navigationBarColor = palette.bgBase`（`themes.xml` 救不了，`enableEdgeToEdge` 会在 `onCreate` 里覆盖它）。
>
> 另外 `SystemBars.apply` **不传** `monetPalette`：`ThemeMode.isDark` 只由外观决定、`toMonet` 从不改 `isDark`，传壁纸色只会在 `onCreate` 主线程白建一次 `dynamicColorScheme`。`WindowBackground.apply` 则确实需要它（它画的是 Monet **颜色**）—— 这就是两个调用唯一的差别。
> 基线看不到它 —— 状态栏不是 Compose 树的一部分，和 2.0 的窗口是同一个结构性盲区。所以补的是 `SystemBarsTest`，不是截图。

#### (3) ROUTER 去留：**移出目的地列表，改成头部 seat + sheet**

判据不是「能省多少行」，而是**条件该放在哪里**：头部那排 seat 本来就是变宽右对齐的簇，三个成员（lineage / Files / Jobs）**全是条件性的**；底栏是定数等宽项行。同一个条件放前者零成本，放后者是结构性的。

- `RouterTab(target: RouterTarget)` 是**非空签名**、18 处解引用 —— 做成永久第 4 项就要改可空性并新写第 4 种「没有 router」措辞，还推翻 `LlamaRouter.kt:138-147` 写明的「the tab cannot appear for a cloud route」。
- 冷启动时 `localProviders` 先是空 Map，**即便本机用户也是「先 3 项后 4 项」** —— 项数变化是真实存在的，不是理论担忧。
- `DshViewModel` / `RouterTab.kt` / `LlamaRouter.kt` **零改动**，可逆性极高。

结果：`ChatView` 只剩 CHAT / TRAJECTORY / TERMINAL，底栏恒 3 项；`shownView` 及其 12 处下游、`BASE_VIEWS`/`ALL_VIEWS`、`ViewTabs` 整体删除（净减约 75 行）。

> **与 ROADMAP §阶段 2 的对账**：那里写「4 目的地 pager（Chat/Trajectory/Files/Settings）」是**待修正的笔误** —— 代码里 Files 是 `showFiles` 面板、Settings 是 `showSettings` 的 sheet，都不是目的地。第 4 项若要补，正确候选是把 `showFiles` 提升成 `ChatView.FILES`，**那是独立 PR**（本 PR 保持 3 项固定，不混两个变量）。


#### (4) CI 抓到的两件事，本机抓不到

这台机器编不了 `:app` 的测试源码（aapt2，`BUILD-AND-CI.md` §0），所以第一版是带着未验证的 `SystemBarsTest` 推上去的。CI 红了，而且红得对：

| 缺陷 | 表现 | 根因 |
|---|---|---|
| `SystemBars` 的极性反了 | `SystemBarsTest` 6/9 失败 | `isAppearanceLightStatusBars = true` 说的是**「这条栏是浅色，所以图标画深色」** —— 它描述的是**背景**。按「图标是浅色」去理解就会把调色板取反，于是修出了**方向相反的同一个 bug**。现已改名 `lightBackground`，把语义放进名字，并逐档断言方向而不是让它隐含 |
| `build.yml` 还在 JDK 17 | Debug APK 任务里 `:design:testDebugUnitTest` **25 个测试全挂**，`NoClassDefFoundError` | 2.4 把适配器搬进 `:design`，miuix 的 Java 21 字节码就上了**那个模块**的测试类路径。症状像「测试文件写坏了」，实际是 JDK 版本。已升 21；`release.yml` 留 17 并写明原因（它只 `assembleRelease`，D8 吃 21 字节码没问题） |

> **可复用的判据**：一个测试类**整片**因 `NoClassDefFoundError` / `ExceptionInInitializerError` 挂掉时，先看 JDK 版本，再看测试本身。而「6 个测试按同一个方向失败」几乎总是**命名与语义反向**，不是六个独立的 bug。

### 2.5a 的三条结论（2026-11-01）

#### (1) 拆法：状态一坨、动作一堆，不要按字段拆

`ChatPanel(state: ChatUiState, actions: ChatActions, terminal: @Composable (Modifier) -> Unit)`。

`ChatUiState.ui` 是**整个 `UiState`**，不是三十个扁平字段。理由不是省事，是这次拆分的目的是「让屏幕可被拍照」，而不是「让参数好看」：`ChatPanel` 有 30 处从 `ui` 上取字段，逐字段拆开是一次**改名**，不是一次重构，而它带来的每个新参数都是下一次改屏时的一处冲突。真正需要「是数据」的是**边界**——面板里没有 `DshViewModel`、没有 `collectAsState`、没有 RPC——而不是每个字段单独成参。

#### (2) 哪些东西必须留在壳里（这是判据，不是清单）

判据一句话：**「是连接，还是连接的画法」**。留在 `ChatScreen` 的只有四类：

| 留下的 | 为什么 |
|---|---|
| `collectAsStateWithLifecycle` 的十几个订阅 | 面板收的是值；订阅是「跟 host 说话」 |
| 一秒走时器（`nowMs`） | 时钟是对着 host 时间线的一次**动作**；面板改成收 `elapsedMs: Long?` 一个数，截图才能定格 |
| 首帧的 `loadHostSettings()` | 同上，是一次 RPC |
| `TerminalScreen` | 它自己 collect `StateFlow` 并在 `LaunchedEffect` 里 `enterTerminal`/`leaveTerminal`，所以面板收的是**已组好的 Composable 槽位** `terminal(Modifier)`，而不是知道怎么造它 |

> **踩到的一处**：第一版把 `terminal` 槽位写好了，却忘了把 `vm.terminal` 订阅接上——`val seats = terminal.terminals.size` 于是变成 `state.terminal.terminals.size`，而 `state.terminal` 恒为默认值。症状是「关掉最后一个 shell 不再自动回 Chat」，**编译完全通过**。这条和 §2.4 的「一条 import 指向哪个模块看不出来」是同一类：**拆分时最容易丢的是没被任何一个测试覆盖的那条连线**。

#### (3) 「纯搬运」的证据是 diff，不是感觉

做法：从 `git show HEAD:...ChatScreen.kt` 里取出原 body，用与改代码**同一套**正则做 `vm.` → `actions.` 替换，再逐行 diff 新 `ChatPanel` 的 body。剩下的差异应该**只有**下面六处，多一处就是手滑：

1. 签名行（`fun ChatScreen(vm)` → `internal fun ChatPanel(state, actions, terminal)`）；
2. 六个 `collectAsStateWithLifecycle` → 直接读 `state`；
3. 首帧 `loadHostSettings` 移走；
4. 一秒走时器 → `state.elapsedMs`；
5. `TerminalScreen(...)` → `terminal(Modifier.weight(1f))`；
6. `LaunchedEffect(ui.draftRestore)` → `LaunchedEffect(state.draftRestore)`。

> 还有两处**故意的行为修正**，写在这里以免下一轮 review 当成漂移：`onSend` 拆成 `send(text, submitMode())`（会话内）与 `sendDeferred(text)`（hero），因为原代码 hero 用的是 `vm.send(text)` 的一参重载、会话内用的是两参——**两者不是同一个调用**，抽成一个 lambda 会把 hero 也套上 busy 策略。

### 2.5b 抓到的两个缺陷（2026-11-01）

录一张截图失败了两次，**两次都不是测试写坏了**。这正是「先补基线再改屏」的价值：这两个 bug 都不是 2.5b 引入的，是本来就存在、只是**没有任何东西能把它们触发出来**。

#### (1) `performMeasureAndLayout called during measure layout`

`TailFollow` 的 rearm 调 `scrollToItem`，它**立即测量**。当这个 effect 跑在「正在做首次测量」的那次组合里时，等于在 `measureAndLayout` 内部再调一次，Compose 直接抛异常。

这个排布不是测试造出来的，**是会话屏自己的**：`ChatPanel` 故意跨会话复用 `LazyListState`，并让它从「越界」开始（`initialFirstVisibleItemIndex = Int.MAX_VALUE`），这样首帧画的是最新一行；打开会话时 key 变了、rearm 跑了。**手机上之所以没炸，只是因为常规路径里列表那时已经布局完了** —— 截图 fixture 是冷的，正好撞上手机从未走过的那个分支。

修法：`requestScrollToItem`，它设置「下一次测量要用的位置」而不是现在测。可观察行为一样，且这里完全不碰布局。回归测试用屏幕的真实形状写：**列表从越界开始、且里面已经有行**。

#### (2) `Compose did not get idle after 86857 attempts in 60 SECONDS`

transcript 分支调 `DiagFrames()`，那是一个包着 `withFrameNanos` 的 `while (true)`。在设备上不可见（帧回调本来就是 app 的日常），在 Compose 测试里是致命的：`waitForIdle` 等的是组合静止，而帧循环是**永久的下一帧请求**。

**这比 2.5b 老得多**：`diag/DiagScroll.kt` 自己的文件头写着「Nothing here consumes a pointer event, holds a gesture, requests a frame, or writes Compose state」，而它是这个包里唯一违背这句话的东西。修法：只有 `ScrollDiag` 真的 attach 了才采样——app 在 `DshApplication.onCreate` 里 attach，测试 harness 不 attach。

> **可复用的判据（2.5c 必读）**：`diag/` 里的东西必须**惰性**。任何 `withFrameNanos` / `rememberInfiniteTransition` 都会让 Compose 永不 idle —— 它不只是「测试环境的问题」，而是**任何想把这一屏拍下来的尝试都会失败**。同理，pager 的弹簧动画在静止时必须是静止的，否则 `chat-*` 这两张基线会变成「录一次失败一次」。

### 2.5c / 2.5d 的三条结论（2026-11-01）

#### (1) pager 之后，「当前目的地」只能有一个可写来源

`pagerState` 是唯一的可写状态，`view = ChatView.entries[pagerState.currentPage]` 是**派生值**。底栏高亮、返回键、「关掉最后一个 shell 回 Chat」三条规则都读同一个派生值，所以**一次滑动同时更新三者**。

这是 §7.3.4 那条「别两个都存」的落实。反例很具体：如果同时保留 `var view` 与 `pagerState`，第一次只写其中一个时它们就分叉，症状是**底栏高亮 A 而屏幕上是 B**。

> **代价**：`view` 不再是 `rememberSaveable`，所以进程被回收后不会恢复到 Shell 页 —— 恢复语义交给 pager 的 `initialPage = CHAT` 与「切会话回 Chat」的 effect。这与旧行为一致（旧代码也只是 `rememberSaveable` 到会话粒度）。

#### (2) 2.5c 的 13 张基线**逐字节不变**，这是意料之中而不是门禁失效

pager 改的是**页面怎么进来**，不是页面长什么样：三个目的地在第一页的静止状态与改动前完全一致。所以 13 张基线全同，恰恰证明「重构没换皮」。

> 判据：**动画类改动在静止帧上是零像素差**。如果 2.5c 让某张基线动了，那才是要查的问题。

#### (3) 2.5d 用 `BoxWithConstraints`，刻意不引 `material3-window-size-class`

§7.3.2 说过那个库已经作为 miuix 的传递依赖在类路径上。**实测比预想的更糟**：Gradle 的 transform 缓存里**两个变体都在** —— `org.jetbrains.compose` 那份和 `androidx` 那份，包名相同、坐标不同。

与其去解这个冲突（显式声明覆盖、再确认 BOM 版本），不如**不问那个问题**。屏幕真正要回答的只有「内容旁边还有地方吗」，`BoxWithConstraints` 的 `maxWidth` 直接回答它，零新增依赖、零重复类风险。

> **可复用**：**「有一个库能用」不等于「应该用」**。当一个问题有等价且零依赖的本地答法时，引入一个已经在类路径上重复的库是**净负收益** —— 它把「窗口多宽」这件事变成一个需要裁决的第三方结论。

> **基线要单开一个类**：Robolectric 的窗口尺寸是**类级 `@Config` qualifier**，测试内部改不了。把宽屏 fixture 塞进 411dp 的手机类里，会得到一张**任何真机都不会画出来**的「宽屏」图（`BoxWithConstraints` 只会看到 411dp）。所以 `WideBaselineTest` 是独立文件，尺寸写在类上。

### 2.1 执行中修正的四件事（2026-11-01）

1. **2.0 的原方案不成立，已改**：原文说「`MainActivity` 在 Compose 之前就持有 `ui.themeMode`，所以可以据此设窗口底色」。实际 `ui.themeMode` 来自 `setContent` 里的 view model —— 拿到它时窗口早已按静态资源画完。**真实做法是让 `ConfigStore` 记住外观，在 `onCreate` 里先画**。计划里这句话没有导致返工（方案是朝「更早拿数据」的方向修正的），但**下一个人别再照着原文写**。
2. **2.2 拆成三个 PR，不是两个**：原计划只写了「补基线」，但 Shell 那一屏不可能只加测试（它要读 view model）。于是拆成 **2.2a 补三张** / **2.2b 抽 `TerminalPanel`** / **2.2c 补 Shell**。顺序不能反：先 refactor 再 baseline，且分开发，才有「纯搬运不动像素」这条证据。
3. **聊天主界面没有基线，且这一步不做**。它的障碍不是「没有 fixture」而是「`ChatScreen` 的入口就是 9055 行的 view model」。要给它录基线必须先做一次大重构（把 `ChatScreen` 拆成「无状态屏 + 有状态外壳」），那是**独立的一个 PR，不是 2.2 的一部分**。现阶段聊天界面的保护来自 `tests=1136` 里那批 Compose 语义测试，不是像素。
4. **测试数不是常量了**：`tests` 从 1123 涨到 **1136**（2.0 的 +8 / +5）。判据从「数字不变」改为「**原有测试一个不少 + 新增有理由**」—— 与 HANDOFF-NEXT §2.1 对功能 PR 的写法一致。`screenshots` 从 4 涨到 **8**，同步更新。

> 一个反复出现的判据：**「纯搬运 PR」和「纯测试 PR」的验收是「数字不变」；功能 PR 的验收是「原数字不减 + 新数字有理由」**。2.0 / 2.2a / 2.2c 属于前者（不动界面或不改生产代码），2.2b 也属于前者（搬运），只有 2.0 是功能 PR。

## 3. 工作量分布（实测，用于排期）

换皮的机械工作量集中在这几个文件（`RoundedCornerShape` / 裸 `.dp` 计数）：

| 文件 | 行数 | `RoundedCornerShape` | 裸 `.dp` |
|---|---|---|---|
| `ui/components/ChatRows.kt` | 1856 | **18** | **66** |
| `ui/ChatScreen.kt` | 3274 | 12 | 55 |
| `ui/components/Composer.kt` | 969 | 10 | 38 |
| `ui/SessionsDrawer.kt` | 2283 | 11 | 36 |
| `ui/TrajectoryScreen.kt` | 601 | 2 | 26 |
| `ui/components/JobsCard.kt` | 808 | 12 | 23 |
| `ui/SettingsContent.kt` | 1157 | 12 | 15 |
| `ui/Sheets.kt` | 968 | 10 | 15 |

全仓：**196 处 `RoundedCornerShape`、526 处裸 `.dp`、245 处 `DshTheme.colors`**。

> 关键判断：**颜色层是最便宜的**（245 处调用点全部读 `DshTheme.colors`，换外观已由 1.4b 完成，一处不用改）。**最贵的是圆角与间距**（196 + 526 处字面量），而且改它们必然动像素、必然要重录基线。
>
> **所以阶段 2 的策略应该是：先动「容器与外壳」（底栏、pager、TopBar、抽屉外框），后动「行内视觉」**。前者文件少、面积大、易验证；后者散落在 `ChatRows.kt` 这种 1856 行文件里，风险高收益低。

## 4. 明确不做的事

| 不做 | 原因 |
|---|---|
| **主色选择（15 seed + material-kolor）** | 不是外观档位而是额外配色生成器，要引新依赖，与换皮无依赖。移出阶段 2，见 DESIGN-TOKENS §4.1 |
| **`miuix-blur`** | 模块 minSdk 33，我们 26。玻璃效果自绘 + API<31 降级 |
| **`miuix-nav`** | 要求所有路由改成 `@Serializable NavKey`，收益不抵成本 |
| **`ui/markdown/**`** | 原生 Compose 渲染已比 WebView 好，且是既有资产 |
| **`TerminalScreen` 的 Canvas 绘制 / `TerminalInputView` 的 IME** | 这两处一改就会破坏终端，而终端已有 186 个测试保护的是协议不是绘制 |
| **改 `DshViewModel`（9055 行）** | 换皮完全不碰它，这是护栏 |

## 5. 开工检查清单（新会话照着做）

```sh
# 1. 现状确认
git log --oneline -1
#    模块数应是 6（:app + 5 个）
grep -c '^include' settings.gradle.kts
#    源码里的 @Test 总数是 **1144**（2026-11-01），CI 报 tests=1136 —— 差 8 是截图测试，
#    verify.yml 按文件名把 screenshot/ 那一类单独算成 screenshots=8。
#    **别把 1144 当功能测试数**，对不上会以为丢测试了。
grep -rc '@Test' --include=*.kt app/src/test core/*/src/test design/src/test | awk -F: '{s+=$2} END {print "src @Test (incl. screenshots):", s}'
ls app/src/test/screenshots/ | wc -l

# 2. 本机能不能编译（临时改 buildToolsVersion，验完还原）
#    见 BUILD-AND-CI.md §0.0b

# 3. 每轮 CI 之后必须看的两个数字（分开报，不要合并）
#    tests=1123（或更多）  screenshots=N
```

**每一步的 PR 描述里必须写**：这一屏**故意**改成什么样，以及基线 diff 里哪几处是预期的、哪几处不是。

> 没有这句话的换皮 PR 不可 review ——「红色变成了另一种红色」在 diff 图里看不出来是有意还是手滑。
