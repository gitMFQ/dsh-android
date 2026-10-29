# 06 · fork 基线 `bqv/dsh-android` 的 UI 层盘点

> 基线 `/tmp/dsh-android-prior`，tag **v0.6.6**（`bd0848a "Cut 0.6.6"`），MPL-2.0，Kotlin + Jetpack Compose。`app/src/main/java/uk/xa0/dsh/` 下文简写为 **`S/`**；所有 `path:line` 均为该 checkout 实测。
> 规模：main **140 kt / 58 128 行**，test **127 kt / 22 746 行**，合计 267 文件 / 80 874 行；`ui/` **54 文件 / 27 107 行**（占 main 46.6%）。注意 README 里 "342 tests" 已过时，实际 **1 116 个 @Test**。

---

## 1. 架构

### 1.1 包布局

| 包 | 文件 | 行数 | 职责 | Android 依赖 |
|---|---|---|---|---|
| `S/ui`（根） | 18 | 13 111 | 三屏 Chat/Setup/Startup + 抽屉 + Sheets + Settings + Terminal/Trajectory/Router | 18/18 Compose |
| `S/ui/components` | 28 | 11 688 | 聊天行、composer、工具卡、面板、基础件 | 28/28 Compose，4 个 import android |
| `S/ui/composer` | 2 | 267 | 纯 Kotlin：草稿回填、`/`/`@` 触发检测 | 0 |
| `S/ui/markdown` | 2 | 1 105 | `Markdown.kt` 591 纯解析 + `MarkdownView.kt` 514 渲染 | 解析器 0 |
| `S/ui/{scroll,search,settings,theme}` | 4 | 936 | 尾随滚动、搜索行、本地设置事实、**唯一主题文件** | 2/4 |
| `S/model` | 47 | 10 948 | 传输无关投影：Transcript reducer / Trajectory / SessionFiles / Stats / Router | 47 中仅 2 |
| `S/net` | 14 | 4 031 | `DshClient`(1155) / `RemoteMux`(575) / `EndpointResolver`(673) / `CertTrust`(273) | 3/14 |
| `S/term` | 7 | 2 485 | VT 核心 `TerminalEmulator`(971)、键盘码表、会话门 | **0** |
| `S/widget` | 6 | 1 475 | 桌面卡：快照 / 文案规则 / RemoteViews | 3/6 |
| `S/data` | 2 | 418 | 加密配置、草稿 | 2/2 |
| `S/diag` | 3 | 728 | 滚动诊断埋点（无 UI） | 2/3 |
| `S/`（根） | 7 | 10 936 | `DshViewModel` **9 055**、`MainActivity` 301、`Attention*`、Service、Application | 7/7 |

### 1.2 状态归属：`DshViewModel.kt`（9 055 行，占 main 15.6%）

- 单一 `AndroidViewModel`（`S/DshViewModel.kt:1-20` import `android.app.Application`），持 **19 个 StateFlow**：`ui`(1240)、`entries`(1247)、`live`(1249)、`todos`(1251)、`header`(1253)、`endedTurns`(1256)、`closingSeqs`(1267)、`branchSeqs`(1277)、`turnDurations`(1280)、`turnStartedAt`(1283)、`loadingHistory`(1460)、`directory*`(1470-1477)、`workspaceExplorer`(1494)、`terminal`(1519)、`references`(5151)、`commands`(8315)。
- `data class UiState`（**`:701-1107`**）**87 字段**，扁平快照：phase/busy/error、sessions/workspaces/archived、sessionSearch*、currentSessionId/pendingSession、models/modelSelection、permission/plan/context、stats/balance/schedule*、jobs/queue/approvals/questions。
- **267 个 fun**（含 private）：连接（`connect`:4371、`attemptConnectNow`:4603）、终端（`enterTerminal`:1587 → `onTerminalFrame`:2313）、审批/提问（`resolveApproval`:4003、`answerQuestions`:3997）、发送/队列（`send`:7444、`dispatchSend`:7511）、模型（`selectModel`:7949）、工作区（`addWorkspace`:5796）、附件（`addAttachment`:8473）、图片（`imageBitmap`:5272、`markdownImageBytes`:8838）。
- **无 `import androidx.compose`、无 `import uk.xa0.dsh.ui`** → ViewModel 不认识 Compose，UI 单向消费。

### 1.3 屏幕装配 `MainActivity.kt`（301 行）

| 位置 | 事实 |
|---|---|
| `:64-65` | `setContent { val vm: DshViewModel = viewModel(); val ui by vm.ui.collectAsStateWithLifecycle() }` |
| `:118-119` | `CompositionLocalProvider(LocalImageBytes provides vm::markdownImageBytes) { DshTheme(themeMode = ui.themeMode) { … } }` |
| `:131-156` | 唯一"路由"：`when (ui.phase)` → `StartupScreen` / `SetupScreen` / `ChatScreen(vm)` |
| `:125` | `BackGate(onClose = { finish() })` 在所有屏幕之前组合 |
| `:175-190` | `systemGestureExclusionRects`：左缘 48dp × 200dp 留给抽屉手势 |
| `:203-265` | Intent 消费：通知深链、分享文件、`Provisioning` |

