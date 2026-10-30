# 07 · compose-miuix-ui/miuix 公共组件 API 盘点（Apache-2.0，616 .kt / 162 553 行）

> 基线：`/tmp/miuix`（单次浅克隆，`git log` 仅 1 条：`23b469e`，故**无法按 git 历史判定"某组件自哪个版本引入"**）。
> 版本 `0.9.4`（`build-logic/src/main/kotlin/BuildConfig.kt:5`）；发布模块 9 个（`settings.gradle.kts:45-53`）。
> 缩写下述路径前缀 `src/commonMain/kotlin/top/yukonga/miuix/kmp/`：**U**=miuix-ui · **P**=miuix-preference · **I**=miuix-icons/icon/extended · **B**=miuix-blur · **S**=miuix-squircle · **N**=miuix-nav · **C**=miuix-core · **H**=miuix-shader · **W**=miuix-window(`src/desktopMain/…`) · **A**=`miuix-ui/src/androidMain/kotlin/top/yukonga/miuix/kmp`。
> 所有 `path:line` 相对 `/tmp/miuix`。`✅`=库内直属 API · `⚠️`=可拼但有缺口 · `❌`=库内没有。

## 0. 结论速览

| # | 结论 | 证据 |
|---|---|---|
| 1 | 12 项需求中 **9 项有直属 API**；Toast、骨架屏、分段控件"内容槽"、头像圆角 4 处必须手写 | §2 矩阵 |
| 2 | 全库**不依赖 `androidx.compose.material3`**（只吃 `materialKolor` 与 `material3-window-size-class`），可与 M3 同栈共存 | `miuix-ui/build.gradle.kts:60-68`、`gradle/libs.versions.toml:49,43` |
| 3 | 无任何 `@Since`/`@Experimental*` 版本标注；**唯一 opt-in 是 `ExperimentalScrollBarApi`**，且整库自述 experimental | `U/interfaces/ExperimentalScrollBarApi.kt:13-23`、`README.md:5` |
| 4 | 硬门槛：Compose Multiplatform **1.12.2** + Kotlin **2.4.21**；全库 minSdk **24**，但 `miuix-blur` 自身 minSdk **33** | `gradle/libs.versions.toml:3,10`、`BuildConfig.kt:14`、`miuix-blur/build.gradle.kts:31` |
| 5 | 主题有完整 Monet 链路，但**没有 M3 `ColorScheme`→Miuix `Colors` 的公开适配器**（映射函数是 `internal`）；只能自己写 53 角色赋值 | `U/theme/MonetMapping.kt:86`（`internal fun`）、`U/theme/Colors.kt:68-122` |
| 6 | `miuix-window` 只有 `jvm("desktop")` 目标，**Android 上没有该模块** | `miuix-window/build.gradle.kts:20` |

## 1. 逐模块公共 Composable 清单

### 1.1 miuix-ui（核心，`api` 传递 core+squircle：`miuix-ui/build.gradle.kts:61-62`）

