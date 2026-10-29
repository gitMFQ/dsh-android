# 05 · KernelSU Style UI Kit 设计系统盘点（Kotlin + Compose，GPL-3.0）

调研对象：`/tmp/ksu-kit`（github.com/chenaizhang/KernelSU-Style-UI-Kit），只读，HEAD `8cc556d` "chore(release): prepare version 0.1.1"。规模实测：105 个 `.kt`、12691 行；其中 `ui/component/` 48 个文件 6113 行，`app/src/main/assets/webview/` 10 个文件 2038 行。
路径前缀：`KT:` = `app/src/main/java/com/example/kernelsustyleuikit/`，`AS:` = `app/src/main/assets/webview/`，`GD:` = `gradle/libs.versions.toml`。所有结论均来自实读源码，非 README 宣传。

## 0. TL;DR

| 问题 | 结论 |
|---|---|
| "KernelSU style" 是什么 | **不是一套自研令牌**，而是"MIUI/HyperOS 观感 + GitHub Primer 排版的 Miuix 组件库皮肤"，外加可切到 Material3 Expressive 的第二套皮 |
| 令牌层存在吗 | **几乎不存在**：无自定义 `Typography(...)`，无 `Shapes(...)`，颜色全部来自 `material-kolor` 生成的 ColorScheme 与 `MiuixTheme.colorScheme`；间距/字号是散落字面量（12/16/8/24 dp 占 155 处） |
| Material3 vs Miuix | **两套同时编译进 APK，靠 `UiMode` 枚举 + `LocalUiMode` CompositionLocal 在每个门面 `when` 二选一**（KT:ui/UiMode.kt:5-19）；Miuix 为默认（`DEFAULT_VALUE = Miuix`） |
| 切换粒度 | 逐屏/逐组件选择，不是换主题对象；`BottomBar`/`Dialog`/`MarkdownContent`/每个 Screen 都有 `when (LocalUiMode.current)` 分支 |
| 真正的差异化资产 | ①毛玻璃浮动底栏（liquid glass + 倾角高光）②Miuix 分段列表封装 ③JS 桥 WebView Markdown 管线 ④OS3 动态背景 AGSL 着色器 |
| 复用风险 | 主体 GPL-3.0（传染整个 APK）；但 `FloatingBottomBar`/`liquid/*`/`miuix/effect/*` 头部注明源自 Apache-2.0 上游示例 |
| 对 dsh-android（MPL-2.0） | **不可复制 Kotlin**；可复制的是"观感"与上游 Apache-2.0 原件；Markdown CSS/JS 需回溯 Primer/Thunderbird 许可证 |

## 1. 设计令牌

### 1.1 颜色角色

无 `Color.kt` 式令牌表。颜色有两个来源，且**语义名直接借用两端库**：

| 来源 | 角色集合 | 代码 |
|---|---|---|
| Material 路径 | `MaterialTheme.colorScheme`（M3 全套 role：primary/onSurface/surfaceContainer{Lowest..Highest}/outline/errorContainer 等） | KT:ui/theme/MaterialTheme.kt:29-52 |
| Miuix 路径 | `MiuixTheme.colorScheme`（surface/onSurface/onBackground/surfaceContainer/surfaceContainerHigh/onSurfaceVariantSummary/onSurfaceVariantActions/disabledOnSecondaryVariant 等） | KT:ui/theme/MiuixTheme.kt:57-73 |
| 第三处 | `ui/util/Colors.kt` 的**算法色**：`cssColorFromArgb` / `mixArgb` / `relativeLuminance` / `contrastRatio` / `argbToHsl` / `adjustLightnessArgb` / `ensureVisibleByMix`（12 步二分逼近最小对比度） | KT:ui/util/Colors.kt:5-110 |

- 种子色板 = 15 个写死 ARGB（红/粉/紫/深紫/靛/蓝/青/青绿/绿/黄/琥珀/橙/棕/蓝灰/樱）；索引 0 语义特殊：`keyColor == 0` 表示"跟随系统动态取色"。见 KT:ui/theme/Colors.kt:6-22 与 KT:ui/theme/MaterialTheme.kt:25（`dynamicColor = appSettings.keyColor == 0`）。
- 局部硬编码语义色（Miuix 路径为贴近 KernelSU 观感而不走主题）：权限卡 `#DFFAE4`/`#F8E2E2`/`#36D167`/`#F72727`/`#111111`（KT:ui/screen/home/HomeMiuix.kt:123-125）、警告卡 `#310808`/`#F8E2E2`/`#F72727`（KT:ui/component/miuix/WarningCard.kt:36-37,52）。**这是"KernelSU style"最具体的视觉指纹之一**。

### 1.2 明暗 / 动态 / AMOLED

| 维度 | 实现 |
|---|---|
| 模式枚举 | `ColorMode` 7 值：SYSTEM(0)/LIGHT(1)/DARK(2)/MONET_SYSTEM(3)/MONET_LIGHT(4)/MONET_DARK(5)/DARK_AMOLED(6)（KT:ui/theme/Theme.kt:14-45），提供 isSystem/isDark/isAmoled/isMonet 与 toMonetMode/toNonMonetMode 双向映射 |
| 动态取色 | `material-kolor 5.0.1`：`rememberDynamicColorScheme(seedColor, isDark, isAmoled, style: PaletteStyle, specVersion)`（KT:ui/theme/MaterialTheme.kt:31-52）；动态分支还能喂 6 个系统色作 primary/secondary/tertiary/neutral/neutralVariant/error |
| 调色风格 / 规范版本 | `PaletteStyle`（TonalSpot 默认）与 `ColorSpec.SpecVersion`（Default / SPEC_2021 / SPEC_2025），持久化为字符串 color_style/color_spec（KT:ui/theme/Theme.kt:74-85、KT:data/repository/SettingsRepositoryImpl.kt:36-42） |
| Miuix 侧对齐 | `ThemePaletteStyle.valueOf(colorStyle.name)` + `ThemeColorSpec.Spec2025|Spec2021`（KT:ui/theme/MiuixTheme.kt:30-40）；`ColorSchemeMode` 由 ColorMode 逐一映射（同文件 :43-50） |
| Miuix 的 Monet 开关折算 | 切到 Miuix 时若 miuix_monet=false 而 ColorMode 属 Monet 系则自动降级为非 Monet，反之升级（KT:ui/theme/Theme.kt:60-70） |
| 系统栏 | `enableEdgeToEdge(transparent, transparent, darkMode)` + `isNavigationBarContrastEnforced=false`（KT:ui/MainActivity.kt:99-112）；两套主题再各写 `isAppearanceLightStatusBars`（KT:ui/theme/MaterialTheme.kt:54-60、KT:ui/theme/MiuixTheme.kt:60-66） |
| 暗色判定 | 单一 `isInDarkTheme()`，读 LocalColorMode 整数 1/4=强制亮、2/5/6=强制暗、否则 isSystemInDarkTheme()（KT:ui/theme/Theme.kt:113-121） |

