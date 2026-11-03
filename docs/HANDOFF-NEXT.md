# 交接文档（给新会话）

> 写于 **2026-11-03 傍晚**（#50 合入之后），本版为 **第九版**。
> 沿革：一版「接管 + 工具链全量升级」→ 二版「结构拆分」→ 三版「1.2b」→ 四版「阶段 1 完成」
> → 五版「阶段 2.5 完成 + 2.6 清单」→ 六版「2.6 被 3.0 取代」→
> 七版「输入框/底栏三轮重做 + 一次 RPC 取消崩溃修复之后」→
> 八版「3.5b：Me 的八个设置页签变成真页面」→
> **本版「七连 PR：深色基线 / Files 工具栏 / 状态胶囊 / Plugins 真页 / i18n 起步 / 玻璃顶栏 / 换装 sheet」**。
>
> **新会话先读本文件**，再读 [PHASE-3-PLAN.md](PHASE-3-PLAN.md)。注意：该计划的 §4 PR 表
> **没有记录第 1–3 轮输入框改造**（那是计划写成之后按用户真机反馈做的），当前外壳状态以本文件为准。

## 1. 一句话现状

仓库 <https://github.com/gitMFQ/dsh-android>（私有）是整仓 GPL-3.0 的 **DSH 原生安卓客户端**。
阶段 1、阶段 2.0–2.5、阶段 3 的 3.0–3.8 全部合入。3.5b（Me 的设置页签）之后，第八版列出的六项
一次做完（PR #44–#50），当前 master `e11afca`。

**CI 实测（master `e11afca`）**：`tests=1239 failures=0` / `screenshots=26` / Debug APK 绿。
本机 aarch64 只能编译 Kotlin 和跑 `:design`/`:core:protocol` 测试，**测试真值以 CI 为准**。

## 2. 已合入（master）

| PR | 内容 |
|---|---|
| #25 | 许可转 GPL-3.0 + `NOTICE.md` + 接 `miuix-blur`（`tools:overrideLibrary`，minSdk 仍 26）+ 玻璃基座 `ui/glass/` |
| #26 | 底栏改 4 目的地（Chat / Files / Plugins / Me），会话三视图降为页内药丸 |
| #27 | 移植套件玻璃底栏（8 文件）替换手写底栏 |
| #28 | Me / Me 子页 / Plugins 三条基线 |
| #30 | 修 CI：阿里云镜像不再排在官方源前面 |
| #29 | 3.2b：backdrop 布线，液态档真正可达 |
| #31 | 3.5a：移植 `overlay/` `list/` `feedback/` |
| #38 | **输入框第 1 轮**：输入框变成底栏另一半——收起 `[导航栏][圆按钮]`、展开 `[当前页圆][消息条]`；控件搬成药丸行（`+`/权限/计划/模型/上下文）；新增 `liquidPane` |
| #39 | **输入框第 2 轮**：默认改为**消息条展开**、导航折成当前页圆；`+` 进输入条；药丸行加**速度 / Cache** 两颗读数、权限/模型只留图标；To-dos/回到底部玻璃化；**修对话底部被底栏挡住**（栈高度量在 padding 内侧）；**修流式输出"一会抬一下"**（TailFollow 对 padding 边缘修正） |
| #40 | **第 3 轮**：长按菜单加**全屏输入**；点铅笔/圆图标不弹键盘；turn rail 可跳转；收起态导航**拉伸填满**；**系统导航栏透明 + 对话滑到其后**；**抽屉跟手**、底栏不响应抽屉；修长按放大玻璃被裁；**液态档去描边**；排队消息条玻璃化；会话统计速度药丸常驻；**RPC 取消竞态闪退修复**；`localbuild.sh` 锁修复 |
| #42 | **3.5b**：Me 的八个页签变真页面；`SettingsContent.kt` 的段落提权成 `ui/settings/` 的卡片，**sheet 与页签共用同一套卡片**；Me 页行分组卡片化；`MeSubPage` 换真返回箭头 + trailing 槽；**Terminal 页签接管 shell 字号** |
| #43 | 两个闪退：To-dos 卡片的液态玻璃（在 pager 内采样自己）+ 设置下拉的 `NavigationEventDispatcher` 缺失（`androidx.activity` 1.9→1.12）；顺带 **Crash log 页**（debug 写 `files/diag/crash-*.txt`，Me 里可 Copy/Share/Delete） |
| #44 | **F1 深色基线**：`capture()` 的 `themeMode` 变成参数，补 `drawer-dark` / `chat-transcript-dark` / `me-tab-dark` / `me-tab-amoled` 四张；20→24 |
| #45 | **Files 页 A3**：目录工具栏（Up / Copy path / Name·Size·Recent 循环）+ 名称过滤 + 两个不同的空态；`ExplorerOrder` 与过滤在 `:core:model`，8 条测试 |
| #46 | **状态胶囊 + Plugins 真页**：`StatusCapsules`（`:core:model`，13 条测试）排出「等待 > 离线 > 运行 > 空闲(不画) > 未知」；Plugins 接 `pluginInventory/list`，`PluginInventoryWire` 16 条测试 |
| #47 | 录 `plugins-tab`（占位页已被替换）+ 新增 `plugins-tab-empty`；24→26 |
| #48 | **S2 玻璃顶栏**：`ChatHeader` 走 `liquidPane`；新增 `flatFill` 缝隙使平档逐字节不变 |
| #49 | **F2 i18n 起步**：Plugins 页文案进 `values/strings.xml`（含一条 `<plurals>`）+ `values-zh-rCN`；首个可翻译屏 |
| #50 | **D1 起步**：`DshSheet`（`:design` 里包 `WindowBottomSheet`，**window 级，不需要 miuix Scaffold**），`PermissionSheet` 换容器、内容不动 |
| #51 | 第九版交接文档（本文件） |
| #52 | **D1 完成（sheet 部分）**：其余 **11 处** `ModalBottomSheet` 全部换 `DshSheet`；`sheetState` 纯透传、无人回读，所以删掉没有行为变化；12 处的 `containerColor` 都是 `bgBase` |
| #54 | **3.9 完成（dialog 部分）**：7 处 `AlertDialog` 全换；`ConfirmDialogVisuals` 补 `confirmEnabled` + `confirmTone`（kit 原来把每个 confirm 都画成 accent，会把「Force quit」这类销毁动作也染成品牌色） |