| Composable | 用途 | 关键参数（默认值） | 稳定性 | 证据 |
|---|---|---|---|---|
| `Scaffold` | 根壳；**唯一 overlay 宿主** | `topBar`/`bottomBar`/`snackbarHost`/`floatingToolbar`、`popupHost={MiuixPopupHost()}`、`contentWindowInsets` | 稳定 | `U/basic/Scaffold.kt:79,90` |
| `Surface` ×2 | 基面 / 可点击面 | `shape`、`color`、`contentColor`、`border`、`shadowElevation` | 稳定 | `U/basic/Surface.kt:46,89` |
| `Card` ×2 | 卡片 | `cornerRadius=16.dp`、`pressFeedbackType=None`、`onClick`/`onLongPress` | 稳定 | `U/basic/Card.kt:50,87`；`CardDefaults.CornerRadius` `:190` |
| `Button` / `TextButton` | 主/文本按钮 | `cornerRadius=16.dp`、`minWidth=58.dp`、`minHeight=40.dp`、`colors` | 稳定 | `U/basic/Button.kt:50,111`；`ButtonDefaults` `:152-174` |
| `IconButton` | 图标按钮 | `backgroundColor`、`cornerRadius=40.dp`、`minWidth/Height=40.dp` | 稳定 | `U/basic/IconButton.kt:40`；默认值 `:79-94` |
| `FloatingActionButton` | 悬浮球 | `shape=CircleShape`、`containerColor=primary` | 稳定 | `U/basic/FloatingActionButton.kt:36` |
| `FloatingToolbar` | 悬浮工具条 | `color=surfaceContainer`、`cornerRadius`、`shadowElevation=4.dp` | 稳定 | `U/basic/FloatingToolbar.kt:38` |
| **`FloatingNavigationBar`** + `FloatingNavigationBarItem` | **需求①悬浮底栏** | `cornerRadius`、`horizontalOutSidePadding=36.dp`、`shadowElevation=1.dp`；item: `icon: ImageVector`/`label`/`badge` | 稳定 | `U/basic/NavigationBar.kt:275,377`；默认值 `:510-533` |
| `NavigationBar` + `RowScope.NavigationBarItem` | 贴底导航 | `mode=IconAndText\|IconOnly\|IconWithSelectedLabel`、`showDivider` | 稳定 | `U/basic/NavigationBar.kt:74,145`；`NavigationBarDisplayMode` `:544-553` |
| **`NavigationRail`** ×2 + `NavigationRailItem` + `rememberNavigationRailState` | **需求⑧平板侧栏** | `expanded` 或 `state: NavigationRailState`、`minWidth=80.dp`、`expandedWidth=240.dp`、`header` | 稳定 | `U/basic/NavigationRail.kt:91,142,329,731`；默认值 `:601-607` |
| **`SearchBar`** + `InputField` | **需求②搜索栏（带展开动画）** | `inputField` slot、`expanded`、`onExpandedChange`、`outsideEndAction`、`content`（展开区）；`InputField.color=surfaceContainerHigh`（传 `Color.Transparent` 可自己叠 blur 底） | 稳定 | `U/basic/SearchBar.kt:81,158`；展开动画 `:106-113`；返回键收起 `:122` |
| `TextField` ×3 | **需求⑦输入框** | `TextFieldState` / `TextFieldValue` / `String` 三重载；`label`、`leadingIcon`/`trailingIcon`、`lineLimits`、`inputTransformation` | 稳定 | `U/basic/TextField.kt:82,188,294`；`TextFieldDefaults` `:374-388` |
| `Switch` | **需求⑥开关** | `checked`、`onCheckedChange=null` 即只读、`colors` | 稳定 | `U/basic/Switch.kt:66` |
| `Checkbox` / `RadioButton` | 复选 / 单选 | `state: ToggleableState` / `selected`、`onClick?` | 稳定 | `U/basic/Checkbox.kt:60`、`U/basic/RadioButton.kt:59` |
| `Slider` / `VerticalSlider` / `RangeSlider` | **需求⑥滑条** | `valueRange`、`steps`、`keyPoints`、`magnetThreshold=0.02f`、`hapticEffect`、`height=28.dp` | 稳定 | `U/basic/Slider.kt:88,295,498`；`SliderDefaults` `:1348-1357` |
| `NumberPicker` | 滚轮选择 | `range=0..10`、`visibleItemCount=5`(须奇数≥3)、`wrapAround` | 稳定 | `U/basic/NumberPicker.kt:69` |
| `TabRow` / `TabRowWithContour` | 标签行（**入参只有 `List<String>`**） | `selectedTabIndex`、`cornerRadius`、`height=42.dp/45.dp`、`itemSpacing` | 稳定 | `U/basic/TabRow.kt:87,228`；`tabs: List<String>` `:88` |
| **`BasicComponent`** ×2 | **需求③设置行骨架** | `title`/`summary`、`startAction`、`endActions: RowScope`、`bottomAction`、`onClick`、`holdDownState` | 稳定 | `U/basic/Component.kt:59,126`；`InsideMargin` `:266` |
| `SmallTitle` | 分组小标题 | `text`、`textColor=onBackgroundVariant` | 稳定 | `U/basic/SmallTitle.kt:25` |
| `HorizontalDivider` / `VerticalDivider` | 分隔线 | `thickness`、`color=dividerLine` | 稳定 | `U/basic/Divider.kt:30,53` |
| `BadgedBox` / `Badge` | 角标 | `containerColor`、`contentColor`、`content?` | 稳定 | `U/basic/Badge.kt:51,133` |
| `TopAppBar` / `SmallTopAppBar` / `MiuixScrollBehavior` | 折叠大标题栏 | `title`、`largeTitle`、`subtitle`、`scrollBehavior` | 稳定 | `U/basic/TopAppBar.kt:100,173,250` |
| `LinearProgressIndicator` / `CircularProgressIndicator` | **需求⑩定量进度**（`progress=null`→无限动画） | `progress`、`colors`、`height=6.dp`/`strokeWidth=4.dp`、`size=30.dp` | 稳定 | `U/basic/ProgressIndicator.kt:49,124`；默认值 `:321-333` |
| `InfiniteProgressIndicator` | 轨道绕点 loading | `color`、`size`、`orbitingDotSize` | 稳定 | `U/basic/ProgressIndicator.kt:220` |
| **`PullToRefresh`** + `rememberPullToRefreshState` | **需求⑪下拉刷新** | `isRefreshing`/`onRefresh`、`contentPadding`、`topAppBarScrollBehavior`、`refreshTexts`（**默认 4 条英文**）、`onPullProgress` | 稳定 | `U/basic/PullToRefresh.kt:125,256`；英文文案 `:1029-1034` |
| **`SnackbarHost` / `Snackbar`** + `SnackbarHostState` | **需求⑨反馈条** | `showSnackbar(msg, actionLabel?, withDismissAction, duration=Short)`；`Short/Long/Indefinite/Custom(ms)` | 稳定 | `U/basic/Snackbar.kt:154,183,272,385`；`SnackbarDuration` `:80-91` |
| `VerticalScrollBar` / `HorizontalScrollBar` + `rememberScrollBarAdapter` ×3 | **需求⑫滚动辅助** | `adapter`（`ScrollState`/`LazyListState`/`LazyGridState`）、`thumbWidth`、`thumbMinLength` | ⚠️ **`@ExperimentalScrollBarApi`（WARNING 级）** | `U/basic/ScrollBar.kt:79,90,101,135,175`；注解 `U/interfaces/ExperimentalScrollBarApi.kt:13-23` |
| **`OverlayDialog`** | **需求④对话框（alert+自定义内容）** | `show`、`title`/`summary`、`enableWindowDim`、`maxWidth=420.dp`、`cornerRadius=32.dp`、`largeScreen` | 稳定 | `U/overlay/OverlayDialog.kt:47`；默认值 `U/layout/DialogContentLayout.kt:451-467` |
| **`OverlayBottomSheet`** | **需求⑤底部弹层** | `show`、`title`、`startAction`/`endAction`、`allowDismiss`、`enableNestedScroll`、`cornerRadius=28.dp`、`sheetMaxWidth=640.dp` | 稳定 | `U/overlay/OverlayBottomSheet.kt:46`；默认值 `U/layout/BottomSheetContentLayout.kt:861-883` |
| `OverlayListPopup` / `OverlayCascadingListPopup` | 下拉/级联菜单 | `show`、`entries: List<DropdownEntry>`、`alignment` | 稳定 | `U/overlay/OverlayListPopup.kt:37`、`U/overlay/OverlayCascadingListPopup.kt:43` |
| `WindowDialog`/`WindowBottomSheet`/`Window*Popup` | **窗口级**弹层（不依赖 Scaffold） | 同 Overlay，走 `androidx.compose.ui.window.Dialog` | 稳定 | `U/window/WindowDialog.kt:19,54,91`、`U/window/WindowBottomSheet.kt:52` |
| `LocalDismissState` | 弹层内容里主动关闭 | `(() -> Unit)?` | 稳定 | `U/theme/DismissState.kt:26` |
| `TooltipBox` / `RichTooltipBox` / `rememberTooltipState` | 长按/悬停气泡 | `text`、`positioning`、`isPersistent`、`actionText` | 稳定 | `U/basic/Tooltip.kt:273,621,672` |
| `BreadcrumbBar` | 面包屑 | `items: List<BreadcrumbItem>`、`highlightIndex` | 稳定 | `U/basic/BreadcrumbBar.kt:101` |
| `ColorPicker` / `ColorPalette` | HSV/OkLab/OkLch 取色 | `colorSpace=HSV`、`showPreview` | 稳定 | `U/basic/ColorPicker.kt:69,131,379,631,761`、`U/basic/ColorPalette.kt:64` |
| `Text` ×4 | 文本 | `style=LocalTextStyles.current.main`、`autoSize: TextAutoSize?` | 稳定 | `U/basic/Text.kt:90,108` |
| `RowScope.DropdownArrowEndAction` / `DropdownImpl` | **需求⑦"带箭头的行"** | `actionColor`、`item`/`optionSize` | 稳定 | `U/basic/Dropdown.kt:45,81,220` |