### 1.3 排版

| 事实 | 证据 |
|---|---|
| **无自定义 Typography**：全仓 `Typography(` 出现 0 次 | grep 实测 |
| Material 路径走 `MaterialExpressiveTheme(colorScheme, motionScheme = MotionScheme.expressive())` | KT:ui/theme/MaterialTheme.kt:62-66 |
| Miuix 路径走 `MiuixTheme.textStyles.{headline1, body1, body2, title4}` | KT:ui/component/miuix/EditText.kt:76,82；KT:ui/component/miuix/DropdownItem.kt:44；KT:ui/component/miuix/SendLogDialog.kt:78 |
| 裸字号（KernelSU 味来源） | 底栏标签 11sp/行高 14sp（KT:ui/component/bottombar/BottomBarMiuix.kt:104-105）；搜索框 17sp Medium（KT:ui/component/miuix/SuperSearchBar.kt:249-251）；权限卡 22sp SemiBold + 版本 15sp（KT:ui/screen/home/HomeMiuix.kt:168-176）；关于页 App 名 35sp Bold（KT:ui/screen/about/AboutMiuix.kt:346）；警告 14sp（KT:ui/component/miuix/WarningCard.kt:53） |
| WebView 文本缩放 | `textZoom = 90 * (density/systemDensity) * fontScale`（KT:ui/component/markdown/GithubMarkdown.kt:82-86），与页面缩放设置双通道 |

反证"没有字号体系"的代价：改主题色不改字号；`MiuixTheme.textStyles.*` 只被组件内部借用，屏幕层大量直接写 sp。

### 1.4 圆角 / 阴影 / 高程

| 令牌 | 值 | 位置 |
|---|---|---|
| 胶囊 | `CircleShape`（底栏容器、指示器、搜索框背景） | KT:ui/component/FloatingBottomBar.kt:188,325；KT:ui/component/miuix/SuperSearchBar.kt:260 |
| Material 大卡 | `MaterialTheme.shapes.large`（默认 M3 值，未覆盖） | KT:ui/component/material/TonalCard.kt:17；KT:ui/component/material/SegmentedList.kt:77 |
| 分段列表形状 | `ListItemDefaults.segmentedShapes(index, count)`，单行时强制 shapes.large | KT:ui/component/material/SegmentedList.kt:74-81 |
| 固定 16 / 20 dp | About 图标底板 16dp；主题预览机框 20dp；预览列表 14dp；色卡 12dp | KT:ui/screen/about/AboutMaterial.kt:79；KT:ui/screen/colorpalette/ColorPaletteScreenMaterial.kt:424；KT:ui/screen/colorpalette/ColorPaletteScreenMiuix.kt:594；KT:ui/screen/colorpalette/ColorPaletteScreenMaterial.kt:542 |
| 小圆角 | 8dp 加载对话框；6/5/3/2dp 微量 | KT:ui/component/dialog/DialogMaterial.kt:29；KT:ui/screen/colorpalette/ColorPaletteScreenMiuix.kt:657,639,608 |
| 投影 | `dropShadow(radius = 10.dp, black, alpha = 暗 0.2 / 亮 0.1)` | KT:ui/component/FloatingBottomBar.kt:307-314 |
| 内阴影 | `InnerShadow(radius = 8.dp * pressProgress, black .15)` | KT:ui/component/FloatingBottomBar.kt:429-435 |
| 高程语义 | 仅 `surfaceColorAtElevation(1.dp)` 当"卡片底" | KT:ui/component/material/SegmentedList.kt:68；KT:ui/component/material/TonalCard.kt:16 |
| 深度替代方案 | **模糊而非高度**：`textureBlur(blurRadius = 25f)` 顶栏、60f 卡片、150f Logo；前置条件 `isRenderEffectSupported()` | KT:ui/util/BlurExt.kt:15-42；KT:ui/screen/about/AboutMiuix.kt:301-310,416-422 |

### 1.5 间距

无间距常量、无 LocalSpacing。全域 dp 字面量频次（ui/ 目录 grep）：12 出现 42 次、16 四十次、8 三十次、6 二十三次、24 二十次、4 十五次、20 十三次 → 事实刻度就是 **4/6/8/12/16/20/24**，且 12 与 16 分工明确：

| 场景 | Miuix | Material |
|---|---|---|
| 列表左右内距 | 12dp（KT:ui/screen/home/HomeMiuix.kt:93、KT:ui/screen/settings/SettingsMiuix.kt:89、KT:ui/screen/colorpalette/ColorPaletteScreenMiuix.kt:147） | 16dp（KT:ui/screen/home/HomeMaterial.kt:69、KT:ui/screen/settings/SettingsMaterial.kt:78） |
| 卡片内距 | 16dp（KT:ui/screen/home/HomeMiuix.kt:260） | 16dp / 24dp（KT:ui/screen/home/HomeMaterial.kt:151） |
| 卡间距 | 12dp（反复出现的 padding(top = 12.dp)） | `Arrangement.spacedBy(16.dp)`（KT:ui/screen/home/HomeMaterial.kt:70） |
| 行最小高 | 56dp（KT:ui/component/miuix/EditText.kt:68、KT:ui/component/material/SegmentedList.kt:64） | 同左 |
| 底栏 | 浮动 64dp 高 / 内容 56dp / 距导航栏 12dp（KT:ui/component/FloatingBottomBar.kt:347,379；KT:ui/component/bottombar/BottomBarMiuix.kt:83） | `FlexibleBottomAppBar` 默认 |

### 1.6 动效