## 3. 当前外壳长什么样（接手 UI 必读）

### 3.1 输入框 / 底栏

- 默认状态：**消息条展开**，底栏左侧一个当前页圆图标；点圆图标 → 消息条收成圆形铅笔、四项导航回来；
  再点铅笔 → 展开。
- 收起态：导航条**拉伸到整行宽**（16dp 外边距），四格分宽度；铅笔圆独立在右。
- 行宽不变：`[圆 56][间隔 8][消息条 余量]`；导航折叠用 `requiredWidth` + **只在折叠动画中裁切**，
  静止不裁（否则长按放大会被裁掉）。
- 输入条内：`+`、文字、发送/停止、附件条；**dock 从不主动弹键盘**，只有点文字框才弹。
- 药丸行（始终可见）：权限（盾牌图标）、计划（仅在计划模式）、模型（脑图标）、上下文（环+%）、
  **会话统计（恒显示 `xx tok/s`，无 decode 窗口时保留上次数值、首帧 0）**、**Token 用量（`Cache xx%`）**。
  点速度/缓存各自开原来的完整弹层（含余额入口）。
- 抽屉里的「会话统计 / Token 用量」旧 footer 行已删除，读数并入药丸行——**这是流式不跳的关键**：
  药丸行固定高度，数字只改宽度。

相关文件：

| 文件 | 作用 |
|---|---|
| `app/ui/components/Composer.kt`（570 行） | `ComposerContent`：`+`、文本、附件、发送/停止、IME undo、自动换行上限 120dp；选择工具栏接入点 |
| `app/ui/components/ComposerDock.kt`（488） | `ComposerDock`：导航折叠/展开、宽度动画、点击/滑动手势、输入条容器；`InputPane` / `NavigationPane` |
| `app/ui/components/ComposerAccessories.kt`（409） | 药丸行 `ComposerAccessoryRail` + `GlassPill` + 上下文环/弹层 |
| `app/ui/components/SessionStatsPills.kt` | 速度 / Cache 药丸与完整弹层（原 `SessionStatsPills` 行已删） |
| `app/ui/components/ComposerFullScreen.kt`（370） | 自定义 `LocalTextToolbar`（全选/复制/剪切/粘贴 + **Full screen**）、`FullScreenComposer` 全屏写作面 |
| `design/ui/glass/LiquidPane.kt`（100） | `Modifier.liquidPane`：液态 = vibrancy + blur 4dp + lens 24dp + 无描边；平档 = `glassSurface`（有 0.5dp 描边）；`glassLift` 由调用方加 |