> ⚠️ **Overlay* 必须落在 `Scaffold` 的 `popupHost` 内**否则不渲染（`docs/components/overlaydialog.md:5-16`；默认值见 `U/basic/Scaffold.kt:90`）。聊天 App 若不套 Miuix Scaffold，用 `Window*` 变体。

### 1.2 miuix-preference（依赖 ui：`README.md:30`）

| Composable | 用途 | 关键参数 | 证据 |
|---|---|---|---|
| `ArrowPreference` | **需求③⑦带右箭头设置行**（内嵌 `MiuixIcons.Basic.ArrowRight`） | `title`、`summary`、`onClick`、`endActions`、`holdDownState` | `P/preference/ArrowPreference.kt:49`；箭头绘制 `:100-113` |
| `SwitchPreference` | 开关行 | `checked`、`onCheckedChange: (Boolean)->Unit`（非空）、`switchColors` | `P/preference/SwitchPreference.kt:45` |
| `CheckboxPreference` / `RadioButtonPreference` | 复选/单选行 | `checked`/`selected`、`checkboxLocation`/`radioButtonLocation` | `P/preference/CheckboxPreference.kt:49`、`P/preference/RadioButtonPreference.kt:52` |
| `SliderPreference` / `RangeSliderPreference` | 滑条行 | `value`、`valueText`、`keyPoints` | `P/preference/SliderPreference.kt:83,212` |
| `OverlayDropdownPreference` ×3 | 选项行（Overlay） | `items: List<String>`/`entry`/`entries`、`collapseOnSelection` | `P/preference/OverlayDropdownPreference.kt:58,113,217` |
| `OverlaySpinnerPreference` ×6 | 弹窗选择（含 `dialogButtonString` 变体） | `items: List<DropdownItem>`、`selectedIndex` | `P/preference/OverlaySpinnerPreference.kt:60,288` |
| `WindowDropdownPreference`/`WindowSpinnerPreference` | Window 变体 | 同上，无 `renderInRootScaffold` | `P/preference/WindowDropdownPreference.kt:56`、`P/preference/WindowSpinnerPreference.kt:57` |
| `Overlay/Window IconDropdownMenu` + `IconCascadingDropdownMenu` | 图标按钮菜单/级联 | `content` 作触发图标、`cornerRadius` | `P/menu/OverlayIconDropdownMenu.kt:28`、`P/menu/OverlayIconCascadingDropdownMenu.kt:32` |
| `OverlayDropdownPopup`/`*DropdownDialog` | 底层弹层原语 | `show`、`onDismiss`、`onDismissFinished` | `P/popup/OverlayDropdownPopup.kt:35,115` |

### 1.3 miuix-icons / 1.4 miuix-core / 1.5 miuix-shader / 1.6 miuix-window