| 类型 | 参数 | 位置 |
|---|---|---|
| Material 全局 | `MotionScheme.expressive()` | KT:ui/theme/MaterialTheme.kt:64 |
| 底栏拖拽 | 5 条弹簧：value spring(1f,1000f,vt)、velocity spring(0.5f,300f,vt*10)、press spring(1f,1000f,0.001f)、scaleX spring(0.6f,250f)、scaleY spring(0.7f,250f) | KT:ui/component/miuix/animation/DampedDragAnimation.kt:33-42 |
| 拖拽回弹 | 橡皮筋 `4dp * sign * EaseOut(fraction)` | KT:ui/component/FloatingBottomBar.kt:201-212 |
| 按压缩放 | 指示器 78f/56f ≈ 1.39x；标签 `lerp(1f, 1.2f, progress)` | KT:ui/component/FloatingBottomBar.kt:229,356 |
| 指示器滑入 | spring(1f, 300f, 0.5f) | KT:ui/component/FloatingBottomBar.kt:250 |
| 搜索展开 | tween(300, LinearOutSlowInEasing) + 背景 alpha tween(200, FastOutSlowIn) | KT:ui/component/miuix/SuperSearchBar.kt:119-134 |
| 内容淡入 | tween(300) alpha，占位 tween(150) | KT:ui/component/markdown/MarkdownContent.kt:33-42 |
| 触觉 | `HapticFeedbackType.VirtualKey` 于开关/主题切换；列表 `scrollEndHaptic()` | KT:ui/component/material/SegmentedList.kt:241,287；KT:ui/screen/colorpalette/ColorPaletteScreenMaterial.kt:217 |

## 2. Material3 vs Miuix：怎么切

`KT:ui/UiMode.kt:5-19`

`enum class UiMode(val value: String) { Miuix("miuix"), Material("material") }`，伴生对象里 `fromValue` 非 material 一律回落 Miuix，`DEFAULT_VALUE = Miuix.value`；同文件定义 `val LocalUiMode = staticCompositionLocalOf { UiMode.Miuix }`。

| 环节 | 机制 |
|---|---|
| 决策点 | 唯一：MainActivity 从 ViewModel 读 `uiState.uiMode` → `CompositionLocalProvider(LocalUiMode provides uiMode)`（KT:ui/MainActivity.kt:127） |
| 主题 | `TemplateTheme` 内 when(uiMode) → MiuixTemplateTheme / MaterialTemplateTheme（KT:ui/theme/Theme.kt:100-110） |
| 根 Scaffold | when(uiMode) 选 `androidx.compose.material3.Scaffold` 或 `top.yukonga.miuix.kmp.basic.Scaffold`（KT:ui/MainActivity.kt:164-167、264-290、304-312） |
| 底栏 | `BottomBar()` → BottomBarMiuix(blurBackdrop, backdrop, modifier) / BottomBarMaterial()（KT:ui/component/bottombar/BottomBar.kt:98-101）；侧栏同理 :108-111 |
| 对话框 | rememberLoadingDialog() / rememberConfirmDialog() 各自 when 选 *Miuix 或 *Material 实现（KT:ui/component/dialog/Dialog.kt:303-306,330-344） |
| 屏幕 | 每个屏三件套：XxxScreen.kt（取数据/建 actions）+ XxxMiuix.kt + XxxMaterial.kt |
| 加载指示器 | Material `LoadingIndicator()` vs Miuix `InfiniteProgressIndicator()`（KT:ui/component/markdown/MarkdownContent.kt:72-75） |
| 切换生效 | 设置项 ui_mode 写 SharedPreferences，MainActivityViewModel 监听 11 个 key 并重建 uiState（KT:ui/viewmodel/MainActivityViewModel.kt:23-27,59-71） |

依赖并列（GD 全文）：compose-bom 2026.09.00、material3 1.5.0-alpha28（含 Expressive API，靠 kotlin 块 freeCompilerArgs 全局 opt-in）、material-kolor 5.0.1；Miuix 0.9.4 拆 5 个 artifact：miuix-ui、miuix-icons、miuix-nav、miuix-preference、miuix-blur。**两条路径共享同一份状态层**（SettingsRepository / ViewModel），渲染层各自独立。

## 3. 组件清单（`ui/component/`，48 文件 / 6113 行）

### 3.1 根目录（6 文件 / 748 行）

| 文件 (LOC) | 用途 | 公开 API |
|---|---|---|
| FloatingBottomBar.kt (456) | 毛玻璃胶囊浮动底栏。三层 Row：底层 backdrop 玻璃 + 中层"取色采样层"（alpha=0 但 layerBackdrop，用于把图标 tint 成彩色）+ 上层滑块指示器；含重力倾角高光（rememberDeviceTilt 旋转主光源，约 6 度阈值） | `FloatingBottomBar(modifier, selectedIndex, onSelected, backdrop, tabsCount, isBlurEnabled, content)` :178；`RowScope.FloatingBottomBarItem` :149；`LocalFloatingBottomBarTabScale` :84 |
| ScrollToTop.kt (90) | "条件变化才回顶"：SideEffect 比对 keys → requestScrollToItem(0)；再逐帧钉住直到列表静默 250ms 且 isBusy()==false（上限 3s） | `ScrollToTopOnChange(listState, vararg keys, onScrolledToTop, isBusy, observedList)` :34；`suspend scrollToTopAfterListSettles(...)` :67。**全仓 0 处调用**（示例遗留） |
| SearchStatus.kt (58) | 搜索四态机 EXPANDED/EXPANDING/COLLAPSED/COLLAPSING + 结果态 DEFAULT/EMPTY/LOAD/SHOW，带 offsetY 供顶栏联动；onAnimationComplete() 收敛中间态 | `@Stable data class SearchStatus` :15；`SearchStatus.TopAppBarAnim` :37 |
| MenuPositionProvider.kt (89) | Miuix PopupPositionProvider 实现：4 种对齐 + 兜底"下方/上方/居中"，结果夹紧在窗口内；RTL 镜像 Start/End | `object ListPopupDefaults { val MenuPositionProvider }` :11-76。**0 处引用** |
| KeyEventBlocker.kt (28) | 用 focusable()+onKeyEvent 抢焦点吞掉按键（防手柄/实体键穿透），LaunchedEffect 自动 requestFocus | `KeyEventBlocker(predicate: (KeyEvent) -> Boolean)` :15。**0 处引用** |
| ObserveAsEvents.kt (27) | repeatOnLifecycle(STARTED) 的 one-shot 事件收集器 | `ObserveAsEvents(events, minActiveState, onEvent)` :16 |

### 3.2 bottombar/（5 文件 / 469 行）