### 3.2 边到边与系统栏

- `ChatPanel` 根布局**不再** `navigationBarsPadding()`：pager 跑到窗口底，对话可从透明系统导航栏后滑过。
- dock 自己付导航栏 inset（`WindowInsets.navigationBars.exclude(WindowInsets.ime)`，键盘打开不叠加）；
  Files/Plugins/Me 页各自补导航栏 inset。
- `SystemBars`：导航栏颜色透明、API 29+ 关掉 `isNavigationBarContrastEnforced`；图标明暗仍跟调色板。
- `onSizeChanged` 量在 dock 两处 inset **外侧**：`dockAreaPx` = 窗口底到 dock 顶的完整距离，
  所有要避让 dock 的地方（聊天栈、hero、回到底部按钮）都用它一次，不要再加第二个 gap。

### 3.3 抽屉 / turn rail / 列表跟随

- `drawerDrag`：Initial pass 抢占边缘手势，**逐帧把位移喂给 Drawer 的 anchored offset**
  （`DrawerState` 的 internal `anchoredDraggableState`，局部 `@Suppress`；松手仍按 40dp/200dp/s settle）。
  顶栏右滑仍走 Material 根手势；**起手在 dock 区的向下手势在 Main pass、底栏孩子之后被消费**，
  所以底栏不拉抽屉、底栏自己的 tab 拖动不受影响。
- `TurnRail`：标准 `detectTapGestures` + 瞬时 `scrollToItem`；旧手写观察器的 `isConsumed` 判断会把 tap 吞掉。
- `TailFollow`：尾部增长按**padding 后的内容边缘**（`viewportEndOffset - afterContentPadding`）修正，
  每行只补它溢出的像素；列表底部内边距 = `md + composerStackPx`，而 `composerStackPx`
  现在量的是「卡片 + dock 区 + gap」的完整占位（量在 padding 外侧）。有 `TailFollowMathTest` 4 条算术测试钉住。

### 3.4 崩溃修复（#40 的协议部分）

设备报告：`DshRpcException("the host did not answer…")` at `DshClient.kt:1024`，
`StandaloneCoroutine{Cancelling}` + suppressed `DiagnosticCoroutineContextException`。
根因：`JobSupport.getFinalRootCause` 取第一个非 CancellationException，取消与判决同时到达时 RPC 异常
成为已取消 job 的完成原因 → 线程未捕获处理器 → 进程崩。

- `DshClient`：`rpcRaw`/`rpcBytes` 的 wire 工作收进 `rawCall`/`rawBytesCall`，catch 后经
  `rpcFailureForCaller(failure, coroutineActive)`：active → 原样抛；已取消 → `CancellationException`
  （判决作 cause）；已有取消不再包。`ensureActive()` 只留作廉价早退。
- `DshViewModel`：用 `CoroutineScope(SupervisorJob() + Main.immediate + CoroutineExceptionHandler)`（`uiScope`）
  替代 `viewModelScope`（71 处 launch，同一 `onCleared` 取消点）。已失效 job 的异常 debug 丢弃；
  仍 active 的 error 日志 + 现有错误横幅。
- 新增 `core/protocol/src/test/.../RpcCancellationTest.kt` 6 条。

### 3.5 Me 页与设置（3.5b 之后）

- Me 页 = 身份头 + **四张分组卡片**（`ME_GROUPS`：This device / What answers /
  Rare and dangerous / Facts），行是 `SegmentedColumn`（`design/.../ui/list/`，miuix `BasicComponent` 装进 Card）。
