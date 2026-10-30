# 设计规范 v2（基于实测设计刻度 + 许可安全重写）

> 依据：`research/05-ksu-kit.md`（对 `/tmp/ksu-kit @8cc556d` 的实读）+ `research/06-bqv-ui.md`（对 fork 的实读）。本文替代 DESIGN.md 中凭印象的部分，给出可直接落到 Compose 的**刻度表与行为规格**。

## 1. 关键修正：参考项目其实没有令牌层

它 0 个自定义 Typography / Shapes；颜色全来自 material-kolor 生成的 ColorScheme 与 MiuixTheme.colorScheme；间距与字号是**散落字面量**（155 处）。
这反而是好事：**我们自建 `KsuTokens.kt`，把散落刻度收敛成一套原创令牌**——比抄它更干净，也彻底避开 GPL 污染。

### 1.1 实测刻度（直接采用）

| 类别 | 值 | 出处 |
|---|---|---|
| 间距档 | 4 / 6 / 8 / 12 / 16 / 20 / 24 dp | 155 处字面量统计 |
| 列表行高 | 56 dp（触控目标） | miuix 组件 |
| 字号 | 11 / 15 / 17 / 22 / 35 sp | 底栏标签 11sp/行高 14sp、搜索框 17sp Medium、标题 22sp、大标题 35sp |
| 底栏 | 高 56–64 dp，胶囊圆角 = 高/2 | FloatingBottomBar.kt |
| 卡片圆角 | 12 / 16 / 20 dp 三档（Sheet 28dp） | 分段列表 + 卡片 |
| 阴影/模糊 | 底栏 blur 4dp + 高光描边 alpha 0.75（暗色）/ 阴影 alpha 0.1–0.2 | FloatingBottomBar.kt:307-333 |

## 2. `KsuTokens`（新建，原创）

```kotlin
object KsuTokens {
    object Space  { val xs = 4.dp;  val s = 8.dp;  val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 24.dp }
    object Radius { val card = 16.dp; val cardLarge = 20.dp; val sheet = 28.dp; val pill = 100.dp }
    object Size   { val rowHeight = 56.dp; val barHeight = 60.dp; val icon = 24.dp; val touchTarget = 48.dp }
    object Type {
        val caption   = TextStyle(fontSize = 11.sp, lineHeight = 14.sp)
        val body      = TextStyle(fontSize = 15.sp, lineHeight = 24.sp)
        val bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
        val title     = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
        val display   = TextStyle(fontSize = 35.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
    }
    object Motion {
        val pageSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 400f)
        val dialogIn   = tween<Float>(180, easing = FastOutSlowInEasing)
        val streamFade = tween<Float>(120)
    }
}
```

## 3. 底栏行为规格（重写依据，不抄代码）

| 行为 | 规格 |
|---|---|
| 选中指示器 | 指示器 x 位置对目标索引做弹簧动画（pageSpring）；切页用 pager spring 滚动 |
| 三层背景 | ① 内容模糊层（blur 4dp）② 半透明描边（白/黑 alpha 0.12）③ 高光条纹随设备倾斜（无传感器时退化为静态高光） |
| 未选中/选中 | 未选中仅图标 alpha 0.6；选中为图标 + 文字（11sp） |
| 滚动隐藏 | 向下滚动 `translateY(+barHeight)` + fade 隐藏；向上滚动或到顶显示 |
| 键盘 | 输入时底栏上移，`imePadding()` 与底栏高度联动，不遮挡输入框 |
| 无障碍 | 每项 `Role.Tab` + contentDescription，选中态 `selected = true` |

> **必须提供降级路径**：API 31+ 用 `Modifier.blur()` / RenderEffect；API < 31 退回纯色 + 描边，否则 Android 8–11 会出问题。

## 4. 外观档位（7 档）落地