| 文件 (LOC) | 用途 | 公开 API |
|---|---|---|
| BottomBar.kt (112) | 模式分派 + 导航状态机 MainPagerState（selectedPage/isNavigating，用 Job 防连点竞态，springAnimateToPage 或 scrollToPage）；useNavigationRail 阈值判定 | `MainPagerState` :24；`rememberMainPagerState` :72；`useNavigationRail` :83；`BottomBar` :93；`SideRail` :105 |
| BottomBarMaterial.kt (63) | M3 FlexibleBottomAppBar + NavigationBarItem，图标 Filled/Outlined 双态 | `BottomBarMaterial()` :25 |
| BottomBarMiuix.kt (123) | 两态：不浮动时 BlurredBar { NavigationBar }；浮动时 FloatingBottomBar + 12dp 底距，图标 24dp 文字 11sp | `BottomBarMiuix(blurBackdrop, backdrop, modifier)` :40；`enum BottomBarDestination`（Home/Setting 的 StringRes+ImageVector） :117 |
| NavigationRailMaterial.kt (115) | M3 WideNavigationRail，展开/收起状态持久化到 SettingsRepositoryImpl().navigationRailExpanded，头部 Menu/MenuOpen 切换 | `NavigationRailMaterial(modifier)` :41 |
| NavigationRailMiuix.kt (56) | Miuix NavigationRail + rememberNavigationRailState，状态同样回落 prefs | `NavigationRailMiuix(modifier)` :18 |

判定线：`widthDp >= 840 || (widthDp >= 600 && height/width < 1.2)`；**特例**：Miuix + 开启浮动底栏时强制不用侧栏（KT:ui/component/bottombar/BottomBar.kt:83-90）。

### 3.3 dialog/（3 文件 / 579 行）

| 文件 (LOC) | 用途 | 公开 API |
|---|---|---|
| Dialog.kt (366) | 手柄式对话框框架：DialogHandle(:54)/LoadingDialogHandle(:61)/ConfirmDialogHandle(:71)；awaitConfirm 用 suspendCancellableCoroutine + Channel<ConfirmResult> 桥接按钮点击，Saver 支持进程重建；show() 对空 visuals 抛异常 | `rememberLoadingDialog()` :299；`rememberConfirmCallback` :350；`rememberConfirmDialog(onConfirm,onDismiss)` :359 与 (callback) :364；`withLoading { }` :121 |
| DialogMaterial.kt (86) | AlertDialog（markdown/html/纯文本三态 body）+ 100dp LoadingIndicator 方块 | `LoadingDialogMaterial(showDialog)` :22；`ConfirmDialogMaterial(visuals,confirm,dismiss,showDialog)` :42 |
| DialogMiuix.kt (127) | WindowDialog；正文 + 双 TextButton 用自写 Layout（有正文时按剩余高度测量，无正文时只放按钮） | `LoadingDialogMiuix` :34；`ConfirmDialogMiuix` :61 |

### 3.4 liquid/（4 文件 / 430 行，全部注明 Apache-2.0 上游）

| 文件 (LOC) | 用途 | 公开 API |
|---|---|---|
| Lens.kt (216) | AGSL 折射着色器：圆角矩形 SDF + circleMap 位移；含"色散"变体（7 次采样 R/O/Y/G/C/B/P 模拟虹边） | `BackdropEffectScope.lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)` :13 |
| InnerShadow.kt (160) | Modifier.innerShadow：GraphicsLayer(Offscreen) 内录 BlendMode.Clear 遮罩再模糊 8dp 叠加 | `data class InnerShadow` :33；`Modifier.innerShadow(shape, shadow)` :46 |
| CombinedBackdrop.kt (39) | 把两个 Backdrop 合成一个（底栏要和"标签采样层"一起采样） | `CombinedBackdrop` :16；`rememberCombinedBackdrop` :38 |
| Vibrancy.kt (15) | `colorControls(brightness = 0, contrast = 1, saturation = 1.5f)` 提饱和度 | `BackdropEffectScope.vibrancy()` :9 |

### 3.5 material/（9 文件 / 1196 行）

| 文件 (LOC) | 用途 | 公开 API |
|---|---|---|
| SegmentedList.kt (493) | **M3 分段列表全家桶**：SegmentedColumn（标题+2dp 间隔+自动首/中/尾形状）、开关/下拉/单选/复选/文本行；LocalListItemShapes 用 CompositionLocal 传形状 | SegmentedColumn :84；SegmentedItem :117；SegmentedListItem x3 重载 :130,161,194；SegmentedSwitchItem :227；SegmentedDropdownItem :262；SegmentedRadioItem :338；SegmentedCheckboxItem :369；SegmentedTextField :404；LocalListItemShapes :61 |
| SearchBar.kt (239) | LargeFlexibleTopAppBar + M3 SearchBar + ExpandedFullScreenContainedSearchBar 全屏展开；BackHandler 收起并清空；空查询显示 defaultContent、非空显示 searchContent | `SearchAppBar(...)` :63。**无屏幕调用**（0 引用） |
| SendLogBottomSheet.kt (184) | Material 侧"发送日志"用 ModalBottomSheet + 两个 64dp FilledTonalIconButton（存 SAF / FileProvider 分享） | `SendLogBottomSheet(onDismiss, snackbarHostState)` :61 |
| ExpressiveScaffold.kt (50) | Scaffold 包 surfaceContainer 默认底 + 顶栏配色 helper | `ExpressiveScaffold(...)` :17；`expressiveTopAppBarColors()` :44 |
| ExpressiveSwitch.kt (55) | Switch + 拇指勾/叉图标（SwitchDefaults.IconSize） | `ExpressiveSwitch(checked, onCheckedChange, showThumbIcon, ...)` :18 |
| SettingsItem.kt (57) | ListItem + toggleable(Role.Switch)，supportingContent 用 outline 色 | `SwitchItem(icon, title, summary, checked, onCheckedChange)` :17 |
| TonalCard.kt (47) | surfaceColorAtElevation(1.dp) 底色的两版 Card（可点击/静态） | TonalCard x2 :14,34 |
| SnackBar.kt (38) | SwipeToDismissBox 包裹 SnackbarHost，侧滑关闭 | `SnackBarHost(modifier, hostState, snackBar)` :15 |
| TopBarBackButton.kt (33) | 圆角返回键：surfaceContainerHighest 容器 + onSurfaceVariant 内容 | `TopBarBackButton(onClick, ...)` :15 |

