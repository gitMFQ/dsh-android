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