### 1.4 导航模型：**没有 NavHost**

`androidx.navigation:navigation-compose:2.7.7` 在 `app/build.gradle.kts:126` 声明，但 main 与 test **零 import**（`grep -rn 'androidx.navigation' app/src` 只命中该 gradle 行）。实际是 4 层叠加：

1. **Phase 枚举** `AppPhase { LOADING, SETUP, READY }`（`S/DshViewModel.kt:205`）决定顶层屏幕。
2. **抽屉**：`ModalNavigationDrawer` + `ModalDrawerSheet`（`ui/ChatScreen.kt:796,815`），宽 `DRAWER_WIDTH = 300.dp`（`:2714`），自写 `drawerDrag`（`:2749`，commit 阈值 40dp / 200dp·s⁻¹）。
3. **Sheet 模态**：11 个 `ModalBottomSheet`（`ui/Sheets.kt:109,207,276,467,631,761` + Automation / Balance / Jobs / WorkspaceBrowser）+ 7 个 `AlertDialog`；全部由 `ChatScreen` 的 `var show*` 持有，**同时只允许一个**（否则"两层 scrim 吃掉第一次返回"）。
4. **页内视图**：`ChatView { CHAT, TRAJECTORY, TERMINAL("Shell"), ROUTER }`（`ui/ChatScreen.kt:2258`），`ViewTabs`（:2297）；Router 页按当前模型路由条件出现。

返回键汇总到 `BackGate`（`ui/BackGate.kt:42`，二次确认）。

### 1.5 纯 Kotlin（可测） vs Compose-bound

| 层 | 判定 | 证据 |
|---|---|---|
| `term/` 7 文件 | **完全 Android-free** | `grep 'import android' term/` = 0；186 个 @Test 打 `TerminalEmulator`(69)、`TerminalInput`、`TerminalProtocol` |
| `model/` 47 文件 | 逻辑纯 Kotlin，仅 2 文件沾 Android | `ChatModels.kt:4` / `TurnProcess.kt:4` 是 `androidx.compose.runtime.Immutable`（编译期注解）；`TurnProcess.kt:3,183` 一处 `android.util.Log.w` |
| `ui/markdown/Markdown.kt` | **纯 Kotlin 解析器** | 591 行零 import android/compose；`:40 object Markdown`、`:488 object SyntaxHighlighter`；`MarkdownInlineTest`/`MarkdownHighlightTest` |
| `ui/composer/*`、`ui/scroll/TailFollow`、`ui/settings/*` | 仅 Compose 注解，无平台 API | `ComposerTriggers.kt:3-4` 只 import `TextFieldBuffer`/`TextRange` |
| `ui/**` 其余 50 文件 | **Compose-bound** | 50/54 有 `import androidx.compose`；51 有 `import android`（Context / Intent / Uri / Bitmap / LruCache） |
| `ui/TerminalInputView.kt` | 例外：**非 Compose**，原生 View | 416 行，`:3-12` import `android.view.*`、`BaseInputConnection`、`InputMethodManager` |

---

## 2. UI 清单（file | 角色 | LOC | restyle 影响 | 备注）

影响分级：**S** = 只换 token；**M** = 换外形、仍内联在同文件；**L** = 结构/交互需重写。

### 2.1 `ui/` 根（18 文件 · 13 111 行）