### 3.6 `miuix/`（7 文件 / 1004 行）

| 文件 (LOC) | 用途 | 公开 API |
|---|---|---|
| SuperSearchBar.kt (338) | Miuix 版搜索流：SearchPager 用 animateDpAsState 顶距 + zIndex(5f) 覆盖层 + "取消"横向展开动画 + NavigationBackHandler；SearchBar 是手写 BasicTextField（17sp、surfaceContainerHigh 胶囊、44dp 图标位） | SearchStatus.SearchBox :100；SearchStatus.SearchPager :108；SearchBar :227；SearchBarFake :311；internal Modifier.deferredTopPadding :77 |
| EditText.kt (194) | 手写"标题+摘要左 / 输入右对齐"两栏行，Layout 精确排版，56dp 最小高 | EditText :38；EditTextDefaults :150；BasicComponentColors :188 |
| SendLogDialog.kt (155) | Miuix 版日志浮层：OverlayDialog + 两条 ArrowPreference（保存/分享），Toast 反馈 | `SendLogDialog(show, onDismissRequest, loadingDialog)` :42 |
| SuperEditArrow.kt (127) | ArrowPreference 点开数字编辑 OverlayDialog，内部接 FilterNumber 过滤输入 | `SuperEditArrow(title, defaultValue, ...)` :28 |
| ScaleDialog.kt (83) | 页面缩放 80%-110% 输入框（只收数字，提交时 coerceIn(80,110)） | `ScaleDialog(show, onDismissRequest, volumeState, onVolumeChange)` :25 |
| WarningCard.kt (58) | Miuix 警告卡（动态色走 errorContainer，否则硬编码红），PressFeedbackType.Tilt | `WarningCard(message, color, onClick, action)` :23 |
| DropdownItem.kt (49) | 手写下拉项：首/尾行额外 20dp、中间 12dp 内距 | `DropdownItem(text, optionSize, index, ...)` :19 |

### 3.7 `miuix/` 子目录（animation 257 + effect 661 + modifier 83 = 1001，10 文件）+ `filter/`（133，2 文件）= 12 文件 / 1134 行

| 文件 (LOC) | 用途 | 公开 API |
|---|---|---|
| animation/DampedDragAnimation.kt (145) | 可拖拽浮点状态机：值/速度/按压进度/双轴缩放 5 条独立 Animatable + MutatorMutex；canDrag 判定"落点必须在可视范围内" | class DampedDragAnimation :20，暴露 value/targetValue/pressProgress/scaleX/scaleY/velocity/modifier/press/release/updateValue/animateToValue |
| animation/InteractiveHighlight.kt (112) | AGSL RuntimeShader 径向高光（smoothstep(radius, radius*0.5, dist)）+ BlendMode.Plus，跟随手指 | class InteractiveHighlight(animationScope, position) :25，modifier / gestureModifier |
| modifier/DragGestureInspector.kt (83) | inspectDragGestures：不吃 Initial pass 的 down，逐事件比对 previousPosition，被消费即取消 | `PointerInputScope.inspectDragGestures(...)` :15 |
| effect/OS3BgFrag.kt (90) | HyperOS "流体背景" GLSL：Perlin 噪声 + 4 个径向色点 + HSV 去饱和 + 抖动 | `const val OS3_BG_FRAG` :5 |
| effect/BgEffectConfig.kt (69) | 4 套预设（PHONE/PAD x 明/暗）：4 色点 UV、3 组 16 浮点色、插值周期、光照/饱和/点位偏移 | internal object BgEffectConfig :5 |
| effect/BgEffectModifier.kt (199) | 按帧驱动 + 静态 uniform 缓存 + invalidateDraw | internal Modifier.bgEffectDraw :20 |
| effect/BgEffectPainter.kt (173) | RuntimeShader → Brush，多级缓存（分辨率/边界/颜色/动画时间/设备类型） | internal class BgEffectPainter :14 |
| effect/BgEffectBackground.kt (102) | 对外容器；SDK < TIRAMISU 或 isRuntimeShaderSupported 为假时直接退化为普通 Box | `BgEffectBackground(dynamicBackground, ...)` :27 |
| effect/ColorBlendToken.kt (20) | 2 组 BlendColorEntry（Overlay/HardLight/Luminosity/PlusDarker）给毛玻璃调色 | object ColorBlendToken :9 |
| effect/DeviceType.kt (8) | PHONE / PAD | enum class DeviceType :5 |
| filter/BaseFieldFilter.kt (51) | TextFieldValue 输入过滤基类（onValueChange() 返回 lambda） | open class BaseFieldFilter :7 |
| filter/FilterNumber.kt (82) | 整数过滤：只留数字、负号仅首位、越界丢字符、按光标位置重算 TextRange | class FilterNumber(value, minValue, maxValue) :6 |

### 3.8 markdown/（2 文件 / 553 行）

见第 5 节。GithubMarkdown.kt(474) 提供 `GithubMarkdown(content, isMarkdown, onLoadingChange, containerColor)` :76 与 class MarkdownScrollInterface :418；MarkdownContent.kt(79) 提供 `MarkdownContent(content, isMarkdown)` :27（外层滚动 + 加载占位 + 淡入）。

## 4. 屏幕清单（`ui/screen/**`，19 文件 / 3569 行）