- 八个页签**都是真页面**：`ui/settings/MeSettingsPage(id, state, actions)` 按 id 分发。
  **About 是唯一例外**：它开 About **sheet**，不是页面（那是关于 host 的文档，不是设置；
  塞进页里会同时出现第二个 Close 和返回箭头）。
- **sheet 与页签共用一套卡片**：`ui/settings/SettingsCards.kt` 每个主题一个 card，
  `SettingsContent.kt` 只剩 web 的段落**顺序**（1157 行 → 约 180 行）。
  改一行设置只需改一处，两个界面同时生效——这是 3.5b 的全部目的。
- `SettingsSheet(state, actions, onDismiss)` 与页签拿同一对 holder（`MeSettingsState` /
  `MeSettingsActions`，在 `ui/settings/MeSettings.kt`），所以两者不可能对
  host 写入规则产生分歧。
- **host 写入规则原样搬过来**：`writerFor` 在字段缺失 / `applies != live` / secret /
  provider 不可写时**不给控件**；被拒的写入显示 host 自己的措辞。
- **Terminal 页签是新增状态**：shell 字号以前只能在 shell 里改，现在
  `ChatUiState.terminalFontSp` + `ChatActions.setTerminalFont` 走同一个
  `DshViewModel.setTerminalFontSp`。
- **`MeSubPage` 的返回是左箭头**（原来是右向 chevron 配 "Back"，读起来像"继续往里走"），
  并多了 trailing 槽，放 **All settings**（完整文档仍是 sheet）。
- **页签内的 Back 先退页签**：handler 注册在 `MeDestination`（状态在那里），
  所以它赢过"退回 Chat"。

| 文件 | 作用 |
|---|---|
| `ui/settings/MeSettings.kt` | `MeSettingsState` / `MeSettingsActions` + `MeSettingsPage(id)` 分发 |
| `ui/settings/SettingsCards.kt` | 九个主题卡片 + `writerFor` + `LocalSettingNoteInset` |
| `ui/SettingsContent.kt` | sheet 的段落顺序（约 180 行），不再是第二份实现 |
| `ui/ShellTabs.kt` | `MeTab` 分组卡片、`ME_GROUPS`、`MeSubPage` |
| `ui/ChatPanel.kt` | `MeDestination`：页签状态 + BackHandler + About 跳 sheet |

> **`SegmentedColumn` 的卡片现在用 `codeBlock` + 0.5dp `borderL1`**，不是 `bgLayer1`：
> 本 app 的浅色调色板里 `bgBase`/`bgLayer1`/`bgLayer2`/`bgLayer3` **全是纯白**，
> 用 `bgLayer1` 填充的卡片在白底上完全看不见（第一次录基线就是这样暴露的）。
> 3.5a 移植它之后一直没人用过，所以这个洞直到 Me 页才露出来。

> **基线截图里必须关掉 miuix 的 squircle 着色器**：`Card` 用 AGSL runtime shader 画
> 圆角，`isRuntimeShaderSupported()` 只是 `SDK_INT >= 33`，Robolectric 答"有"，
> 然后把它交给自己的 native 编译器 → `IllegalArgumentException: 'color' is not a valid
> layout qualifier`，**整个屏幕在 composition 阶段就崩**。两个基线类都 provide
> `LocalSquircleEnabled = false`（miuix 自带的降级开关，回退到 `RoundedCornerShape`）。
### 3.6 本版新增的四处表面（读代码前先知道这些）

| 表面 | 文件 | 要点 |
|---|---|---|
| **状态胶囊** | `:core:model/StatusCapsule.kt` + `ChatPanel.kt` 的 `StatusCapsuleChip` | 顶栏 trailing **一颗**，按「等待 > 离线 > 运行 > 空闲(不画) > 未知」排名。**只有离线态可点**（开 About 的 Restart connection），等待态故意不可点——那张卡已经浮在对话上（#43） |
| **Plugins 页** | `ui/PluginsPage.kt` + `:core:model/PluginInventory.kt` + `DshViewModel.loadPluginInventory` | 只读接 `pluginInventory/list`；排序 **Failed → Active → Idle → Disabled**（枚举声明顺序即排序顺序）；`enabled` 与 `fiberPhase` **分开画**，两者不合并成一个灯 |
| **Files 工具栏** | `FilesPanel.kt` 的 `ExplorerPane` + `:core:model/WorkspaceExplorer.kt` | Up / Copy path / 排序循环 / 名称过滤；目录在每个排序里都排最前；`RECENT` 是**转录的 seq**、不是 mtime（host 不报 mtime） |
| **`DshSheet`** | `design/.../ui/overlay/Sheet.kt` | 包的是 miuix **`WindowBottomSheet`**（window 级），**不是** `OverlayBottomSheet`（后者要 `MiuixPopupUtils` 的 popup host / miuix Scaffold）。**全 app 12 处 sheet 已全用它**，`ModalBottomSheet` 归零 |