| file | 角色 | LOC | 影响 | 备注 |
|---|---|---|---|---|
| `ChatScreen.kt` | 唯一主屏：Drawer + Header + Tabs + Transcript + Dock + Sheets | 3267 | **L** | 20 个 @Composable、57 处裸 dp；Drawer `:796`、LazyColumn `:1241` |
| `SessionsDrawer.kt` | 抽屉：搜索/分组/树/行菜单/页脚 | 2283 | **L** | `:271`，13 @Composable，`MAX_TREE_DEPTH`=4（:2259） |
| `SettingsContent.kt` | 设置分区 + 可复用行件 | 1136 | M | 25 @Composable；`SettingsRow`:818 / `SettingsGroup`:841 / `SettingsSelector`:926 / `SettingsStepper`:1002 |
| `Sheets.kt` | 6 sheet 入口 + ContextDialog | 968 | **L** | `SettingsSheet`:73 / `AboutSheet`:158 / `ModelSheet`:249 / `WorkspaceSheet`:455 / `PermissionSheet`:621 / `AgentPresetSheet`:752 |
| `AutomationSheet.kt` | 定时任务 sheet | 899 | M | 14 @Composable；`FilterTab`/`TaskRow`/`StatusChip` |
| `TerminalScreen.kt` | Shell 页：**单 Canvas 网格**（非逐 cell composable） | 837 | M | `:102`，kdoc 明写 "No WebView and no xterm.js"；`:788` 是唯一漏网的 `Color(0x…)`（ANSI RGB） |
| `AboutScreen.kt` | 关于页 + `ForceQuitDialog` | 735 | S | 静态文案 + `InfoRow`/`SupportRow` |
| `TrajectoryScreen.kt` | 轨迹时间线（Canvas 时间条） | 601 | M | `:83`；Canvas `:270`、`timelineSpanColor`:343 |
| `RouterTab.kt` | llama.cpp router 诊读 | 491 | M | `:79`，只读事实表 |
| `TerminalInputView.kt` | IME 桥（原生 View） | 416 | — | `handleTerminalKeyEvent`:310、`render`:409；视觉不可换 |
| `SetupScreen.kt` | 首次配置（URL/账号/证书） | 250 | M | `:49`，单列表单 |
| `Modifiers.kt` | `clickableNoRipple`:22 / `rememberTouchGate`:60 / `pinchCellSize`:202 | 247 | S | 无主题耦合 |
| `BalanceSheet.kt` | 余额 sheet | 217 | M | `:55` |
| `WorkspaceDragGesture.kt` | 拖拽几何（纯函数） | 231 | — | `isOutsideDragBounds`:227 |
| `SubagentRollup.kt` | 子代理汇总（纯） | 198 | — | `indexSubagentRollups`:82 |
| `ModelTriggerState.kt` | 模型触发器文案（纯） | 155 | — | `modelTriggerState`:37 |
| `BackGate.kt` | 返回二次确认 | 93 | S | `:42`，AlertDialog |
| `StartupScreen.kt` | 启动页 | 87 | S | `:54` |

### 2.2 `ui/components`（28 文件 · 11 688 行）

| file | 角色 | LOC | 影响 | 备注 |
|---|---|---|---|---|
| `ChatRows.kt` | **全部聊天行**：User/Assistant/Live/Reasoning/ToolCall/ToolCallTree/Todo/Notice/Approval/Goal/TurnProcess | 1852 | **L** | 24 @Composable；`UserMessageRow`:135、`AssistantMessageRow`:637、`ToolCallRow`:776、`ApprovalCard`:1412；66 处裸 dp（全仓最多） |
| `FilesPanel.kt` | 文件面板：浏览/预览/SVG/下载 | 1369 | **L** | `:167`；`SvgPreview`:1300（androidsvg 渲染，非 WebView） |
| `Composer.kt` | 输入卡 + 工具栏 | 969 | **L** | `:151`，12 @Composable，见 §3.4 |
| `JobsCard.kt` | 任务座 + sheet + 输出面板 | 808 | M | `JobsSeat`:79 / `JobsSheet`:148 / `JobOutputPanel`:596 |
| `QueueDock.kt` | 待发队列坞 | 640 | M | `:125` |
| `SessionStatsPills.kt` | 统计胶囊 + 展开面板 | 491 | M | `:103`；`StatPillButton`:284 |
| `WorkspaceBrowser.kt` | 目录浏览模态 | 477 | M | `:77` |
| `SearchRow.kt` | grep/glob 工具卡 | 429 | M | `SearchCallRow`:184 |
| `AskQuestionRow.kt` | 已答提问转录行 | 420 | M | `AskQuestionCallRow`:259 |
| `BashRow.kt` | shell 调用终端卡 | 407 | M | `BashCallRow`:151 |
| `DiffRow.kt` | 补丁卡（LCS 折叠） | 401 | M | `diffCardModel`:116 |
| `ReadRow.kt` | 读文件卡 | 379 | M | `ReadCallRow`:200 |
| `DeliverableRow.kt` | 产物 chips + 交付卡 | 368 | M | `:78` |
| `CommandMenu.kt` | composer `/` 与 `@` 双菜单 | 339 | M | `CommandMenu`:157 / `ReferenceMenu`:252 |
| `DshInput.kt` | **基础件**：`DshMark`:65 / `DshWordmark`:91 / `DshTextField`:108 / `DshPrimaryButton`:285 | 308 | M | 无 M3 TextField，自绘 hairline 输入框 |
| `QuestionCard.kt` | 待答提问卡（阻塞式） | 292 | M | `:70` |
| `WebRow.kt` | web 检索/抓取卡 | 270 | M | `WebCallRow`:117 |
| `ReadImageRow.kt` | 图片读取卡 | 269 | S | `ReadImageCallRow`:107 |
| `FileDownloadControl.kt` | 下载按钮 + 进度 | 230 | S | `:83` |
| `PlanRow.kt` | `todo_write` 摘要行 | 149 | S | `PlanCallRow`:77 |
| `CertificateSection.kt` | 证书信任块 | 147 | S | `:43` |
| `StateDot.kt` | 5 态状态点（ONGOING 8 格追逐） | 136 | S | `Canvas`:98，自绘动画 |
| `ComposerImeUndo.kt` | IME undo/redo 桥 | 128 | — | 非视觉 |
| `TodoGlyph.kt` | 待办三态字形（14×14 画板） | 123 | S | `Canvas`:78/100/112 |
| `DshIcons.kt` | 自有 SVG path 图标 | 114 | S | `:22`；其余用 material-icons-extended |
| `SubagentReadOnlyComposer.kt` | 只读占位（替换 composer） | 78 | S | `:40` |
| `DshKeyboardOptions.kt` | 键盘契约（纯） | 49 | — | `:29` |
| `ComposerShortcuts.kt` | Ctrl+Z/Y 映射（纯） | 46 | — | `:34` |