| 屏幕 | 文件 (LOC) | 结构 |
|---|---|---|
| **Home**（首页，pager 页 0） | home/HomeScreen.kt(67) + HomeMiuix.kt(277) + HomeMaterial.kt(205) + HomeUiState.kt(18) + HomeUtils.kt(27) | 三件套模式：Screen 只做 VM/权限/actions 装配。**Miuix**：Scaffold + BlurredBar(TopAppBar) + LazyColumn（scrollEndHaptic/overScrollVertical/水平 12dp）→ 4 张卡：警告卡、权限大卡（右侧 110dp 半透明图标 + 22sp 标题 + 版本号，PressFeedbackType.Tilt）、信息卡（设备型号/Android 版本两行图标+双行文本）、"了解更多" ArrowPreference 卡。**Material**：LargeFlexibleTopAppBar(exitUntilCollapsed) + verticalScroll Column(16dp 间距) → WarningCard(error 色 TonalCard)、PermissionCard（Surface(onClick)+ListItem，secondaryContainer/errorContainer 双态）、InfoCard（SegmentedColumn+2 行）、LearnMoreCard（SegmentedListItem+右箭头）。无开关（只跳权限页/主题页） |
| **Settings**（设置，pager 页 1） | settings/SettingsScreen.kt(41) + SettingsMiuix.kt(191) + SettingsMaterial.kt(175) + SettingsUiState.kt(32) | **Miuix**：3 张 Card：①SwitchPreference 检查更新 ②OverlayDropdownPreference UI 模式 + ArrowPreference 主题 ③ArrowPreference 发送日志（触发 SendLogDialog）+ 关于。**Material**：3 个 SegmentedColumn：①SegmentedSwitchItem 检查更新 ②SegmentedDropdownItem UI 模式 + SegmentedListItem 主题 ③发送日志（SendLogBottomSheet）+ 关于；带 SnackBarHost。同一份状态开两处 UI 模式下拉（索引 0=Miuix、1=Material，UiMode.entries.map { it.name }） |
| **About** | about/AboutScreen.kt(36) + AboutMiuix.kt(449) + AboutMaterial.kt(127) + AboutUiState.kt(17) + AboutUtils.kt(30) | 数据来自 R.string.about_source_link 的 HTML，extractLinks() 用 Html.fromHtml + URLSpan 拆出 link 列表（AboutUtils.kt:14-29）。**Miuix**：Logo 视差三段式（version→name→icon 随滚动偏移依次淡出/缩 5%），textureBlur(150f) + BgEffectBackground 流体背景（alpha 随滚动衰减），SmallTopAppBar 标题 alpha 从 0.35 起随滚动显现，1 张 textureBlur(60f) 卡片装 ArrowPreference 链接行。**Material**：居中 80dp 圆角 16 白底图标 + 名称/版本 + SegmentedColumn 链接行（surfaceBright 底） |
| **ColorPalette / 主题** | colorpalette/ColorPaletteScreen.kt(67) + *Material.kt(656) + *Miuix.kt(660) + ColorPaletteUiState.kt(33) | 最重的屏。共同：顶部"手机预览卡"（aspectRatio(screenRatio) 机框，内含迷你顶栏+卡片+底栏）。**Material**：ToggleButton 连体按钮组选 4 个明暗模式、LazyRow 15 个圆形色块（首项=动态取色）、SegmentedDropdownItem x2（PaletteStyle / ColorSpec）、SegmentedSwitchItem（预测性返回[仅 SDK>=34]/侧滑返回）+ 下拉（pager 手势模式）、TonalCard 内 M3 Slider(0.8-1.1)。**Miuix**：TabRow 3 段明暗、SwitchPreference Monet → 条件展开 OverlayDropdownPreference（键色 / 风格 / 规范）、毛玻璃开关（enable_blur，仅 SDK>=33）、浮动底栏开关 → 条件展开"玻璃质感"开关、ArrowPreference 页面缩放（bottomAction 内嵌 Miuix Slider + keyPoints=[0.8,0.9,1.0,1.1] + Step 触觉）并弹 ScaleDialog |
| **Permission** | permission/PermissionScreen.kt(461) | 单文件双实现（PermissionScreenMiuix :135 / PermissionScreenMaterial :301）。顶部总状态卡（requiredGranted 决定 primary/error 图标）→ 5 行权限：存储 / 通知 / 麦克风 / 电池白名单 / 悬浮窗（悬浮窗标记 required=false）。Material 用 Card+自绘 Row+AssistChip（已授予）/OutlinedButton（去授予）；Miuix 用 Card+BasicComponent 头+5 条 ArrowPreference（endActions 显示"已授予/去授予"） |

## 5. Markdown / WebView 管线

### 5.1 资产（AS:，10 文件 / 2038 行）

| 文件 | 行 | 说明 |
|---|---|---|
| template.html / template_dark.html | 18 / 18 | 仅 html data-color-mode + 5 个 link（colors/markdown/syntax/font/suggested_changes）+ main class="markdown-body" 与 @body@ 占位；`window.native = { sendMessage: function() {} }` **把 JS 桥打桩成空实现**（template.html:15） |
| colors_light.css / colors_dark.css | 171 / 171 | GitHub Primer 色阶（blue/gray/green/yellow/orange/red/pink/purple 各 10 档）+ 语义别名（--textPrimary/--link/--border/--diffLine*Background/--suggestedChange*） |
| markdown.css | 883 | 全量 .markdown-body 排版（标题/kbd/table/blockquote/pre/code/task-list），颜色全走变量，明暗共用 |
| syntax_light.css / syntax_dark.css | 124 / 124 | pl-c/pl-k/pl-s 等词法类着色（GitHub syntax 配色） |
| suggested_changes.css | 141 | GitHub 建议修改块 .js-suggested-changes-blob 样式 |
| font.css | 3 | -apple-system, sans-serif |
| markdown.js | 386 | class GitHub：_sendMessage、_addHeightListener/_updateHeight、_unescapeImageURLs、_applyImageLoadingWorkaround、_didLoad、load(id, html, ...)、_addSuggestedChangesButton、_commitSuggestedChange、getAnchorPosition/_positionOf、start() |

### 5.2 渲染链路（KT:ui/component/markdown/GithubMarkdown.kt）