| 模块 | 公共面 | 证据 |
|---|---|---|
| **miuix-icons** | 156 个扩展图标名（156 个 .kt，名与文件一对一），**每个图标 5 字重**（`Light/Normal/Regular/Medium/Demibold`）；入口 `MiuixIcons.<Name>` = Regular 别名 | `ls I/*.kt`=156；`grep "MiuixIcons.*: ImageVector"`=156；别名 `I/Settings.kt:15`、Regular `:242`；5 字重 `C/icon/MiuixIcons.kt:6-13` |
| **miuix-core** | `MiuixIcons` 空对象（5 嵌套 object 供扩展属性挂载）、`platform(): Platform`、`enum Platform{Android,IOS,Desktop,WasmJs,MacOS,Js}`、`getRoundedCorner(): Dp`、`platformDialogProperties()`、`RemovePlatformDialogDefaultEffects()`、`hasFocusReassignBug` | `C/icon/MiuixIcons.kt:6-13`、`C/utils/Utils.kt:13-20,25,31,37,46,54`、`miuix-core/src/androidMain/…/Utils.android.kt:24` |
| **miuix-shader** | `expect isRuntimeShaderSupported()`（Android=API33）/`isRenderEffectSupported()`（Android=API31）、`interface RuntimeShader`（`setFloat/Int/ColorUniform`、`setInputShader`）、`RuntimeShader(src)`、`asComposeShader()`、`asBrush()` | `H/RuntimeShader.kt:11,123,130,133,141`、`H/RenderEffect.kt:7`、`miuix-shader/src/androidMain/…/RuntimeShader.android.kt:18`、`RenderEffect.android.kt:12` |
| **miuix-window** | **仅 Desktop JVM**：`MiuixWindow`、`MiuixDialogWindow`、`MiuixCaptionBar`、`MiuixCaptionButton`、`LocalWindowState`/`LocalDialogState`（X11/Win32 DWM/macOS 实现），**Android 无此模块** | `miuix-window/build.gradle.kts:20`；`W/MiuixWindow.kt:53`、`W/MiuixCaptionBar.kt:27`、`W/MiuixDialogWindow.kt:53`、`W/basic/MiuixCaptionButton.kt:68` |

## 2. 12 项需求覆盖矩阵

| # | 需求 | 承接 | 具体 API | 结论 | 缺口 / 落法 |
|---|---|---|---|---|---|
| R1 | 悬浮底部导航 | ui | `FloatingNavigationBar`+`...Item` | ✅ | `U/basic/NavigationBar.kt:275,377`；官方限 2–5 项（`docs/components/navigationbar.md:5`）；**item 只渲染 icon，label 仅作 contentDescription**（`:399-414`）→ 要"图标+文字"得自己包一层或用 `NavigationBar(mode=IconWithSelectedLabel)` |
| R2 | 搜索栏 + 展开动画 | ui | `SearchBar`+`InputField` | ✅ | 展开动画 `expandHorizontally+slideIn`（`U/basic/SearchBar.kt:106-113`）；展开区自绘；配 blur 时 `InputField(color=Color.Transparent)`（`:170`） |
| R3 | 分段 / 分组设置列表 | ui+preference | `TabRow`/`TabRowWithContour`、`BasicComponent`、`*Preference`、`SmallTitle`、`HorizontalDivider` | ⚠️ | **`TabRow` 入参是 `List<String>`，无 content slot**（`U/basic/TabRow.kt:88`）→ 带图标/计数的分段控件需手写（可复用 `squircleClip/Border`+`selectable`） |
| R4 | 对话框（alert + 自定义内容） | ui | `OverlayDialog`（`content` 即自定义区）/ `WindowDialog` | ✅ | 需 `Scaffold.popupHost`（`U/basic/Scaffold.kt:90`）；无 Scaffold 用 `WindowDialog`（`U/window/WindowDialog.kt:54`） |
| R5 | 底部弹层 | ui | `OverlayBottomSheet` / `WindowBottomSheet` | ✅ | `enableNestedScroll` 已处理列表内滚动（`U/overlay/OverlayBottomSheet.kt:46`） |
| R6 | 开关 / 滑条 / 复选 | ui | `Switch`、`Checkbox`、`RadioButton`、`Slider/RangeSlider` | ✅ | `Switch` 支持拖拽+触觉（`U/basic/Switch.kt:66-120`） |
| R7 | 带箭头的输入行 | ui+preference | `ArrowPreference`、`RowScope.DropdownArrowEndAction`、`TextField.trailingIcon` | ✅ | 箭头用内置 `MiuixIcons.Basic.ArrowRight/ArrowUpDown`（`U/icon/basic/ArrowRight.kt:14`、`ArrowUpDown.kt:14`） |
| R8 | 平板导航栏（rail） | ui | `NavigationRail`(可展开)+`NavigationRailItem`+`rememberNavigationRailState` | ✅ | 展开 240.dp/收起 80.dp（`:601-607`）；`NavigationRailState` `:692` |
| R9 | Toast / Snackbar | ui | `SnackbarHost`+`showSnackbar` | ⚠️ | **全模块 `Toast` 命中 0**；`Snackbar` 只在 `Scaffold.snackbarHost` 中出（`U/basic/Snackbar.kt:272`）→ 全局 Toast 需自建 overlay 或复用 `FloatingToolbar`（`U/basic/FloatingToolbar.kt:38`） |
| R10 | 进度 / loading | ui | `Linear`/`Circular`/`InfiniteProgressIndicator` | ⚠️ | **`Skeleton`/`Shimmer` 命中 0**；聊天列表骨架屏必须手写 |
| R11 | 下拉刷新 + 滚动助手 | ui | `PullToRefresh`、`overScrollVertical/Horizontal/OutOfBound`、`Modifier.scrollEndHaptic`、`Modifier.pagerGestureOverride` | ✅/⚠️ | 文案默认英文→传 `refreshTexts`（`U/basic/PullToRefresh.kt:1029-1034`）；overscroll `U/utils/Overscroll.kt:51,64,87`、haptic `U/utils/ScrollEndHaptic.kt:124`、pager `U/utils/PagerGestureUtils.kt:169,201,223` |
| R12 | blur/glass + squircle + 图标 | blur+squircle+icons | `textureBlur`/`progressiveTextureBlur`/`Highlight`/`rememberTiltLight`、`squircle*` | ✅（API33+） | 见 §5/§6；**低端机无"降级样式"，只有"关掉"**（`B/DrawBackdropModifier.kt:118`） |