> **`liquidPane` 新增 `flatFill` 参数**：材质面板在平档退化时用 `glassSurface`（填充 + 四边 0.5dp 描边），
> 那对**浮动**面板是对的；但顶栏是**齐平**页面的全宽条，四边描边会变成屏幕上下左右的一道框。
> 传 `flatFill = Color.Transparent` 表示「这里本来什么都没有」，平档就逐字节不变——**这是 S2 能落地的前提**。
> 实测证据：不加这个参数时 `chat-transcript` 动了 932 像素，全在 y=0..57 的顶栏带，颜色 `#FFFFFF→#E0E0E0`。

> **没有任何基线包含对话框** —— 所以 #54 里 `confirmTone` 这类「颜色被换掉」的回归，基线**抓不到**。
> 给一张确认框基线（销毁动作 + 两个按钮）是下一步最划算的测试投资。

> **i18n 只覆盖了 Plugins 一页**。`values-zh-rCN` 是证明链路通了（真机切中文这一页就是中文，不需要 App 内切换、不需要 appcompat backport，因为本 app 没有任何 locale override）。**其余约 1100 条仍是英文硬编码**，别把「一页可翻译」当成「已翻译」。

## 4. 下一步（建议顺序）

### 4.1 先做真机验收（第 1–3 轮 + 3.5b 的账还没结）

截图基线都是 **FLAT 档**（Robolectric 无 GPU），液态档、手势、系统栏透明、IME 都只有真机能验。
建议按下面清单过一遍，把问题记成下一轮的输入：

1. **API 33+ 液态档**：药丸/输入条/To-dos/排队条/回到底部按钮的 blur+lens 是否与底栏同一观感；**没有描边**；
   长按底栏 tab 的放大玻璃能画出容器、不被裁。
2. **键盘**：点铅笔 / 当前页圆图标不弹键盘；点文字框才弹；全屏进入时弹键盘、返回收回。
3. **全屏**：长按输入框 → 菜单有 Full screen；进去是同一个草稿（含附件），发送/关闭正常。
4. **turn rail**：点刻度能跳到对应轮次（不是预览一下又弹回底部）。
5. **收起态**：导航条填满整行（16dp 边距），四格文字不被裁；点 tab 的高亮/放大正常。
6. **边到边**：对话能滑到透明系统导航栏后面并透出来；dock 浮在系统栏上方；键盘弹出时 dock 不被
   叠加两层 inset（gap 不应明显变大）。
7. **抽屉**：对话页左缘右滑**跟手**；顶栏右滑仍跟手；底栏右滑**不**拉抽屉；抽屉打开时在 sheet 上
   左拖跟手关。
8. **排队消息条**：繁忙时入队，条是液态玻璃、四角圆角，不再有旧的开口描边。
9. **读数药丸**：速度常驻（新会话首帧 `0 tok/s`，流式中数字变化、不闪烁消失）；`Cache xx%` 点开完整面板。
10. **闪退**：反复切会话/退出页面/重连（尤其弱网时网关返回 HTML），进程不应再被 RPC 取消竞态带走；
    若出现真正的未处理异常，应看到错误横幅而不是闪退。
11. **3.5b 设置页签**：Me → 每个页签都进得去、有内容、Back 先退页签（不是直接回 Chat）；
    **All settings** 打开完整 sheet；**Terminal 页签改字号后 Shell 页立刻变**；
    Appearance 七档能切；只读部署里 General 的控件是死的、有说明。

### 4.2 然后回到 PHASE-3-PLAN 的未完成项