1. **Markdown → HTML**：commonmark-java 0.30.0 + 4 扩展 Tables / Strikethrough(requireTwoTildes) / Autolink / TaskListItems（:101-113）；isMarkdown=false 时入参当 HTML 直出（:112）。
2. **主题注入**：remember(isDark) 选 template.html 或 template_dark.html 读字符串（:97-100）；再拼一段 style:root{--background:transparent; --pre-background/--code-background; --tr-alt/--thead-background; --textPrimary; --link} 覆盖层再拼正文（:114-130），最后 replace(@dir@)/(@commit_label@)/(@body@)（:131-134）。**没有 CSS 变量运行时热替换**——明暗切换靠换整份模板（remember(isDark) 触发重载）。
3. **颜色来源**：Material 直接映射 surfaceContainerHigh/Low（:446-449）；Miuix 用 HSL 调明度 ±0.05/±0.02 再用 ensureVisibleByMix 二分保证对比度 >= 1.1/1.05（:453-471，依赖 ui/util/Colors.kt）。
4. **装载**：AndroidView + WebViewAssetLoader 挂 /assets/（:211-213），loadDataWithBaseURL("https://appassets.androidplatform.net", html, "text/html", UTF-8, null)（:397-400）；textZoom 每次 update 重设（:393）。
5. **安全设置**：allowFileAccess=false、allowContentAccess=false、setSupportZoom(false)、setGeolocationEnabled(false)、mixedContentMode=ALWAYS_ALLOW、cacheMode=LOAD_CACHE_ELSE_NETWORK（:193-204）。
6. **崩溃自愈**：onRenderProcessGone 必须 removeView + destroy 且绝不复用（:215-222），上层按文档最多重建 3 次、且只在 RESUMED 时重建（:136-169）。
7. **外链/子资源**：点击一律 ACTION_VIEW 交系统（:292-303）；http(s) 子资源用 App 的 OkHttp（10MB 缓存）代理并回写 mime/charset，失败返回堆栈文本（:309-337、KT:TemplateApplication.kt:51-59）。
8. **横向滚动冲突**：注入 JS 监听 touchstart/touchmove/scroll 找最近可横向滚动容器，变化时回调 AndroidScroll.updateScrollState(l, r)（:228-289）→ @JavascriptInterface 写 volatile 布尔（:418-430）；原生 OnTouchListener 判断 |dx|>|dy| 且可继续滚动才 requestDisallowInterceptTouchEvent(true)，纵向一律放行父容器（:340-383）。

### 5.3 能否原样复用

| 资产 | 结论 |
|---|---|
| template.html + markdown.css + colors_*.css + syntax_*.css + font.css | **技术上可直接搬**：纯静态资源、无原生依赖；宿主只需提供 WebViewAssetLoader 的 /assets/ 路由并按 @dir@ / @commit_label@ / @body@ 三个占位符拼串。注意 --background 必须由原生注入（模板无默认值），否则背景透明 |
| suggested_changes.css | 依赖 markdown.js 的 _addSuggestedChangesButton，而 window.native.sendMessage 已被打桩为空实现 → **建议修改链路在模板里是死的**，该 css 实际不生效，可不搬 |
| markdown.js | 可搬但功能大半用不上：高度上报、图片 workaround、commit 按钮都依赖宿主桥；只有 _unescapeImageURLs / getAnchorPosition 有独立价值。搬它必须自己实现 window.native.sendMessage |
| **许可证** | 这批资产**未在本仓注明来源**（全仓 grep 无 license header）。markdown.css / colors_*.css / markdown.js 的 class GitHub 结构与类名强烈指向 Thunderbird for Android / GitHub Primer 系（MPL-2.0 / MIT）。**搬前必须回溯上游并按其许可署名**，不能因"来自 GPL 仓库"就当作 GPL 覆盖 |

## 6. 交互模式

| 维度 | 实现 |
|---|---|
| **导航模型** | 双层：①HorizontalPager 两页（Home/Settings）为主容器 ②NavDisplay + Navigator（自研，包在 miuix-nav 的 NavBackStack 上）承担 About/ColorPalette/Permissions。Route 是 @Serializable @Parcelize sealed interface Route : NavKey（KT:ui/navigation3/Routes.kt:12-37）；Navigator 提供 push/replace/replaceAll/pop/popUntil + navigateForResult/setResult/observeResult/clearResult（MutableSharedFlow(replay=1) 做结果总线，KT:ui/navigation3/Navigator.kt:80-125）；push 会去重（同 key 不入栈） |
| **返回优先级** | MainScreenBackHandler：仅当"当前是 Main 且栈深=1 且 pager 不在第 0 页"才拦截，回第 0 页（KT:ui/MainActivity.kt:317-336）；搜索展开时另有 NavigationBackHandler/BackHandler 先收起搜索（KT:ui/component/miuix/SuperSearchBar.kt:192-205、KT:ui/component/material/SearchBar.kt:141-145）；NavDisplay 兜底 onBack = { navigator.pop() }（KT:ui/MainActivity.kt:151-153） |
| **侧滑返回** | NavSwipeDirection.None / LeftToRight（RTL 下 RightToLeft），受设置 enable_swipe_dismiss 控制（KT:ui/MainActivity.kt:130-138）；NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()) 让转场跟随物理圆角 |
| **翻页手势三档** | PagerInterceptionMode：Native / CrossAxisInterceptor / iOS-like；拦截档用 pagerGestureOverride + PagerGestureNestedScrollConnection 且 userScrollEnabled=false（KT:ui/MainActivity.kt:197-198,236-244） |
| **底栏行为** | 单击 animateToPage（springAnimateToPage）；浮动栏支持**横向拖拽**：拖动时 updateValue(target + dx/tabWidth)、橡皮筋溢出 4dp、松手 fastRoundToInt 吸附 + 回弹；selectedIndex 与内部 currentIndex 双向 snapshotFlow 同步并 drop(1) 防自触发（KT:ui/component/FloatingBottomBar.kt:216-275） |
| **滚动回顶** | ScrollToTopOnChange 提供能力但 0 引用；实际列表用 LazyColumn + scrollEndHaptic() + overScrollVertical()，overscrollEffect=null 关掉 Compose 默认拉丝（KT:ui/screen/home/HomeMiuix.kt:96） |
| **对话框 vs 浮层** | 统一抽象 DialogHandle（show/hide/isShown）+ awaitConfirm 挂起式；Miuix 用 WindowDialog/OverlayDialog，Material 用 AlertDialog/ModalBottomSheet。**同一功能两端不同形态**：发送日志在 Material 是 ModalBottomSheet（横排两个 64dp 按钮），在 Miuix 是 OverlayDialog（竖排两条 ArrowPreference）→ "底部浮层"不是跨端设计语言，只是 Material 侧实现 |
| **触觉/按压反馈** | Miuix PressFeedbackType.Tilt（卡片倾斜）；开关行点按 VirtualKey；holdDownState 让 ArrowPreference 箭头保持按下态（KT:ui/screen/colorpalette/ColorPaletteScreenMiuix.kt:418） |
| **权限 UX** | PermissionManager（KT:permission/PermissionManager.kt:18-72）读 5 项真值：Environment.isExternalStorageManager()、POST_NOTIFICATIONS（<33 回退 areNotificationsEnabled）、RECORD_AUDIO、isIgnoringBatteryOptimizations、canDrawOverlays；requiredGranted = storage && notification && microphone && batteryWhitelist（**悬浮窗不计入 required**，KT:permission/PermissionState.kt:10-11）。刷新靠 LifecycleResumeEffect（KT:ui/screen/permission/PermissionScreen.kt:103-106）。交互分两类：**运行时权限**用 RequestPermission launcher（通知/麦克风），**特殊权限**跳系统设置页（ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION / ACTION_APP_NOTIFICATION_SETTINGS / ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS / ACTION_MANAGE_OVERLAY_PERMISSION），统一用 StartActivityForResult 回调后 refresh（KT:ui/screen/permission/PermissionScreen.kt:96-117）；无 rationale、无"不再询问"引导 |