| 档位 | 实现 | 与 fork 的对接 |
|---|---|---|
| 跟随系统 | `isSystemInDarkTheme()` | 已有 `themeMode` |
| 亮 / 暗 | 固定 ColorScheme | 已有 |
| AMOLED | 背景 #000000、卡片 #0A0A0A、描边 #1A1A1A | 新增 |
| Monet 跟随 / 亮 / 暗 | `dynamicColorScheme`（API 31+），低版本回退到亮/暗 | 新增，需 `Build.VERSION` 判断 |
| 主色选择 | 15 色 seed + material-kolor 生成 | 新增依赖 `material-kolor 5.0.1` |

**与 `DshColors` 的关系**：`DshColors` 是语义层（工具状态、审批红、连接绿等业务色），`KsuTheme` 是外观层（surface / container / outline）。渲染组件继续读 `DshTheme.colors`（244 处调用点**一处不改**），外壳读 `KsuTheme`，两者在 `AppTheme` 合并。

## 5. 许可安全的资产清单

| 想要的东西 | 参考项目位置 | 许可 | 我们怎么做 |
|---|---|---|---|
| 液态玻璃底栏 | `FloatingBottomBar.kt` | 文件头注明源自 compose-miuix-ui 示例（Apache-2.0），但文件本身在 GPL 仓库 | **从 compose-miuix-ui 官方示例取**（IosLiquidGlassNavigationBar），或按 §3 规格自写 |
| lens / innerShadow | `component/liquid/Lens.kt`(216)、`InnerShadow.kt`(160) | 注释称源自 `Kyant0/AndroidLiquidGlass`（Apache-2.0） | 直接参考上游 Apache 原件 |
| OS3 流体背景 | `component/miuix/effect/*` | Apache-2.0 上游 | 可选，后置 |
| Markdown CSS/JS | `assets/webview/*` | 仓库内原创、无许可声明，需溯源 Primer/Thunderbird | **不用**（fork 已有原生 Compose 渲染，更好） |
| 分段列表 / 设置项 | `SegmentedList.kt`(493)、`SettingsItem.kt`(57) | GPL | 按行为规格自写（约 200 行） |
| 搜索栏 | `SuperSearchBar.kt`(338) | GPL | 自写（展开动画 + 结果态） |

## 6. 与 DESIGN.md 的差异（以本文为准）

| 项 | DESIGN.md v1 | 本文 v2 |
|---|---|---|
| 圆角 | 16–20dp 泛泛 | **12 / 16 / 20 / 28 四档明确** |
| 字号 | 15–16 / 13sp 泛泛 | **11 / 15 / 17 / 22 / 35 实测档** |
| 底栏 | 液态玻璃一句话 | **三层结构 + 滚动隐藏 + 降级路径** |
| 令牌 | 未提 | **新建 `KsuTokens.kt`**（原创，弥补参考项目缺失） |
| WebView Markdown | 列为可选 | **明确不用** |

## 7. 换皮的真实工作量（来自 fork 实读，非估计）

`research/06-bqv-ui.md` 给了几个必须先知道的数据：

| 事实 | 数据 | 对换皮的含义 |
|---|---|---|
| 裸尺寸 | main 里 **542 处 `N.dp`**，`ChatScreen.kt` 57 处、`Composer.kt` 38 处 | 改圆角/间距必须逐个文件改，**不能靠换主题一次性生效** |
| 圆角直调 | **164 处 `RoundedCornerShape(...)`** | 其中大部分应改为 `KsuTokens.Radius` |
| 颜色硬编码 | `Color(0x…)` 共 101 处，**100 处在 `DshTheme.kt`** | 颜色反而干净：只需改令牌文件 |
| Material 容器 | 全仓 **0 处** `Scaffold` / `TopAppBar` / `Card` / `Button` / `OutlinedTextField` | 容器全是自绘 `Box + clip + border` → **换皮不涉及覆盖 Material 内部样式**，是好事 |
| 导航 | 声明了 `navigation-compose` 但**零 import**；实际是 `AppPhase` 枚举 + 抽屉 + 11 个 Sheet + `ViewTabs` | 加底栏 pager 是新增，不是替换，风险低 |
| 状态 | `DshViewModel.kt` **9055 行** / 19 个 StateFlow / 87 字段 / 267 个函数，零 Compose import | 换皮完全不碰它（这是护栏） |
| 改名 | 267 个 `.kt` 的 package 行 + 1053 个 import + 3 处 XML/工作流；**唯一 IDE 重构改不到的字符串**是 `Attention.kt:42` 的 `EXTRA_SESSION = "uk.xa0.dsh.session"` | 改名清单必须含这一项，否则通知点击失效 |
| 首帧闪白 | `res/values/colors.xml` 的 `dsh_window_bg = #0B0F14` 与 Compose 的 `bgBase = #151517` **不一致** | 换肤/暗色切换会闪；换皮时必须同步窗口底色 |