| # | 内容 | 备注 |
|---|---|---|
| ~~3.6~~ | ~~Files 页 → 类 VSCode 树~~ | **本版只做了 A3（工具栏/排序/过滤），没建树**（用户选的）。真树仍待做 |
| 3.7 | TopBar 折叠：**随页变，不随滚动** | 见 PHASE-3-PLAN §6；**仍未做** |
| ~~3.8~~ | ~~状态胶囊~~ | ✅ #46 |
| ~~3.9~~ | ~~Sheet/Dialog 换装~~ | ✅ **全完成**（#50 + #52 + #54）：`ModalBottomSheet` 与 `AlertDialog` 在本仓归零。**两种机制并存是刻意的**：`ConfirmDialogHost` 的 body 是 String，所以「重命名」（要输入框）和「上下文窗口」（要条形图）走 `DshDialog` 自绘 |
| 3.10 | 抽屉换装 | **未做，且是另一类改动**：搜索框换 `SuperSearchBar` 是**交互变化**（展开/随滚动收起），不是换容器；抽屉还有自绘拖拽手势，会和第二个可滚动区域抢 |

> **为什么 sheets 没一次换完**：34 处 `ModalBottomSheet` 调用点里不少是惰性列表（`ModelSheet`、
> `AgentPresetSheet`），换容器会连带改内边距与滚动。一次一张、每次只看一张基线，比一次 34 处更容易定位。
> **判据就是 `permission` 那张**：只它动 = 只改了它。

### 4.3 已知隐患 / 可选的收尾

- **`ComposeFoundationFlags.isNewContextMenuEnabled = false` 是全局固定 legacy 文本菜单**（在
  `DshApplication.onCreate`），因为全屏入口要挂在 `LocalTextToolbar` 上，而 targetSdk 35+ 的新右键菜单
  绕过这个接缝。等 Compose 提供可追加自定义项的新菜单 API，应迁移并删掉这行（否则所有输入框都用旧菜单）。
- **`DrawerState.anchoredDraggableState` 是 Material3 internal**：靠局部 `@Suppress` + 编译期把关；
  升 material3 若编不过，退路是自绘抽屉（`ModalDrawerSheet` 内容可复用）。
- **抽屉在开/关动画途中被重新抓住**：目前直接 `dispatchRawDelta`，可能与 settle 动画抢同一个 offset；
  真机若看到抖动，做法是在手势开始时先让当前动画停下（mutex/snap 处理）。
- **性能**：液态档同时有 7 个玻璃面（导航、输入条、5 颗药丸）各自 blur+lens；中端机若掉帧，
  优先把药丸降为 `glassSurface`（平档），而不是降底栏。
- **< 360dp 屏**：收起态 `[四格导航 + 铅笔圆]` 在 320dp 上会溢出（当前按 360+ 设计）。
- **全屏入口只在输入条工具栏**；全屏界面自身仍用平台 legacy 工具栏（不需要再嵌全屏）。
- **`docs/BUILD-AND-CI.md` 的 §0.0 仍准确**：本机 aarch64 不能跑 aapt2，`:app` 测试源码编不了；
  `:core:protocol` 测试要用本地 init script 跳过 resource-link（仅本地，不改仓库）。

## 5. 关键约束（别再踩）