**明确不覆盖、必须 M3 或手写**：Toast（R9）、骨架屏/Shimmer（R10）、分段控件内容槽（R3）、Avatar/`Chip`/`FlowRow`/日期选择器（全库 grep 均 0 命中）、Markdown 渲染、富文本选择工具条。

## 3. Theming：`MiuixTheme.colorScheme` / `textStyles`

| 事实 | 证据 |
|---|---|
| 两个重载：`MiuixTheme(controller=…)` 与 `MiuixTheme(colors=…)`，均注入 `LocalColors/LocalTextStyles/LocalIndication/LocalContentColor/LocalOverscrollFactory`；controller 版额外给 `LocalColorSchemeMode` | `U/theme/MiuixTheme.kt:24-44`、`:55-72` |
| 读取：`MiuixTheme.colorScheme`/`.textStyles`/`.colorSchemeMode`/`.isDynamicColor` | `U/theme/MiuixTheme.kt:74-96` |
| **`colors=` 重载下 `colorSchemeMode==null` → `isDynamicColor=false`**（`Switch` 等走非动态默认色） | `U/theme/MiuixTheme.kt:83-96`、`U/basic/Switch.kt:230` |
| `Colors` 是 `@Stable class`，**53 个角色**（`primary/onPrimary/…/sliderBackground`），字段 `internal set`，提供公开 `copy(...)` | `U/theme/Colors.kt:68-122,230-338` |
| 现成构造器：`lightColorScheme(...)`/`darkColorScheme(...)`，53 角色全是可覆写命名参数（种色 `#FF3482FF`） | `U/theme/Colors.kt:341-450,451-618` |
| `TextStyles` 是 `@Stable class`，**14 角色**；工厂 `defaultTextStyles(...)` | `U/theme/TextStyles.kt:36-50,114-144` |
| 组件默认字色不写死：`Text(style=LocalTextStyles.current.main)`，颜色由 `onBackground` 注入 | `U/basic/Text.kt:108`、`docs/guide/textstyles.md:8` |
| 自定义亮/暗色：`ThemeController(System/Light/Dark/Monet*, lightColors=…, darkColors=…, isDark=null)` | `U/theme/ThemeController.kt:228-243,246-274`；枚举 `ColorSchemeMode` `:51` |
| **Monet 支持**：`MonetSystem/MonetLight/MonetDark`；有 `keyColor` → `colorsFromSeed`（materialKolor），无 → `platformDynamicColors()` | `U/theme/ThemeController.kt:256-274,61`；`U/theme/DynamicColors.kt:9`（`expect`） |
| Android 取色：API33+ 读 `Settings.Secure.theme_customization_overlay_packages`→ API31-32 读 `system_accent*`→ **<31 回落固定紫 `#FF6750A4`** | `A/theme/DynamicColors.android.kt:26-45`、`U/theme/ThemeController.kt:201` |
| `paletteStyle` 10 种 / `colorSpec` Spec2021/2025（Spec2025 仅 4 风格；Cmf 恒 2026） | `U/theme/ThemeController.kt:31-58`、`docs/guide/theme.md:59-66` |
| **无公开 M3 适配器**：`colorsFromSeed`、`mapMd3RolesToMiuixColorsCommon`、`MonetRoles` 均 `internal` | `U/theme/ThemeController.kt:61,201`、`U/theme/MonetMapping.kt:86,11` |
| 显示挖孔修正由 `MiuixTheme` 自动挂载（API30+），`LocalDisplayCutoutFixEnabled=false` 可关 | `U/theme/MiuixTheme.kt:28`、`U/theme/ProvideDisplayCutoutFix.kt:24`、`A/theme/ProvideDisplayCutoutFix.android.kt:30,36` |

**M3 `ColorScheme` → Miuix `Colors` 适配器（照 `MonetMapping.kt:102-156` 库内对照抄；函数本身不可调用）**

