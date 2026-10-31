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

| 已有基线 | 阶段 2 会动它吗 |
|---|---|
| `about.png` | 会（设置/关于换装） |
| `startup.png` | 会（启动页视觉） |
| `drawer.png` / `drawer-empty.png` | 会（抽屉换装） |

**没有基线覆盖**的：聊天主界面、Composer、Sheet（11 个）、Files 面板、Shell/终端、Trajectory、Router。

> **所以阶段 2 的第 1 个任务不是换皮，是补基线**。没有基线保护就换皮 = 改坏了没人知道。

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
| **2.0** | `fix/window-background` | 窗口底色跟随外观。**已验证可行**：`MainActivity` 在 Compose 之前就已经持有 `ui.themeMode`（`MainActivity.kt:119` 把它传给 `DshTheme`），所以可以在 Activity 里按解析出的外观**编程设置** `window.setBackgroundDrawable` 或用 `Theme.applyStyle`。默认值也从 `#0B0F14` 统一到与 `bgBase` 一致 | 现有 4 张基线**不变**（Compose 内渲染，不含窗口底色）；真机冷启动不再闪 | 低 |
| **2.1** | `refactor/drop-ksu-spacing` | 删掉 `KsuTokens.Space`，阶段 2 一律用 `DshSpacing`（DESIGN-TOKENS §4.2 方案 A） | 基线不变（零调用点）；编译通过 | 极低 |
| **2.2** | `test/baseline-expand` | **补基线**：聊天主界面、Composer、一个 Sheet、Files 面板、Shell。只增加，不改现有 4 张 | CI `screenshots=N`（N>4），原有 4 张不变 | 低（纯测试） |
| **2.3** | `spike/miuix-adoption` | **实验 PR，可丢弃**：在一个 Settings 行里用 miuix `SwitchPreference`，套 `MiuixTheme`，跑一遍基线看是否崩、颜色是否可用 | 编译 + 基线可生成 | **中**（决定 2.5 走哪条路） |
| **2.4** | `feat/floating-bottom-bar` | 浮动底栏（自绘玻璃层，**不引 miuix-blur**——它 minSdk 33）：API 31+ `Modifier.blur`，<31 纯色 + 描边。替换顶部 `ViewTabs` | 底栏 4 项；基线新增底栏屏 | 中 |
| **2.5** | `feat/pager-navigation` | 4 目的地 pager + 弹簧切页；宽屏切 `NavigationRail` | 切页流畅；旋转保持 | 中 |
| **2.6** | `feat/topbar-collapse` | TopBar 折叠 + 状态胶囊 | 滚动折叠无跳变 | 中 |
| **2.7** | `feat/drawer-reskin` | 抽屉换装（搜索栏 / 分段列表 / 分组排序） | 基线 diff 符合预期 | 中 |
| **2.8** | `feat/settings-reskin` | 设置页 + 关于页换装 | 项全部可达 | 中 |
| **2.9** | `feat/sheet-unify` | Sheet/Dialog 统一（11 个）；危险操作红卡 + 二次确认 | 审批走 Sheet + 二次确认 | 中 |

> **2.3 是一道闸门**：如果 miuix 实验失败，2.5 / 2.7 / 2.8 / 2.9 全部改为**自绘**（工期 +1 周，但零依赖风险）。这个判断必须在 2.4 之前做完。

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
#    源码里的 @Test 总数是 **1127**，但 CI 报的 tests=1123 —— 差 4 是截图测试，
#    verify.yml 按文件名把 screenshot/ 那一类单独算成 screenshots=4。
#    **别把 1127 当功能测试数**，对不上会以为丢测试了。
grep -rc '@Test' --include=*.kt app/src/test core/*/src/test | awk -F: '{s+=$2} END {print "src @Test (incl. screenshots):", s}'
ls app/src/test/screenshots/ | wc -l

# 2. 本机能不能编译（临时改 buildToolsVersion，验完还原）
#    见 BUILD-AND-CI.md §0.0b

# 3. 每轮 CI 之后必须看的两个数字（分开报，不要合并）
#    tests=1123（或更多）  screenshots=N
```

**每一步的 PR 描述里必须写**：这一屏**故意**改成什么样，以及基线 diff 里哪几处是预期的、哪几处不是。

> 没有这句话的换皮 PR 不可 review ——「红色变成了另一种红色」在 diff 图里看不出来是有意还是手滑。