| 约束 | 原因 |
|---|---|
| **本机不能完整构建** | aarch64 + x86_64 aapt2。`assembleDebug` 会拖到 15 分钟以上且大概率 OOM（3.9 GB）。**打包/测试/截图全部走 CI**。 |
| **本机能编译 Kotlin** | `tools/localbuild.sh . :app:compileDebugKotlin`（本轮实测 5–9 分钟）。`:app` 测试源码仍编不了。 |
| **`localbuild.sh` 现在会把 flock 保持到 Gradle 退出** | #40 修过：原来在 `exec gradle` 前 `exec 9>&-`，实际提前释放锁，两个 agent 的构建互相覆盖 `buildToolsVersion`（报 "Failed to find Build Tools revision 37.0.0"）。仍建议一次只跑一个构建。 |
| **杀 gradle 会留下 `*.btbak`** | `localbuild.sh` 用 `trap` 还原 `buildToolsVersion`，被 kill 时 trap 跑不到。收尾：删 `*.build.gradle.kts.btbak` 并 `git checkout --` 那 5 个 `build.gradle.kts`。 |
| **不要 `git stash -u` / `git clean -fd`** | 会收走队友/其他 agent 的未跟踪产出。切分支可以（`checkout -b` 携带未跟踪文件）。 |
| **跨模块 smart cast 编不过** | 先把属性读进局部变量再判断；别加 `!!`。 |
| **AGP 9 不允许 `org.jetbrains.kotlin.android`** | 内置 Kotlin，应用该插件直接 build fail。 |
| **新增测试任务要写进 `verify.yml` / `build.yml`** | 依赖一个模块不会跑它的测试，CI 会「绿着丢测试」。 |
| **`tests` 与 `screenshots` 是两个数** | CI 分开打印，不要合并引用。当前 `tests=1191` / `screenshots=20`。 |
| **基线里必须关 squircle** | miuix `Card` 用 AGSL shader 画圆角，Robolectric 会在 composition 阶段崩（`'color' is not a valid layout qualifier`）。两个基线类都 provide `LocalSquircleEnabled = false`；**新增基线类也要加**。 |
| **换装不得动平档基线** | 给某表面加材质时，`liquidPane` 要传 `flatFill`（见 §3.6）。平档动了就是缺陷，不是预期差异——#48 第一次跑就是这么被抓回来的 |
| **换装 PR 要写清「哪几张预期动」** | 跑完对着 `Screenshot gate` 的失败清单核对：**多动一张就是误伤**。#48/#50 都靠这条当场抓到问题 |
| **基线录制是一轮 CI，不是一条命令** | `gh workflow run screenshots.yml` → `gh run download <id> -n roborazzi-baselines` → 覆盖 `app/src/test/screenshots/` → 提交 → 再等 Verify。覆盖后 `git status` 里**只该出现预期的那几张** |
| **`SegmentedColumn` 的卡片用 `codeBlock` 不用 `bgLayer1`** | 浅色调色板里 `bgLayer1 == bgBase == 纯白`，卡片会看不见。 |
| **基线只手动录** | `gh workflow run screenshots.yml --ref <分支> -f branch=<分支>`；下载 `roborazzi-baselines` 覆盖 `app/src/test/screenshots/` 后提交。本机录不了。 |
| **`pluginManagement` 必须是 settings 第一个语句** | 所以 `DSH_LOCAL_MIRRORS` 在两个块里各读一次。 |
| **镜像顺序** | 默认官方源在前；`localbuild.sh` 才用 `DSH_LOCAL_MIRRORS=1` 把阿里云排前。 |

## 6. CI

| 工作流 | 触发 | 作用 |
|---|---|---|
| `verify.yml` | push master + PR | JVM tests + 截图像素比对（`tests=` + `screenshots=`） |
| `build.yml` | 同上 | 出 debug APK（artifact 名 `dsh-android-debug-<sha>`） |
| `release.yml` | tag `v*` | release APK |
| `screenshots.yml` | 手动 dispatch | 在 CI 录基线并上传 artifact |

一轮 Verify ≈ 6–8 分钟，Build ≈ 9 分钟。PR 合入流程：push 分支 → 开 PR → verify 会因基线差异红 →
dispatch screenshots → 下载覆盖基线 → 提交 → verify 绿 → merge（历史 PR 都由同一账号合并）。

## 7. 仓库结构（只列本轮相关）