### 2.3 子包（8 文件 · 2 108 行）

| file | 角色 | LOC | 影响 | 备注 |
|---|---|---|---|---|
| `markdown/Markdown.kt` | 流式容忍解析 + 语法高亮（**纯 Kotlin**） | 591 | — | `:40 parse`、`:488 SyntaxHighlighter` |
| `markdown/MarkdownView.kt` | 块级/行内渲染、代码块、图片 | 514 | **L** | `:113 MarkdownText`、`:408 CodeBlock`、`:341 LocalImageBytes` |
| `theme/DshTheme.kt` | **唯一 token 文件**：`DshColors`(50 色) / `DshSpacing` / `DshRadius` / `DshType` / `DshTheme` | 360 | **L（核心）** | `:33` / `:212` / `:224` / `:294` / `:343` |
| `search/SessionSearchRows.kt` | 搜索行 + 文案（逐字对齐 Web） | 274 | M | `SessionSearchCopy` |
| `scroll/TailFollow.kt` | 顶锚列表尾随（替代 reverseLayout） | 234 | — | `:60` |
| `composer/ComposerTriggers.kt` | 光标精确 `/`/`@` 检测（纯） | 200 | — | `:68` |
| `settings/SettingsLocalFacts.kt` | 本地凭据/cookie 事实 | 68 | — | `:40` |
| `composer/ComposerDraft.kt` | 发送失败回填规则（纯） | 67 | — | `:36` |

---

## 3. 设计耦合热点

### 3.1 颜色：收敛得很好，只有一个漏点

- 全仓 `Color(0x…)` 共 **101 处，100 处在** `ui/theme/DshTheme.kt`（Dark:103-155、Light:157-209）；唯一例外 `ui/TerminalScreen.kt:788`（ANSI RGB 解析，合理）。
- `DshColors` 是 Web 端 `--dsw-*` CSS 变量的**逐字复刻**（:25-27 注释："名字镜像 CSS 自定义属性，而不是 Material 角色"），50 个 `Color` 字段 + 7 个语法高亮色。→ **换肤 = 换这一个 data class 的实例**，不动任何行。
- 注释里写死了两个陷阱：`brand`/`ink` **不是蓝**，蓝是 `accent`；assistant 消息**没有气泡**，只有 user 用 `bubble`。

### 3.2 尺寸：token 与字面量并存

| 指标 | 数值 |
|---|---|
| `DshSpacing`/`DshRadius` 引用 | 896 处 |
| 裸 `N.dp` 字面量 | **521 处**（ChatRows 66 / ChatScreen 55 / Composer 38 / SessionsDrawer 36 / Trajectory 26 / JobsCard 23） |
| `DshRadius` 档位 | sm 6 / md 8 / lg 10 / card 12 / thumb 16 / bubble 22 / panel 24 / pill 999 |
| `DshSpacing` 档位 | 2 / 4 / 6 / 8 / 12 / 16 / 24 / 32 dp（2dp 网格） |

→ 圆角从 12/22 改到 16–20dp 必须**同时**改 token 和约 60 处 `RoundedCornerShape(…)` 直接调用（ChatRows 16 / SettingsContent 11 / JobsCard 11 / SessionsDrawer 10 / FilesPanel 10）。

### 3.3 Material3 用量极轻（换肤之利）

| M3 组件 | import 次数 | 说明 |
|---|---|---|
| `Text` / `Icon` | 37 / 27 | 仅当排版原语 |
| `TextButton` | 7 | 对话框按钮 |
| `AlertDialog` | 6 | 确认/危险操作 |
| `ModalBottomSheet`(+state) | 5 + 5 | 所有 sheet |
| `Switch` / `DropdownMenu(Item)` | 2 / 2+2 | 抽屉过滤、行菜单 |
| `ModalNavigationDrawer`/`ModalDrawerSheet`/`DrawerState` | 各 1 | 抽屉 |
| `Scaffold` / `TopAppBar` / `Card` / `Surface` / `Button` / `OutlinedTextField` | **0（全仓 grep 无命中）** | 见下 |