```kotlin
@Composable
fun MiuixThemeFromMaterial3(m3: ColorScheme, dark: Boolean, content: @Composable () -> Unit) {
    val colors = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = m3.primary, onPrimary = m3.onPrimary,
        primaryVariant = m3.primaryContainer, onPrimaryVariant = m3.onPrimaryContainer,
        error = m3.error, onError = m3.onError,
        errorContainer = m3.errorContainer, onErrorContainer = m3.onErrorContainer,
        secondary = m3.outlineVariant, onSecondary = m3.outline,            // MonetMapping:118-119
        secondaryVariant = m3.surfaceContainerHigh,                          // :120
        onSecondaryVariant = m3.onSurface,                                   // :121
        secondaryContainer = m3.secondaryContainer, onSecondaryContainer = m3.onSecondaryContainer,
        secondaryContainerVariant = m3.surfaceContainerHighest,              // :128
        onSecondaryContainerVariant = m3.onSurfaceVariant,                   // :129
        tertiaryContainer = m3.tertiaryContainer, onTertiaryContainer = m3.onTertiaryContainer,
        background = m3.background, onBackground = m3.onBackground,
        onBackgroundVariant = m3.primary,                                    // :135
        surface = m3.surface, onSurface = m3.onSurface, surfaceVariant = m3.surfaceVariant,
        onSurfaceSecondary = m3.onSurface.copy(alpha = 0.8f),                 // :139
        onSurfaceVariantSummary = m3.onSurfaceVariant,                        // :140
        onSurfaceVariantActions = m3.onSurfaceVariant,                        // :141
        surfaceContainer = m3.surfaceContainer, onSurfaceContainer = m3.onSurface, // :143-144
        surfaceContainerHigh = m3.surfaceContainerHigh,                      // :146
        surfaceContainerHighest = m3.surfaceContainerHighest,                // :148
        onSurfaceContainerHighest = m3.onSurface,                            // :149
        outline = m3.outline, dividerLine = m3.outlineVariant,               // :150-151
        windowDimming = Color.Black.copy(alpha = if (dark) 0.6f else 0.3f),   // :152
        sliderBackground = m3.primary.copy(alpha = 0.38f),                   // :155
    )
    MiuixTheme(colors = colors, content = content)   // 注意：此重载 isDynamicColor=false
}
```

> 只改几个角色更省事：`lightColorScheme().copy(primary = …)`（`U/theme/Colors.kt:230`）。**不要**试图 `import` 库内映射函数——`internal`（`MonetMapping.kt:86`）。禁用态 11 个角色库内是"38%/50%/60% alpha 叠 surface 取不透明"算出来的（`MonetMapping.kt:90-100`），适配器里可直接给常量。

## 4. 图标用法与混用

| 事实 | 证据 |
|---|---|
| 命名：`MiuixIcons.<Name>`（= Regular 别名）→ `MiuixIcons.Light/Normal/Regular/Medium/Demibold.<Name>` | `I/Settings.kt:15,242`、`C/icon/MiuixIcons.kt:6-13` |
| ui 内置 7 个 Basic（ArrowRight/ArrowUpDown/Check/Close/Search/SearchCleanup/Sidebar），20dp | `U/icon/basic/*.kt:14-15`、`docs/guide/icons.md:76-86` |
| 扩展图标 `defaultWidth/Height=24.dp`、viewport 1208.4，延迟构建 + 缓存 | `I/Settings.kt:22-27` |
| **全部是标准 `ImageVector`** → Material 图标可同排混用：`Icon(imageVector)`、`NavigationBarItem(icon=)`、`NavigationRailItem(icon=)` 入参均 `ImageVector` | `U/basic/Icon.kt:53`、`U/basic/NavigationBar.kt:148`、`U/basic/NavigationRail.kt:332` |
| 本库**不**打包 `material-icons`，需自行加依赖 | `miuix-ui/build.gradle.kts:60-68` |

## 5. miuix-blur（blur / Backdrop / highlight / tilt）

| API | 层 | 关键参数 | 证据 |
|---|---|---|---|
| `Modifier.textureBlur(backdrop, shape)` ×2 | 高层 | `blurRadius=20f(dp)`、`noiseCoefficient=0.0045f`、`colors`、`highlight`、`contentBlendMode=SrcOver`、`enabled` | `B/TextureEffect.kt:29,66`；默认值 `B/BlurDefaults.kt:147-160` |
| `Modifier.progressiveTextureBlur(…, gradient)` ×2 | 渐进模糊（栏/边缘首选） | `gradient: ProgressiveBlur`（Top/Bottom/Left/Right + `startFraction/endFraction/curve`）、`noiseCoefficient=0f` | `B/TextureEffect.kt:195,234`、`B/ProgressiveBlur.kt:24-41` |
| `Modifier.drawBackdrop(backdrop, shape={}, effects={}, highlight={})` | 低层全控 | `layerBlock`、`progressiveGradient`、`enabled`（内部 `&& isRuntimeShaderSupported()`） | `B/DrawBackdropModifier.kt:103-118` |
| `BackdropEffectScope.blur/progressiveBlur/noiseDither/blendColors/colorControls/effect/runtimeShaderEffect` | effect 块（**半径单位 px**） | `padding`、`downscaleFactor`、`noiseCoefficient` | `B/BackdropEffects.kt:46,126,245,259,389,418`、`B/BackdropEffectScope.kt:63,102,122` |
| `Backdrop` / `LayerBackdrop` / `rememberLayerBackdrop(onDraw)` / `Modifier.layerBackdrop` | 捕获源 | `graphicsLayer`、`onDraw: ContentDrawScope.() -> Unit` | `B/Backdrop.kt:14-53`、`B/LayerBackdrop.kt:41,58`、`B/LayerBackdropModifier.kt:22` |
| `Highlight(width, alpha, style)` + 6 预设（GlassStroke Big/Middle/Small × Light/Dark）+ `BloomStroke`/`LightSource`/`LightPosition` | 玻璃高光边 | `width=0.8.dp`、`alpha=1f`、`innerBlurRadius=2.8.dp` | `B/highlight/Highlight.kt:24-58`、`B/highlight/HighlightStyle.kt:83-130,165-240` |
| `rememberTiltLight(basePosition, color, intensity, sensitivity)` | 传感器视差光 | `sensitivity=0.1f` | `B/highlight/TiltLight.kt:25` |
| `rememberDeviceTilt(smoothing=0.15f)` | 倾斜状态（Android 读 `TYPE_GAME_ROTATION_VECTOR`；非 Android 恒 `Zero`） | `DeviceTilt(pitch, roll, gravityX, gravityY)` | `B/sensor/DeviceTilt.kt:22,44`、`miuix-blur/src/androidMain/…/DeviceTilt.android.kt:22-98`、`…/DeviceTilt.skiko.kt:12` |