```
app/                 主模块（1175 项功能测试里的 app 部分；19 张截图基线）
  src/main/java/uk/xa0/dsh/
    ui/ChatPanel.kt       3840 行，外壳 + 聊天树 + 底栏/抽屉/rail 手势（最大的一块）
    ui/settings/
      MeSettings.kt（MeSettingsState/Actions + 页签分发）/ SettingsCards.kt（九个主题卡片）
    ui/PluginsPage.kt       Plugins 目的地：只读插件清单（本版新增）
    ui/overlay 的同层改用 design/.../ui/overlay/Sheet.kt 的 DshSheet（本版新增）
    ui/components/
      Composer.kt / ComposerDock.kt / ComposerAccessories.kt / ComposerFullScreen.kt
      SessionStatsPills.kt（速度/Cache 药丸） QueueDock.kt（液态玻璃） ChatRows.kt（TodoCard 玻璃）
      ChatRows.kt / QueueDock.kt / FilesPanel.kt / Sheets.kt / …
    ui/scroll/TailFollow.kt   尾部跟随（padding 边缘修正）
    ui/theme/SystemBars.kt    （在 :design）透明导航栏 / 图标明暗
  src/test/screenshots/      26 张 PNG（本版 +6：4 张深色/AMOLED、chat-transcript-waiting、plugins-tab-empty）
  src/test/java/.../screenshot/ScreenBaselineTest.kt / WideBaselineTest.kt
core/model/          领域模型 + 测试；本版新增 StatusCapsule.kt（胶囊排名）/ PluginInventory.kt（插件清单）/ WorkspaceExplorer 的 ExplorerOrder + 过滤
core/term/           终端（纯 JVM，本机可跑）
core/config/         DshConfig + ThemeMode + AcceptedCert
core/protocol/       net/ + 79 测试（含 RpcCancellationTest 6 条）
design/              主题 + 7 档外观 + 玻璃与套件移植层
  ui/glass/           LiquidGlass（三档）/ LiquidPane / Lens / Vibrancy / GlassBarBackdrop
  ui/nav/             玻璃底栏 + FloatingBarState + SideRail
  ui/theme/           DshTheme / MiuixPalette / SystemBars / WindowBackground
tools/localbuild.sh  本机验证入口（JDK 21 + 临时 build-tools + 镜像开关 + 构建锁）
docs/                规划与调研；上游参考源码在 .work/ksu-kit（gitignored）
```

## 8. 文档地图

| 想知道 | 读 |
|---|---|
| **下一步做什么** | 本文件 §4；PHASE-3-PLAN §4 覆盖到 3.5（3.5b 已合入），输入框三轮不在里面 |
| 当前外壳的视觉/交互 | 本文件 §3、[DESIGN.md](DESIGN.md)、截图基线 |
| 许可与来源规则 | [../NOTICE.md](../NOTICE.md) |
| 阶段 2 历史结论 | [PHASE-2-PLAN.md](PHASE-2-PLAN.md) |
| 构建/CI/本机限制 | [BUILD-AND-CI.md](BUILD-AND-CI.md) |
| 设计 token | [DESIGN-TOKENS.md](DESIGN-TOKENS.md) |
| 模块拆分步骤 | [research/13-module-extraction.md](research/13-module-extraction.md) |

## 9. 给新会话的提醒

- **别重新调研**：`docs/research/` 有 13 份带 `path:line` 证据的文档；上游参考在 `.work/ksu-kit`、BiliPai 参考在 `/tmp/BiliPai`（可能已被清理，需要时重新 clone）。
- **别信旧结论**：标注「已被 X 修正」的以修正后为准；本版作废的旧话包括「输入框是底栏上方卡片」「默认是收起圆按钮」「药丸行只有卡片内控件」「液态档要画描边」。
- **单变量原则**：一个 PR 尽量只做一件事；但用户一次提多条时，按上面的轮次整理成“一组相关修复 + 一次基线重录”也可以（#40 就是 12 条一起）。
- **PR 描述必须写清**：这一轮**故意**改变什么、哪几张基线是预期变动、哪几张不是。
- **UI 改动先想基线**：改 `ChatPanel` 底部/药丸/玻璃几乎必然动 `chat-transcript`、`chat-hero`、`chat-wide`、`chat-transcript-collapsed`；只改平面档规则时 `composer` 可能字节不变（#40 的描边改动就是如此）。
- **液态档验收只能在真机**：截图基线固定 `LocalGlassEnabled=false`；`LocalTextToolbar` 的行为、系统栏 inset、手势跟手性也都只有真机能验。
- **先跑一轮本机编译再 push**：`tools/localbuild.sh . :app:compileDebugKotlin`（10 分钟内），能省 CI 一轮。
- **开新 PR 前确认没有并发构建**：看 `.build.lock` / 是否有人正在 CI；`localbuild.sh` 虽已修锁，但同一时间两个 gradle 在本机仍会争内存。
