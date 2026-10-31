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
| **2.4** | `feat/floating-bottom-bar` | 浮动底栏（自绘玻璃层，**不引 miuix-blur**——它 minSdk 33）：API 31+ `Modifier.blur`，<31 纯色 + 描边。替换顶部 `ViewTabs` | 底栏 4 项；基线新增底栏屏 | 中 |
| **2.5** | `feat/pager-navigation` | 4 目的地 pager + 弹簧切页；宽屏切 `NavigationRail` | 切页流畅；旋转保持 | 中 |
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