**Android 门槛与 <31 降级**

| 行为 | 证据 |
|---|---|
| `miuix-blur` 模块**自身 minSdk=33** | `miuix-blur/build.gradle.kts:31`、`docs/guide/blur.md:6-11` |
| `RuntimeShader` 仅 API33+；`isRuntimeShaderSupported()` 即该判定 | `miuix-shader/src/androidMain/…/RuntimeShader.android.kt:18` |
| `isRenderEffectSupported()` 仅 API31+（只覆盖 RenderEffect 链路） | `miuix-shader/src/androidMain/…/RenderEffect.android.kt:12` |
| 不支持时：`drawBackdrop` **按普通内容绘制**；`blur`/`colorControls`/`blendColors`/`runtimeShaderEffect` **静默 return**；`Highlight` **静默跳过** | `B/DrawBackdropModifier.kt:118`、`B/BackdropEffects.kt:47,64,124,260`、`docs/guide/blur.md:516-518` |
| `layerBackdrop` 只捕获自身内容、不含父背景 → 透明区色彩外溢，需 `onDraw` 里先画不透明底 | `docs/guide/blur.md:105-127`、`B/LayerBackdrop.kt:41` |
| 渐进模糊每帧多一遍全分辨率 overlay，**大面板慎用** | `docs/guide/blur.md:285-291` |

## 6. miuix-squircle（卡片/头像/按钮圆角）

| API | 语义 | 关键参数 | 证据 |
|---|---|---|---|
| `Modifier.squircleBackground(color, cornerRadius[, extension])` ×2 | 只填充，**不裁子级** | `extension=1.1f`（1.0=圆弧，1.1=连续角，钳在 [1,2]） | `S/SquircleBackground.kt:51,69`、`S/SquirclePath.kt:14-24` |
| `Modifier.squircleSurface(color, …)` ×2 | 填充+裁子级（卡片首选） | 同上 | `S/SquircleBackground.kt:134,152` |
| `Modifier.squircleClip(…)` ×2 | 仅裁剪 | 同上 | `S/SquircleBackground.kt:96,112` |
| `Modifier.squircleBorder(width, color, cornerRadius)` ×2 | 描边（**路径式，无需 shader，更便宜**） | `extension` | `S/SquircleBorder.kt:30,80` |
| `absoluteSquircleBackground/Clip/Surface` | 物理角（不受 RTL 翻转） | `topLeft/topRight/bottomRight/bottomLeft` | `S/SquircleBackground.kt:182,209,233` |
| `Path.addSquircleRect(w,h,r,extension,squircleEnabled)` | 低层路径（逐帧遮罩/拆帘） | 需手动传 `squircleEnabled` | `S/SquirclePath.kt:49-55` |
| `LocalSquircleEnabled=true` / `isSquircleEnabled()`（= 开关 && `isRuntimeShaderSupported()`） | 全局 opt-out | — | `S/LocalSquircleEnabled.kt:18,27`、`S/SquircleBackground.kt:370` |
| **<API33 自动退回 `RoundedCornerShape`/`Modifier.border`，调用点零分支** | 无崩溃 | — | `S/SquircleBackground.kt:78,120,161,191,217,242`、`docs/guide/squircle.md:22-27` |
| 圆形头像：只有矩形语义，把 `cornerRadius` 设成半边即圆（内部按 `halfMin` 截断） | — | — | `S/SquirclePath.kt:74-76` |

## 7. miuix-nav：是否采用？

| 类型/函数 | 说明 | 证据 |
|---|---|---|
| `interface NavKey` | 纯标记接口；持久化时**强制 `@Serializable`**（否则首帧抛 `SerializationException`） | `N/core/NavKey.kt:26,21-24`；saver `N/core/NavBackStack.kt:30-55` |
| `typealias NavBackStack` / `navBackStackOf` / `rememberNavBackStack<T>` | 栈就是 `SnapshotStateList`，可直接 `add/removeLastOrNull` | `N/core/NavBackStack.kt:23,28,69` |
| `NavController` / `rememberNavController<T>` | `push/pop/replace/popUntil`；不持额外状态 | `N/core/NavController.kt:19-52,70` |
| `NavDisplay(backStack\|navController, onBack, transition, effects, content)` | `content` 是 `entry<T>{}` DSL；**空栈 `require` 抛错** | `N/core/NavDisplay.kt:974,1005,1045`；DSL `:133` |
| `NavTransitions.MiuixDefault/Modal/None` | 全宽滑入+1/4 视差 / 底部上滑 / 无动画 | `N/transition/NavTransitions.kt:28,43,66,79` |
| `navGraphicsTransition{relativeDepth…}` / `NavDisplayEffects(cornerClipRadius, cornerClipMode, dimAmount=0.5f, blockInputDuringTransition, backdropColor)` | 自造转场（读 float 深度，deferred 不重组）；圆角/scrim 是正交层 | `N/transition/NavTransition.kt:131`、`N/core/NavDisplayEffects.kt:63-69` |
| `NavSwipeDirection`（默认 None）/ `navDirectionalTransition` / `NavMotion(commit/cancel/programmatic)` | 侧滑关闭需**逐路由 opt-in** | `N/transition/NavTransition.kt:24,81,133`、`N/transition/NavMotion.kt:20` |
| `rememberNavSystemCornerRadius()` | Android 读 `RoundedCorner`（API31+）→ dimen → 0.dp | `N/core/NavSystemCornerRadius.kt:18`、`androidMain/…/NavSystemCornerRadius.android.kt:30` |
| **v1 明确不做**：overlay destination、shared-element、KSP 路由、result channel | `docs/guide/miuix-nav.md:340-360` |