即**没有 Scaffold、TopAppBar、M3 Card/Button**；所有容器都是 `Box/Column/Row + .clip() + .background() + .border(0.5.dp)` 自绘，`DshTextField`/`DshPrimaryButton` 自建于 `ui/components/DshInput.kt:108,285`。→ 没有 Material 组件的内部样式需要覆盖。

### 3.4 Composer 布局（`ui/components/Composer.kt:151-396`）

- 外层 `Column(fillMaxWidth)` → 卡片 `Column(clip = RoundedCornerShape(DshRadius.bubble)=22dp, bg = inputMajor, border = 0.5dp borderL2, padding top = 10dp)`（`:189-207`）。
- 卡片内三段：`AttachmentRail`(`:893`，有附件才画) → `Box(heightIn(max = 160dp))`（占位文本 + `BasicTextField(state = TextFieldState)` + `ComposerImeUndo`）→ 工具栏 Row（`ModelTrigger`:607 / `ModeChip`:661 / `PlanChip`:700 / `ContextMeter`:735 / `SendButton`:398）。
- **显式无 minHeight**（`:225` 注释：min 会让占位文本与字段文本错位）；`10dp` 是刻意的"半档"（`:196-206`）。
- dock 装配在 `ui/ChatScreen.kt` ~1490-1620：`QueueDock` → `ComposerPopups`（`/`/`@` 菜单锚在卡片上沿）→ `Composer` **或** `SubagentReadOnlyComposer`（同槽替换）→ `SessionStatsPills`（最后一项，自带 `bottom = DshSpacing.sm` 到导航条）；左右统一 `DshSpacing.xl`=16dp。
- **与浮动底栏的冲突**：`LazyColumn` 的 `contentPadding.bottom = DshSpacing.md`（`ChatScreen.kt:1247`），Column 用 `navigationBarsPadding().imePadding()`（`:923-926`）。引入 56–64dp 浮动底栏时，输入区须抬高 `bar + 8dp`，否则遮挡。

### 3.5 聊天行布局（`ui/components/ChatRows.kt`）

| 行 | 关键布局 |
|---|---|
| `UserMessageRow`:135 | `Column(horizontalAlignment = End)`；气泡 `widthIn(max = 320dp)` + `clip(bubble 22dp)` + `bg(bubble / tip)` + `padding(h = 16dp, v = 10dp)`；`alpha(0.7f)` 表 pending；附件先于文本（Web 顺序）；时间戳挂 `MessageActions`:432（`padding(end = 34.dp)` 避开右侧轮次轨） |
| `AssistantMessageRow`:637 | **无气泡**，纯正文 + footer（时长/分支） |
| `ToolCallRow`:776 | 工具 IN/OUT 双段卡；子调用由 `ToolCallTreeRow`:1070 承接 |
| `ApprovalCard`:1412 / `QuestionCard`（`components/QuestionCard.kt:70`） | 停靠在 composer 上方的**阻塞式**卡（`ChatScreen` ~1419-1445） |
| `GoalBar`:1607 / `TodoCard`:1168 / `NoticeRow`:1279 / `TurnStatusRow`:568 | 行内状态条 |

- 列表：`LazyColumn` 顶锚（`ChatScreen.kt:1241`），`contentPadding` 16dp、`spacedBy(16dp)`、每行 `widthIn(max = 920.dp)`，**每行一个 `SelectionContainer`**（跨 item 选择会掉端点）；滚动交由 `ui/scroll/TailFollow.kt:60`，不用 `reverseLayout`。
- 折叠逻辑在 `model/TurnProcess.kt:79 buildDisplayRows`（**纯函数**）；UI 只是 `when(row)` 分派（`ChatScreen.kt:1274-1322`）。

### 3.6 Markdown：**原生 Compose，不是 WebView**

| 事实 | 证据 |
|---|---|
| 无 WebView | 全 ui 目录仅 3 处提到 WebView，且都是注释里的"不用它"（`MarkdownView.kt:67`、`TerminalScreen.kt:85`、`AboutScreen.kt:250`） |
| 解析器纯 Kotlin | `Markdown.kt:40`，输出 `List<MdBlock>`（Paragraph/Heading/Code/BulletList/NumberedList/Table/Quote/Rule）；流式容错：未闭合 fence 吞尾、表格等 `|---|` 分隔行才成立 |
| 渲染 | `MarkdownText`:113 → `ClickableText` + `AnnotatedString`；`CodeBlock`:408 + `highlightCode`:474；图片经 `LocalImageBytes`(:341) 注入的 `suspend (String) -> ByteArray?` |
| 性能 | `LruCache<String, List<MdBlock>>(48)` + `INLINE_PARSE_CHARS = 4096` 以上异步解析（`MarkdownView.kt:88-93`）；`ParseCostTest` 实测 2.4KB = 1.5ms、18KB = 3.9ms |
| 高亮 | 自研 `SyntaxHighlighter`（`Markdown.kt:488`），7 个 token 类 → `DshColors.syn*` |