### 修正后的换皮顺序（替代 DESIGN.md 的 D1–D7）

| 步 | 内容 | 触碰 |
|---|---|---|
| **D0** | `KsuTokens` 落地 + `colors.xml` 窗口底色同步 | 新文件 + `res/values/colors.xml` |
| **D1** | `DshTheme.kt` 换血：保留语义字段、注入外观层 | 单文件 360 行 |
| **D2** | `ChatScreen.kt` 外框 + 浮动底栏 + pager | 大文件，先补截图测试 |
| **D3** | 抽屉 / 搜索栏 | `SessionsDrawer.kt` 2283 行 |
| **D4** | 设置页行件 | `SettingsContent.kt` 1136 行 |
| **D5** | Sheet 统一（11 个） | `Sheets.kt` 968 + `AutomationSheet.kt` 899 |
| **D6** | 聊天行**仅外壳** | `ChatRows.kt` 1852 行 |
| **D7** | 动效与触觉 | 各处 |

**明确保留不动**：`ui/markdown/**`（原生 Compose 渲染）、`scroll/TailFollow`、`model/`、`net/`、`term/`、`TerminalScreen` 的 Canvas 绘制、`TerminalInputView` 的 IME 处理。

## 8. KernelSU 覆盖不到的地方 → 用 Miuix（Apache-2.0）

我们的策略是**两段式**：

| 层 | 来源 | 说明 |
|---|---|---|
| 观感与壳（KernelSU 风格独有） | 自写（原创） | 浮动底栏、7 档外观、卡片式设置、`KsuTokens` |
| **其余一切通用控件** | **`compose-miuix-ui/miuix`（Apache-2.0）** | 下拉、开关、滑条、输入框、对话框、底部弹层、侧栏、刷新、进度、图标 |

### 8.1 Miuix 能直接替我们做的（`research/07-miuix-api.md` 实测）

| 我们要的 | Miuix 组件 | 模块 |
|---|---|---|
| 悬浮底栏 | `FloatingNavigationBar` / `FloatingNavigationBarItem` | miuix-ui |
| 搜索栏 | `SearchBar` + `InputField` | miuix-ui |
| 对话框 | `OverlayDialog` | miuix-ui |
| 底部弹层 | `OverlayBottomSheet` | miuix-ui |
| 开关 / 滑条 | `Switch`、`Slider`、`RangeSlider`、`VerticalSlider` | miuix-ui |
| 输入框 | `TextField`（3 个重载，新版 `TextFieldState`） | miuix-ui |
| 宽屏侧栏 | `NavigationRail` + `rememberNavigationRailState` | miuix-ui |
| 下拉刷新 | `PullToRefresh` + `rememberPullToRefreshState`（需中文化 4 条文案） | miuix-ui |
| 进度 | `Linear/Circular/InfiniteProgressIndicator` | miuix-ui |
| Snackbar | `SnackbarHost` + `showSnackbar` | miuix-ui |
| 标签行 | `TabRow` / `TabRowWithContour` | miuix-ui |
| 设置行 | `ArrowPreference`、`SwitchPreference`、`CheckboxPreference`、`RadioButtonPreference`、`SliderPreference`、`OverlayDropdownPreference`、`OverlaySpinnerPreference` | miuix-preference |
| 图标 | `MiuixIcons.*`（156 个扩展 + 7 个基础，普通 `ImageVector`，**可与 Material 图标混用**） | miuix-icons |
| 超椭圆圆角 | `squircleSurface` / `squircleClip` / `squircleBorder` / `Path.addSquircleRect` | miuix-squircle |