**建议（聊天客户端）**：**不采用**。(1) 收益仅"连续深度转场 + 预测性返回 + iOS 侧滑"，而我们的根导航是 `when(phase)` 三屏 + 会话栈在 ViewModel（见 `06-bqv-ui.md` §1.3），换 `NavDisplay` 要求路由改成 `@Serializable` 的 `NavKey` 层级并重建 lifecycle/ViewModel 归属（`N/core/NavKey.kt:21-24`、`docs/guide/miuix-nav.md:305-330`）——纯重构、不加功能；(2) 额外拖入 `navigationevent 1.2.0`+`lifecycle 2.11.0`+`kotlinx-serialization-json`+`collections-immutable`（`miuix-nav/build.gradle.kts:56-64`）；(3) v1 无 result channel，"从选人群页返回会话"仍需自建 result 总线（`docs/guide/miuix-nav.md:330-338`）。**若要 iOS 手感，只借旁路工具** `Modifier.pagerGestureOverride`（`U/utils/PagerGestureUtils.kt:169,201,223`），不必引整个 nav。

## 8. 已知限制 / Android 注意点

| 限制 | 值 / 表现 | 证据 |
|---|---|---|
| Compose Multiplatform | **1.12.2**（用到 `Modifier.dropShadow`、`LocalOverscrollFactory`、`TextAutoSize` 等新面） | `gradle/libs.versions.toml:10`、`README.md:8`、`U/basic/Snackbar.kt:495-506`、`U/basic/Text.kt:90` |
| Kotlin | **2.4.21**（`kotlin.reflect.KClass` 无父类遍历 → `entry{}` 必须逐具体类型注册） | `gradle/libs.versions.toml:3`、`README.md:7`、`N/core/NavDisplay.kt:171-177` |
| 全库 minSdk | **24** | `build-logic/src/main/kotlin/BuildConfig.kt:14` |
| `miuix-blur` minSdk | **33**（模块级硬常量） | `miuix-blur/build.gradle.kts:31` |
| shader 生效线 | API33（RuntimeShader）；RenderEffect 线 API31 | `miuix-shader/src/androidMain/…/RuntimeShader.android.kt:18`、`RenderEffect.android.kt:12` |
| Monet 系统取色线 | API31+（33+ 才读 seed/style/spec JSON）；<31 固定基线紫 | `A/theme/DynamicColors.android.kt:26-45`、`U/theme/ThemeController.kt:201` |
| API26-27 焦点回收 bug（SearchBar 收不起来） | `hasFocusReassignBug = SDK_INT <= O_MR1`，库内已"禁 TextField+pointerInput"绕开 | `miuix-core/src/androidMain/…/Utils.android.kt:24`、`U/basic/SearchBar.kt:225-232` |
| 显示挖孔修正仅 API30+ | `MiuixTheme` 内自动执行，`LocalDisplayCutoutFixEnabled` 可关 | `A/theme/ProvideDisplayCutoutFix.android.kt:36`、`U/theme/ProvideDisplayCutoutFix.kt:24` |
| Overlay* 依赖 `Scaffold.popupHost` | 不在 Miuix `Scaffold` 内**不渲染**（不报错） | `docs/components/overlaydialog.md:5-16` |
| `PullToRefresh` 仅触屏环境 | 桌面/网页无效 | `docs/components/pulltorefresh.md:6` |
| ScrollBar 是唯一 opt-in API | `@RequiresOptIn(WARNING)`，官方称 "known issues" | `U/interfaces/ExperimentalScrollBarApi.kt:13-23` |
| `miuix-window` 无 Android 目标 | 桌面窗口换肤 | `miuix-window/build.gradle.kts:20` |
| 模块成熟度 | README 首段自述 experimental；全库 `@Since` grep=0，**无法给出 since-version** | `README.md:5` |
| KMP-only 坑 | 无：`platformDynamicColors`、`rememberDeviceTilt`、`ProvideDisplayCutoutFix` 都有 Android `actual`；只有 `miuix-window` 整模块 Desktop-only | `A/theme/DynamicColors.android.kt:26`、`miuix-blur/src/androidMain/…/DeviceTilt.android.kt:22`、`A/theme/ProvideDisplayCutoutFix.android.kt:29` |

**落地口径**：(1) 先确认我方 CMP/Kotlin 能抬到 1.12.2/2.4.21，否则整套方案不成立；(2) minSdk 保持现值即可——blur/squircle 玻璃在 API33+ 自动开、以下**静默降级为普通色块/圆角**，不崩不裂（`B/DrawBackdropModifier.kt:118`、`S/SquircleBackground.kt:78`）；(3) 主题别去"接" M3 `ColorScheme`（函数 internal），用 §3 的 `copy(...)` 适配器；Monet 只在用户开动态取色时用 `ThemeController(keyColor=)`，否则 <31 会掉基线紫（`U/theme/ThemeController.kt:201`）；(4) 必须自写的最小面：Toast、骨架屏、带内容槽的分段控件、圆形头像——共 4 个 composable。