### 3.7 其他硬耦合

- `DshTheme.kt:320-335` 把 token 映射成 M3 `ColorScheme`（只映射 `primary/background/surface/surfaceVariant/outline/error`），M3 组件配色走它。
- `res/values/colors.xml` 另有一套 **widget 专用暗色**（`widget_background #F2151517` 等），`Theme.DSH` 用 `android:Theme.Material.NoActionBar` + `windowBackground = #0B0F14`，与 Compose `bgBase #151517` **不一致** → 换肤后首帧会闪。
- 版式仍有 `fontSize = 13.sp / 16.sp` 直写（如 `ChatScreen.kt:2327 ViewTabs`）。

---

## 4. 状态 vs 视图分离：什么能纯换皮

**结论：整套视觉可以换，协议 / 数据 / 归约一行不用动。** 证据：

1. **ViewModel 零 Compose 依赖**（无 `import androidx.compose`、无 `import uk.xa0.dsh.ui`），只产 `UiState` / `ChatEntry` / `DisplayRow` 与回调。
2. **组件签名全是"数据 + lambda"**：`Composer(state, onSend, onStop, running, modelTrigger, …)`（`Composer.kt:151`）、`SessionStatsPills(stats, onBalance, rates, family)`（`:103`）、`SessionsDrawer(sessions, workspaces, …, onSelect, onNew, …)`（`:271`）。行渲染只读 `ChatEntry`。
3. **行类型是纯模型**：`sealed interface ChatEntry`（`model/ChatModels.kt:49`，`@Immutable`）、`sealed interface DisplayRow { Single, TurnProcess, Live }`（`model/TurnProcess.kt:21-56`），`buildDisplayRows`(:79) 为纯函数，由 437 个 model @Test 保护。
4. **可换皮面**：`theme/DshTheme.kt`（token）、`ChatScreen` 外框（Drawer / Header / Tabs / LazyColumn 参数）、`SessionsDrawer` 行与分组、`SettingsContent` 行件、`Sheets.kt` 容器、`Composer` 卡片、`ChatRows` 各行外壳、`MarkdownView.kt` 块外观。
5. **不可碰面**：`model/*`、`net/*`、`term/*`、`TranscriptReducer`、`TailFollow` 的滚动不变量、`TerminalScreen` 的 Canvas 网格（性能约束）、`TerminalInputView`（IME）。
6. **测试即护栏**：`ui` 190 + `scroll` 25 个 @Test（Robolectric 真布局）会直接抓到"换皮改坏布局/滚动"；换皮时它们是净收益。

---

## 5. 构建事实

| 项 | 值 | 位置 |
|---|---|---|
| AGP / Kotlin | 8.5.2 / 1.9.24 | `build.gradle.kts:2-3` |
| compileSdk / buildTools | 34 / 35.0.0 | `app/build.gradle.kts:8,32` |
| minSdk / targetSdk | **26 / 34** | `:36-37` |
| namespace / applicationId | `uk.xa0.dsh`（debug 加 `.debug`） | `:7,35,95` |
| versionCode / versionName | 15 / 0.6.6 | `:38-39` |
| JVM | 17（source/target/jvmTarget）+ `-opt-in FlowPreview` | `:75-85` |
| Compose | BOM **2024.09.00**、compiler ext **1.5.14** | `:110,70` |
| 依赖 | core-ktx / activity-compose / lifecycle / material3 / material-icons-extended / **navigation-compose 2.7.7（未使用，可删）** / security-crypto / okhttp 4.12 / androidsvg 1.4 / coroutines 1.8.1 | `:112-141` |
| 测试依赖 | junit 4.13.2 / **org.json:20240303**（盖住 android.jar 的 Stub!）/ robolectric 4.13 / compose ui-test-junit4 | `:145-150` |
| 构建入口 | `build.sh`：`JAVA_HOME=.toolchain/jdk-17.0.20.1+1`、`gradle-8.7`、`ANDROID_HOME=/opt/android-sdk`、三个 home 全指 `.toolchain/`、`flock .build.lock` 串行 | `build.sh:1-42` |
| 无 wrapper | 无 `gradlew`；无 `local.properties`（CI 生成）；`android.builder.sdkDownload=false` | `gradle.properties` |
| 测试隔离 | `user.home` 重定向到 `.toolchain/robolectric-home`（Robolectric 锁文件不可写会直接失败） | `app/build.gradle.kts:56-67` |
| CI Build | push main/PR → `testDebugUnitTest` + `assembleDebug` → artifact `dsh-android-debug-<sha>`；现场铺 `.toolchain/` | `.github/workflows/build.yml` |
| CI Release | `v*` tag → `assembleRelease` → 非 `-unsigned` 校验 → GitHub Release（正文取 `docs/releases/<tag>.md`） | `.github/workflows/release.yml` |
| 签名 | 环境变量 `SIGNING_STORE_FILE/_PASSWORD/_KEY_ALIAS/_KEY_PASSWORD`，**缺省回退 debug key**；CI 用 `RELEASE_KEYSTORE_BASE64` 等 | `app/build.gradle.kts:11-26` |
| 测试规模 | **127 文件 / 1 116 @Test**（README:63 的 342 已过时）；47 文件用 Robolectric 或 `createComposeRule` | `app/src/test` |