### 8.2 Miuix **没有**、必须自写或留 Material3 的（共 5 处）

| 缺口 | 事实 | 我们的做法 |
|---|---|---|
| **Toast** | 全库 grep `Toast` = 0 | 复用 fork 已有的 toast host，或用 `FloatingToolbar` 包一层 |
| **骨架屏 / Shimmer** | grep `Skeleton` / `Shimmer` = 0 | 自写（约 60 行，用 `Brush.linearGradient` 位移） |
| **分段控件（带任意 content）** | `TabRow` 入参是 `List<String>`，**没有 content slot** | 自写 `SegmentedColumn`（约 200 行） |
| **Markdown 渲染** | 库里没有 | **继续用 fork 的原生 Compose 渲染**（`MarkdownView.kt`） |
| **Chip / Avatar / FlowRow / 日期选择** | 全部 0 命中 | Chip 用 `Surface`+`Row` 自写；头像用 squircle 近似圆形 |

### 8.3 主题对接（关键：不要两套色）

- Miuix 有 `MiuixTheme(colors = …)` 与 `MiuixTheme(controller = …)` 两个重载，`Colors` 有 **53 个角色**，并提供 53 个命名参数的 `lightColorScheme(...)` / `darkColorScheme(...)`。
- **没有公开的 M3 → Miuix 适配器**（`colorsFromSeed`、`mapMd3RolesToMiuixColorsCommon` 都是 `internal`）。
- 所以：**我们自己写映射**，在 `AppTheme` 里同时构造 `DshColors`（语义层，244 处调用点不动）与 `MiuixTheme.Colors`（外观层），由同一份 `KsuTokens` 驱动。映射对照表见 `research/07-miuix-api.md` §8（M3 角色 → Miuix 角色）。

```kotlin
// 示意：一份外观设置驱两套主题
@Composable
fun AppTheme(mode: ColorMode, content: @Composable () -> Unit) {
    val ksu = KsuTokens.of(mode)              // 自建：surface/container/outline/radius/type
    val miuix = ksu.toMiuixColors()          // 自写映射：53 个角色
    val dsh = ksu.toDshColors()              // 语义层：accent/success/warn/error/label*
    MiuixTheme(colors = miuix) {
        DshTheme(colors = dsh) { content() }
    }
}
```

### 8.4 硬约束（决定升级路线）

| 约束 | 值 | 影响 |
|---|---|---|
| Kotlin | **2.4.x**（0.9.0 起就要 2.3+） | fork 的 1.9.24 **必升**；Kotlin 1.9 读不了它的 metadata |
| Compose | 0.9.2 → CMP 1.11.1（androidx compose 1.11.x）；0.9.4 → CMP 1.12.0 | 我们的 BOM 2024.09 必须升 |
| AGP | 0.9.2 → 8.6+；0.9.4 → **9.1+** | **选 0.9.4**（用户已定全量最新）：AGP 9.5 + Gradle 9.8 |
| compileSdk | 0.9.2 → 35；0.9.4 → **37** | 直接到 37，配合 AGP 9 |
| `miuix-blur` minSdk | **33** | 我们 26 → **不引 blur**，玻璃效果自写 + API<31 降级 |
| 无 `androidx.compose.material3` 依赖 | 只依赖 `material3-window-size-class` | **可与现有 M3 同栈共存**，可渐进替换 |

**结论：用 miuix 0.9.4 + 全量升级。** 目标组合 Gradle 9.8.2 / AGP 9.5.0 / Kotlin 2.4.21 / Compose BOM 2026.09.00 / compileSdk 37（见 `research/09-toolchain-latest.md`）。0.9.2 降为**备选路线**：若全量升级三天内收敛不了，退回 AGP 8.6 + compileSdk 35 + miuix 0.9.2（不换 Gradle/buildTools）。