## 7. 许可与复用（GPL-3.0 → MPL-2.0 应用）

### 7.1 许可事实

- 根 LICENSE 为 **GNU GPL v3.0 全文**（非 LGPL），docs/README.md 的 License 段亦明确。无逐文件 SPDX 头。
- 但 12 个文件带上游归属注释，可据此**绕开 GPL 直接取上游原件**：①`FloatingBottomBar.kt:1`（compose-miuix-ui 示例 IosLiquidGlassNavigationBar — Apache 2.0）②`liquid/{CombinedBackdrop, Lens, InnerShadow, Vibrancy}.kt:1-2`（Kyant0/AndroidLiquidGlass — Apache 2.0，4 文件）③`miuix/effect/*`（6 文件，注明 "Mirrored from compose-miuix-ui example"，属 Apache-2.0 镜像；但仓内未逐文件标许可，取用时以上游仓库为准）。
- assets/webview/* 无任何许可声明（来源见 5.3）。

### 7.2 GPL-3.0 的传染边界

| 动作 | 后果 |
|---|---|
| 复制任意 .kt 到 dsh-android | 与 MPL-2.0 代码形成**组合作品** → 整个 APK 必须以 GPL-3.0 分发（MPL-2.0 第 3.3 条允许 MPL→GPL 单向升级，反向不行） |
| 仅"参考阅读"后独立实现 | 不构成衍生作品（版权只保护表达），但需避免逐行近似、避免复制标识符体系与注释 |
| 搬 Apache-2.0 的 liquid/*、FloatingBottomBar | Apache-2.0 与 GPLv3 兼容，且**直接从上游 Kyant0/AndroidLiquidGlass、compose-miuix-ui 取原件**更干净：保留 NOTICE/归属即可放进 MPL-2.0 项目 |
| 搬 assets/webview/* | 需回溯 Primer(MIT) / Thunderbird(MPL-2.0) 上游并按其条款署名；markdown.css 体积大且是 GitHub 排版复刻，**建议直接用上游 primer/css 原件而非本仓副本** |
| 选择整包 GPL-3.0（另一条路） | dsh-android 全仓转 GPLv3、随 Releases 提供完整对应源码与构建脚本；MPL-2.0 部分可保留双许可，但 GPL 的专利/反 Tivoization 条款覆盖整包 |

### 7.3 可复制 / 必须重写 / 可安全复刻

| 类别 | 内容 |
|---|---|
| **可直接复制（Apache-2.0 上游原件）** | liquid/Lens.kt（AGSL 折射）、liquid/InnerShadow.kt、liquid/Vibrancy.kt、liquid/CombinedBackdrop.kt、FloatingBottomBar.kt 的玻璃/高光结构、miuix/effect/*（OS3 背景着色器 + 预设）——但请以 Kyant0/AndroidLiquidGlass、compose-miuix-ui 仓库为源并保留 Apache NOTICE |
| **必须重写（GPL 表达 + 本项目原创）** | Navigator.kt（含 result bus）、Dialog.kt 手柄框架、SegmentedList.kt 全家桶、HomeMiuix / SettingsMiuix / ColorPalette* 屏代码、SuperSearchBar.kt、EditText.kt、GithubMarkdown.kt（WebView 生命周期/JS 桥/OkHttp 代理）、MarkdownContent.kt、PermissionManager 与 PermissionScreen、MainActivity 的 CompositionLocal 装配、ui/util/Colors.kt 算法实现 |
| **可安全"复刻观感"（非版权客体）** | ①颜色角色与语义映射（动态取色 + 15 色板 + #DFFAE4/#F8E2E2/#36D167/#F72727/#111111 这套权限卡配色）②7 档 ColorMode 与 AMOLED 黑的取舍 ③"12dp 列表内距 + 56dp 行高 + 12dp 卡间距"的节奏 ④胶囊底栏 + 玻璃质感 + 选中项放大 1.2x / 指示器 1.39x ⑤"Miuix 与 Material 双皮、设置页可切"的产品结构 ⑥权限页 5 行的信息结构 ⑦关于页 Logo 三段视差。这些属思想/功能/观感（idea-expression、merger、scenes a faire），用自家代码再画一遍不侵权 |
| **灰区（谨慎）** | 屏幕像素级布局（尤其主页 4 卡顺序与权限大卡构图、主题页预览机框）；建议以截图为参照重排比例，不逐尺寸抄数值（该仓尺寸本就是散落字面量，抄无意义）；图标一律用 Material Icons / Miuix Icons 原件，**不复制 res/drawable** |

### 7.4 对 dsh-android 的落地建议（4 条）

1. **零拷贝起步**：先自建 KsuTokens.kt（色板/间距/圆角/字号常量），把 1.1-1.5 的"事实刻度"（4/6/8/12/16/20/24 dp、56dp 行高、11/15/17/22/35 sp）写成显式令牌——上游恰恰缺这一层，这正好是我们的差异化而非抄袭点。
2. **底栏与玻璃**：只从 Apache-2.0 上游取 Lens/InnerShadow/vibrancy，其余（拖拽状态机、橡皮筋、layerBackdrop 双层采样）按行为规格重写。
3. **Markdown**：不要整搬 GithubMarkdown.kt。沿用已验证的协议层结论（commonmark → 自绘 Compose），仅当需要"代码块/表格像素级 GitHub 观感"时复用 markdown.css + colors_*.css（须溯源署名），并保留"WebView 渲染进程死亡必须销毁重建、最多 3 次"这条硬约束（KT:ui/component/markdown/GithubMarkdown.kt:148-169,215-222）。
4. **不要抄的部分**：FilterNumber（自带中文注释与越界丢字符策略）、ScrollToTopOnChange（0 引用且依赖 miuix 的 requestScrollToItem 扩展）、SearchStatus/SuperSearchBar（0 引用的示例代码）、suggested_changes.css（上游本就失效）。