**测试分布（@Test 数）**：`model` 437 · `ui` 190 · `term` 186 · `widget` 78 · `net` 77 · 根包 69 · `scroll` 25 · `ui.markdown` 19 · `diag` 13 · `ui.components` 12 · `ui.composer` 10。→ 换皮的主护栏是 `model`+`ui`；**`theme` 零测试**，新皮肤须自建快照/语义测试。

### 5.1 改名/换包时必须同步的 10 处

| # | 位置 | 内容 |
|---|---|---|
| 1 | 267 个 `.kt` 的 `package` 行 + **1 053 个 `import uk.xa0`** | 目录 `app/src/{main,test}/java/uk/xa0/dsh/` 需一并移动 |
| 2 | `app/build.gradle.kts:7` `namespace` / `:35` `applicationId` | 决定 `R` 与 `BuildConfig` 包名 |
| 3 | `app/src/main/AndroidManifest.xml` | `.MainActivity`/`.DshApplication`/`.DshConnectionService`/`.widget.DshWidgetProvider` 皆为相对名，`package` 由 `namespace` 注入 |
| 4 | `S/Attention.kt:42` | `EXTRA_SESSION = "uk.xa0.dsh.session"` —— intent extra 字符串，**IDE 重构不会改** |
| 5 | `S/MainActivity.kt:283` kdoc | `adb shell am start -n uk.xa0.dsh.debug/…` 示例命令 |
| 6 | `res/values/strings.xml:7` + `docs/*.md`（HANDOFF / SCROLL-DIAG / WIDGET / research 2 篇） | 注释与文档 |
| 7 | `.github/workflows/build.yml:118`、`release.yml:7,148` | 文案与 release 正文断言 |
| 8 | `README.md` 多处 + `tools/install.sh` | 需核查是否硬编码包名 |
| 9 | 依赖窗口 | 可删 `navigation-compose`；若引 Miuix 须先解 BOM 兼容（现 2024.09.00 比 DESIGN.md 假设老） |
| 10 | `versionCode/versionName`、`settings.gradle.kts` 的 `rootProject.name = "DSH"` | 品牌换名 |

> 成本：**267 文件 / 1 053 import 的机械替换 + 3 个 XML/工作流手改**；无反射、唯一字符串硬编码即第 4 项，风险低，IDE Move/Refactor 可覆盖约 90%。

---

## 6. Restyle plan（换成 KernelSU 风格视觉）

前置（对齐 `docs/DESIGN.md` §2）：**只重写视觉语言，不复制 GPL 的 `.kt`**；语义色层（`DshColors`）保留，Miuix / Monet 只管外观。

### 6.1 顺序（每步可独立出包）

| 步 | 触碰文件（按改动顺序） | 动作 | 验收 |
|---|---|---|---|
| **D0 建基线** | `app/build.gradle.kts` + 新增 `ui/theme/DshSemantic.kt`、`DshSkin.kt` | `DshColors` 降级为**语义层**（accent/success/warn/error/label* 保留），新增皮肤层（surface 阶梯 / 圆角 / 阴影 / 模糊）；旧 `DshTheme` 留作 `WebSkin` 对照 | 编译通过；`theme` 快照测试落地 |
| **D1 token 换血** | `ui/theme/DshTheme.kt:33-233` | 圆角 card 12→16、bubble 22→20；spacing 8→12；阴影与 1dp 描边分离；`DshType` 正文 14→15sp、行高 24→26、代码 11→13sp | 全局观感变，零结构改动（S 级文件自动跟随） |
| **D2 外框换装** | `ui/ChatScreen.kt:796-950` + 新增 `ui/components/FloatingBottomBar.kt` | 借 Scaffold 语义（不引 M3 Scaffold）：折叠 TopBar + 浮动胶囊底栏（Chat/Trajectory/Files/Settings，`ChatView` 降为页内 tab）；输入区抬高 `bar + 8dp`；下滚自动隐藏 | 三页可切；宽屏切 `NavigationRail`；`imePadding` 不破 |
| **D3 抽屉与搜索** | `ui/SessionsDrawer.kt:271-1000` + `ui/components/DshInput.kt:108` | 抽屉换卡片行 + `SuperSearchBar` 式搜索栏（替换现 `DshTextField(minHeight = 40)` 用法，`:956-978`）；`ProjectRow`:1640 / `SessionRow`:1887 行高 ≥48dp | 分组/排序/归档/树全通；`SessionsDrawer*Test` 6 组全绿 |
| **D4 设置行件** | `ui/SettingsContent.kt:818-1114`、`ui/Sheets.kt` | `SettingsRow/Group/Selector/Stepper/ChoiceChip` → `SettingsItem` + `TonalCard` 骨架（`SectionHeading`:766 保留） | 4 组设置视觉对齐；settings 写路径不变 |
| **D5 弹层统一** | `ui/Sheets.kt`（6 处 `ModalBottomSheet`）、`ui/BackGate.kt:42`、`ui/AutomationSheet.kt`、`ui/BalanceSheet.kt`、`ui/components/JobsCard.kt:148` | 统一 `DshSheet`/`DshDialog` 皮（scale 0.92→1.0 + fade 180ms）；危险操作红卡 + 长按触觉 | 审批/提问默认 Sheet；返回栈语义不变 |
| **D6 行内微调** | `ui/components/ChatRows.kt`（**仅外壳**）、`StateDot.kt`、`TodoGlyph.kt` | 气泡圆角/内边距、工具卡 `TonalCard` 化、状态点尺寸；**不动** `buildDisplayRows` / `ChatEntry` | 语义色与 Web 一致；`ApprovalCardTest`/`StatsPillsAnchorTest` 绿 |
| **D7 动效** | `ui/ChatScreen.kt`（切页）、`ui/markdown/MarkdownView.kt:113` | 底栏切页 spring（dampingRatio 0.75 / stiffness 400）；流式仅在段落边界 120ms 淡入 | 无逐 token 重排 |

### 6.2 应替换为 KernelSU 式组件的部分 vs 保留

| 区域 | 替换为 | 现有落点 |
|---|---|---|
| 浮动底栏 | `FloatingBottomBar`（胶囊高 56–64dp、圆角 = 高/2、模糊 + 1dp 描边） | 新增；`ViewTabs`（`ChatScreen.kt:2297`）降为页内 strip |
| 搜索栏 | `SuperSearchBar`（替换自绘 `DshTextField` 作搜索） | `SessionsDrawer.kt:956` + `SessionSearchRows` |
| 设置列表 | `SegmentedList` + `SettingsItem` + `TonalCard` | `SettingsContent.kt:818-1054` |
| 弹层 | 统一 `Dialog`(Scale) + `WarningCard` | `Sheets.kt` 11 个 sheet + 7 个 AlertDialog |
| 顶栏 | 折叠 TopAppBar + 状态胶囊 | `ChatHeader`（`ChatScreen.kt:2089`）、`ConnectionPill`(`:3114`） |
| 工具卡 | `TonalCard` 折叠行（保留信息密度） | `ChatRows.kt:776` 及 `BashRow / DiffRow / ReadRow / SearchRow / WebRow` |

**保留（不替换）**：
- `ui/markdown/*` **保持原生 Compose** —— 参考项目用 WebView + asset pipeline，会抬内存峰值并破坏流式；若公式/复杂表格确有需要，只对单块降级。
- `ui/scroll/TailFollow.kt`（滚动不变量由 25 个测试守护）、`model/*`、`net/*`、`term/*`、`TerminalScreen` 的 Canvas 网格、`TerminalInputView` 的 IME 桥。
- `SessionsDrawer` 自写 `drawerDrag`（40dp commit + 左缘 200dp 手势区）—— 视觉可换，**手势几何不动**（与 `MainActivity.kt:175-190` 配套）。

### 6.3 风险

| 风险 | 对策 |
|---|---|
| `theme` 零测试，换皮无护栏 | D0 先补语义快照测试，再靠 `ui` 190 + `scroll` 25 个 Robolectric 测试兜底 |
| 521 处裸 dp 让"只改 token"不彻底 | 优先 `DshRadius`/`DshSpacing`；字面量按 §3.2 清单（ChatRows / ChatScreen / Composer 前三）逐处收编 |
| 首帧闪色（XML `#0B0F14` vs Compose `#151517`） | 同步改 `res/values/colors.xml` 的 `dsh_window_bg`；AMOLED 需第 3 套值 |
| 浮动底栏遮挡输入 | 抬高 = bar 高 + 8dp；重算 `imePadding` 与 `navigationBarsPadding`（`ChatScreen.kt:923-926`） |
| 依赖漂移 | 现 BOM 2024.09.00 比 DESIGN.md 假设老：引 Miuix 前先升 BOM 并单分支验证，或走 DESIGN.md 方案 B（纯自绘、零新依赖） |
| MPL-2.0 传染 | 改过的 `ui/**` 源文件须继续以 MPL 提供；新皮肤文件可另许可 |
