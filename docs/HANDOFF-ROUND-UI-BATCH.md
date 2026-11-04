# 本轮交接（2026-11-04）

本轮把用户的一整批 UI/交互反馈做成了 16 组改动。以下按「用户原话 → 做了什么 → 落在哪」记录，
方便下一轮验收。

## 1. 文件树文件夹点后不展开（真 bug）

`DshViewModel.toggleWorkspaceDirectory` 改的是 `_workspaceTree.value.open`，而
`publishTree()` 会用 `explorerTreeOpen` **重建**整棵树——那个字段从来没被写过。所以点一下：
先短暂显示展开（copy 出来的 state），紧接着 `publishTree()` 又把它按空集合重建回去。
手机上就是「闪一下，没展开」。

修法：toggle 写 `explorerTreeOpen` 这个字段本身；`resetWorkspaceExplorer` 一并清空它。
`core/model` 侧补了 3 条 `WorkspaceTreeFoldTest` 用例钉住「open 集合才是折叠真相」。

## 2. 液态玻璃：浅色阴影 + 对角高光（design 模块）

- `glassLift` 不再「浅色模式不画阴影」。浅色叠两层小阴影（2dp 贴边 + 10dp 柔和、alpha 减半），
  因为单层 10dp 居中阴影会透过半透明填充漏到面板内部（这是当初关掉它的原因，见文件里的长注释）。
- 新增 `design/.../glass/DiagonalHighlight.kt` 的 `rememberDiagonalHighlight()`：
  `BloomStroke` 双光源放在左上、`z < 0`，得到从左上角起亮、右下角落暗的**对角**镜面边。
  `liquidPane` 现在默认带上它，所以输入框、药丸、To-dos、目标条、顶栏、抽屉全部一起有。
  底栏本来就有自己的 specular，不动。

## 3. 外壳：顶栏归属与底栏通用

| 改动 | 位置 |
|---|---|
| 顶栏只在对话页；其它页自己画 | `ChatPanel.ChatPanel` 的 `if (destination == Destination.CHAT)` |
| 对话页顶栏 = 圆形三横 + 大药丸（标题+目录）+ 圆形三点 | `ChatPanel.ChatHeader` 重写 |
| 三点菜单承载 Files / Router / Jobs / 父会话 / 子代理 | `HeaderAction` + `ChatHeader(overflow = …)` |
| 顶栏透明，内容滑到顶栏和通知栏后面 | `ChatHeader` 用 `liquidPane` + `statusBarsPadding()`，不再 `background` |
| 其它按钮降级为顶栏下的小药丸 | `ChatPanel` 的 pill strip（`SmallGlassPill` + `ViewPill` + 状态胶囊） |
| 底栏在**每一页**都显示 | 删掉 `if (destination == Destination.CHAT || !wide)` 这个门 |
| 非对话页显示圆形铅笔，跨页有同样的展开/收起动画 | `ComposerDock(expanded = destination == CHAT && …)`，动画本来就是 `animateDpAsState`，跨页时目标值变化即播 |
| 所有页面底部系统栏透明 | Files / Plugins 不再 `navigationBarsPadding()`，内容跑到系统栏后面 |

## 4. Me 页与设置

- `Destination.ME` → `Destination.SETTINGS`（图标 `Tune`）。
- 新增 `ui/SettingsDestination.kt`：**两级**页面（列表 → 分区页，Back 上一级），
  这是用户说的「设置的层级现在都不对，点进项后应该是进入新一个层级的」。
- 抽屉 footer 的 Settings/About 删掉（`SessionsDrawer.kt`），About 从设置页进入。
- 设置项映射：`This device`（本地）/ `General` / `Appearance` / `Model` / `Agent` /
  `Terminal` / `Plugins` / `Diagnostics`，全部复用 `ui/settings/SettingsCards.kt` 的卡片。

## 5. AMOLED 从「一种外观」变成开关

- `DshConfig.amoled: Boolean` + `ConfigStore.KEY_AMOLED`（含 window 缓存字段）。
- `dshPalette(themeMode, systemDark, monet, amoled)`：`amoled && dark` 时套 `toAmoled()`。
  **只对深色生效**，这就是用户要的「开启后是所有深色时使用 amoled 模式」。
- `APPEARANCE_CHOICES` 里删掉 AMOLED 项；Appearance 页加一个 `SegmentedSwitchItem`。
- 旧存档里 `themeMode == "amoled"` 仍然解析（不把老用户的黑色重置掉）。

## 6. 对话页 shell（Trajectory / Shell）

- 设置里加 `This device → Trajectory and Shell tabs` 开关（`DshConfig.chatViewsEnabled`）。
- 关掉后 pill 只留 Chat，且如果在关掉时正停在 Trajectory/Shell 上，会 `LaunchedEffect` 退回 Chat
  ——否则那个座位消失后读者出不来。

## 7. 读数药丸：缓存 + 首句后才出现

- `SessionStatsPills` / `ComposerAccessories` 缓存**整份** `SessionStats`（不只 tok/s），
  弹层里的明细、cache、成本、子代理行都用缓存值。
- `statsVisible = hasConversationStarted(entries)`：发出第一句话之前不画这两颗药丸。
- `sessionKey = ui.currentSessionId`：换会话丢弃缓存。

## 8. 展开的思考/工具内容：吸顶收起栏

- 行内：`ReasoningRow` 展开体封顶 280dp 并自带滚动，标题栏永远在滚动区外。
- 全局：`ExpandedProcessCollapseBar` 画在 `LazyColumn` **外面**的 Box 顶部，
  所以内容怎么滚它都在拇指能到的位置。

## 9. 引用文本 + 翻译

- 选择菜单加 `Quote text` / `Translate` 两行（`ComposerSelectionMenu`）。
- **引用**：选区变成 `> ` 引用块追加进草稿（`quoteIntoDraft`）。
- **翻译**：走宿主自己的插件 `cotTranslation/translate`（`DshViewModel.translateFragment`），
  不需要本机 key、也不需要本机联网。目标语言取系统 locale 的语言子标签（`translationTargetOf`）。

## 10. 文件可编辑（重要限制）

宿主**没有** `workspaceFiles/write`——Remote 面只有 list/read/readBytes/stat/changes，
web 自己的文件预览也是只读的。所以保存走**本会话自己的 shell**：
`printf %s <base64> | base64 -d > <tmp> && mv -f <tmp> <target>`。

- 编码是 base64 而不是引号：编辑缓冲区是任意文本，任何引号方案都有一种输入能逃逸。
- 写临时文件再 rename：解码失败时原文件不被截断。
- 一次性 shell 用完即关：不动读者的 Shell 页，也不占宿主的终端配额。
- `fileWriteCommand()` / `shellSingleQuote()` 是纯函数，`core/model` 有 5 条测试。

## 11. 其它

- 插件页按 dsh web 的信息层级重做（分组 + 展开详情 + fiberPhase 六态措辞），仍只读。
- 文件页顶栏换成玻璃药丸（路径 + 工具药丸行），删掉返回按钮。
- access mode 药丸按模式换图标 + 颜色（`accessModeGlyphOf`，4 条测试）。
- 抽屉：筛选三行压成一行「滑杆」菜单，New Session 移到搜索框下并等宽，
  工作区加三点菜单，条目保持不透明底。

## 12. 本轮**没有**做的 / 已知缺口

- **多语言**：只做了 `translationTargetOf` 这类硬事实。全量 i18n（用后端 locale 覆盖）没做，
  `values/strings.xml` 仍只有 77 行。这是一个独立工程，建议单独一轮。
- **图标全量替换为 dsh web 图标**：`DshIcons.kt` 只有 branch/queue 两个真 dsh 图标，其余仍是
  Material rounded。access mode 的四个图标是我按语义选的 Material 图标，不是 dsh 的。
- **截图基线**：`me-tab`/`me-tab-dark`/`me-tab-amoled` 换成了 `settings-page`/`-dark`/`-amoled`；
  `drawer-*`、`files*`、`plugins*`、`chat*` 都因这轮视觉改动会变。**本机录不了基线**，需 CI dispatch。
- **`:core:model` 测试本机跑不了**（aapt2），新增的 4 个测试文件只能由 CI 验证。
- `SessionsDrawerTreeTest` 已按新菜单结构更新；`SessionsDrawerWorkspaceOrderTest` 的注释前提过时但断言应仍成立。
## 13. 验证状态（本轮结束时）

- `tools/localbuild.sh . :app:compileDebugKotlin` → **BUILD SUCCESSFUL**（9m26s，仅有既有 deprecation 警告）。
- `git status` 干净：没有 `buildToolsVersion` 残留、没有 `.btbak`。
- **本机跑不了测试**：`:app` 测试源码依赖 aapt2，`:core:model` 的 `processDebugUnitTestResources` 也挂在
  aapt2（见 `docs/BUILD-AND-CI.md` §0.0）。所以新增的 4 个测试文件（`AccessModeGlyphTest`、
  `FileWriteCommandTest`、`TranslationTargetTest`、扩充的 `WorkspaceTreeFoldTest`）与改动的
  `SessionsDrawerTreeTest`、`ScreenBaselineTest` **只能由 CI 验证**。
- 截图基线必须重录（本机录不了，需要 `gh workflow run screenshots.yml`）：预期变动的有
  `settings-page*`（新增三张，替代 `me-tab*`）、`drawer` / `drawer-empty` / `drawer-dark`、
  `files` / `files-tree`、`plugins-tab` / `plugins-tab-empty`、以及所有 `chat*`（顶栏 + 药丸行 + 底栏）。

## 14. 下一步建议

1. **先 push 让 CI 跑一轮**，拿到 `tests=` / `screenshots=` 的真值，再按失败清单收。
2. **多语言**是唯一还完全没动的大项：`values/strings.xml` 只有 77 行，硬编码英文约 1100 条。
   建议单独一轮，按屏（设置 → 抽屉 → 聊天 → 文件）分批搬。
3. **图标**：`DshIcons.kt` 只有 branch/queue 两个真 dsh 图标。要全量替换，得先把 dsh web 的
   `ui-primitives/src/icons/index.tsx` 里的 SVG path 抠出来（本机只有编译产物 `client.js`，
   里面能 grep 到 SVG，但没有源文件路径映射），工作量不小。

## 本轮（round 3）补齐：多语言 + dsh 图标

### 多语言：从宿主字典反抽，而不是手写

DSH 的每个 client 插件把自己的中文词典编译进 `client.js`（`const zh = { "key": "译文" }`）。
`tools/host-locale.py` 把这 45 个包、**2469 条**中文抽成 `tools/dsh-locale.json`，然后按**英文值精确匹配**
（外加一层标点/占位符归一化）回填 `values-zh-rCN/strings.xml`。

为什么是反抽而不是手写：宿主已经把 `正在分析请求`、`新建会话`、`操作审批` 翻译好了，
手写只会更慢更差，而且两个客户端会在同一个会话里对「workspace」叫法不一致。

实测：**135/311 命中宿主字典**，其余 176 条是本 App 独有文案（设置页副标题、抽屉的视图菜单等），
保留英文——留白是可见且诚实的，错配是隐形且错的。

用法：`python3 tools/host-locale.py`（非破坏性：手写译文优先，宿主只填空白）。

### 字符串外化：316 条，四个文件

| 文件 | 条数 | 覆盖 |
|---|---|---|
| `values/strings-settings.xml` | 133 + 1 plurals | 设置页、关于页、设置卡片 |
| `values/strings-chat.xml` | 130 + 6 plurals | ChatRows、QueueDock、JobsCard、SessionStatsPills、Composer、ComposerFullScreen |
| `values/strings-drawer.xml` | 43 + 3 plurals | SessionsDrawer 全部 |
| `values/strings.xml`（原有） | 16 | Plugins 页 |

分文件的理由：三个 agent 并行抽不同屏，共用一份 `strings.xml` 必然冲突；
Android 允许 `res/values/` 下多份文件按 name 合并，所以按屏拆分既并行又无冲突。

### 应用内语言切换

- `DshConfig.appLanguage`（BCP-47，空白 = 跟随设备）+ `ConfigStore.KEY_LANGUAGE`。
- 设置 → This device → Language 一行，两个选项（跟随设备 / English / 简体中文）。
  **只有两个**：这个 build 只有这两份资源，列八个而六个回落英文是骗人的菜单。
- 应用机制：`MainActivity` 里 `LaunchedEffect(ui.appLanguage)` 用 `updateConfiguration` + `recreate()`。
  不用 `AppCompatDelegate.setApplicationLocales` 是因为本 App 是纯 Compose、没有 appcompat，
  为了一个设置引入整个 backport 不值；`recreate()` 的代价是诚实的——换语言本来就换掉了进程里每一条字符串。

### dsh 图标

`tools/host-icons.py` 把宿主 `ui-primitives/src/icons/artwork.ts` 里的 **93 个 SVG 字形**
转成 `ui/components/DshIconVectors.kt`（2491 行，check in 的生成物，构建不需要 TypeScript 工具链）。

转换保留：`viewBox` → viewport（不重缩放）、每条 path 自己的 stroke/fill（这些字形混用两者）、
`fill-rule="evenodd"`（close-circle / check-circle 靠它挖洞，Compose 默认是 non-zero）。

已接入：对话页顶栏（PanelLeft / Ellipsis）、输入框（Plus / Send / Stop）、
抽屉（Refresh / Plus / Ellipsis / SlidersTwo / Chevron）、底栏四个目的地（Send / FolderOpen / PluginPinwheel / Settings）、
设置页的 Globe（语言行）。

**其余仍是 Material rounded**：`DshIconVectors` 有 93 个可用字形，但把 `Icons.Rounded.*` 全量替换是
一次跨十几个文件的机械改动，且每个替换都要确认语义对得上（Material 的 `Description` 和宿主的
`deliver-doc` 不是同一个东西）。建议单独一轮做。

## 本轮（round 3）收尾状态

- `tools/localbuild.sh . :app:compileDebugKotlin` → **BUILD SUCCESSFUL**（7m06s）。
- `git diff --stat -- "*build.gradle.kts"` 为空（无 buildToolsVersion 残留），`.btbak` 0 个。
- 五个资源文件全部通过 `ET.parse`：values 16/136/46/137 + values-zh-rCN 324。
- **图标覆盖率**：Material rounded 从 75 种降到 **49 种**，宿主图标从 13 种升到 **30 种**，
  共改了 30 个文件。剩下的 49 种里有一部分在 `DshIconVectors` 里没有语义对应（Material 的
  `Description`、`Image`、`ChatBubble` 等在宿主图标集里不是同一个概念），另一部分是纯图形
  （`DshWordmark` 之类）。**没有为了凑数而强行映射**——错配的图标比 Material 的更像 bug。

### 仍未做（诚实清单）

| 项 | 状态 | 原因 |
|---|---|---|
| 剩余 49 种 Material 图标 | 未换 | 无可靠语义对应，强换会画错 |
| 176 条未翻译字符串 | 保留英文 | 宿主字典里没有（本 App 独有文案），手写翻译是另一个工程 |
| 截图基线 | **必须重录** | 本机录不了，需 `gh workflow run screenshots.yml` |
| `:core:model` / `:app` 测试 | **只能 CI 验证** | 本机 aapt2 不可用 |
| `MeSettingsPageTest` 的 APPEARANCE_CHOICES 断言 | 已改 | AMOLED 变开关后是 6 项 |
| `SessionsDrawerSearchTest` 的 "1 sessions" | 已改 | 改复数后是 "1 session" |

### 预期变动的截图基线

`settings-page` / `-dark` / `-amoled`（新增，替代 `me-tab*`）、`drawer` / `drawer-empty` / `drawer-dark`、
`files` / `files-tree`、`plugins-tab` / `plugins-tab-empty`、以及全部 `chat*`——
顶栏、药丸行、底栏、图标、文案全都动过。

## 本轮（round 4）新增：语言切换落地 + 图标铺开 + 中文补齐

### 语言切换

- `DshConfig.appLanguage` + `ConfigStore.KEY_LANGUAGE` + `UiState.appLanguage` + `setAppLanguage()`。
- 设置 → This device → **Language** 一行（跟随设备 / English / 简体中文）。
- 应用机制：`MainActivity` 的 `LaunchedEffect(ui.appLanguage)` 用
  `resources.updateConfiguration` + `recreate()`。没用 `AppCompatDelegate.setApplicationLocales`
  是因为本 App 纯 Compose、无 appcompat，为一个设置引入整个 backport 不值。

### 中文覆盖率：313/314（99.7%）

`tools/host-locale.py` 现在有三层匹配，外加一份手写补充表：

1. **英文值精确匹配**宿主 2469 条字典。
2. **归一化匹配**（`…`→`...`、`{count}`→`%s`、大小写、尾标点）。
3. **按资源名匹配**（`@<name>`）——长段落的中文有时与英文键差一个词，靠名字兜底。
4. **手写 `SUPPLEMENT`**：约 130 条本 App 独有文案（设置副标题、抽屉菜单、审批动词），
   全部复用宿主术语（`审批`/`插话发送`/`排队发送`/`收起`/`展开`），两个客户端说一套话。

剩 7 条**故意不译**：`tok/s`、`tok`、`Android`、`API %1$d`、`Monet`、`DSH`——
这些是术语与品牌，翻译只会让人看不懂。

### 图标：30 种宿主字形，32 个文件

Material rounded 从 75 种降到 49 种，宿主图标从 13 种升到 30 种。
映射只做**语义一对一**的（chevron→chevron、plus→plus、bin→bin）；
Material 的 `Description`/`Image`/`ChatBubble` 在宿主图标集里不是同一个概念，**没有强换**——
错配的图标比 Material 的更像 bug。

## 本轮（round 5/6）新增

### 1. 底栏遮住页面内容（真 bug，我上一轮引入的）

把底栏改成「每一页都显示」之后，**五个页面都没有给自己留出底栏的高度**——
它们以前靠 `navigationBarsPadding()` 避开系统栏，而系统栏的 inset 现在由底栏自己付，
页面就只剩 `fillMaxSize()`。Shell 的提示行、文件页的最后一行、设置页的最后一张卡，
全在底栏底下。

修法：五个页面统一 `padding(bottom = dockAreaDp)`（`dockAreaPx` 量在底栏两处 inset 外侧，
所以这个数**已经包含**系统导航栏）。Shell 和 Trajectory 也补了同一个。

### 2. 图标第二轮：Material 75 → 22 种

第二轮映射又接上 27 组语义一对一（`InsertDriveFile`→`DeliverDoc`、`SmartToy`→`AgentPresetOutline`、
`Psychology`→`ThinkOutline`、`Speed`→`GaugeOutline`、`SubdirectoryArrowRight`→`TreeCorner`…）。
宿主字形从 30 种升到 **49 种**，34 个文件在用。

剩 22 种**故意不换**：`Image`/`BrokenImage`/`BugReport`/`ContentCut`/`ContentPaste`/`FormatQuote`/
`LocalCafe`/`Palette`/`Translate`/`Sort`/`Save`/`SelectAll`/`Memory`/`Lightbulb`/`Inbox`/`Inventory2`/
`RateReview`/`Build`/`Badge`/`ChatBubble`/`Hub`/`VisibilityOff`——宿主图标集里没有这些概念。
强行映射会画出一个**关于控件用途的错误声明**，比一个外来形状更糟。

### 3. 顶栏浮在内容之上（本轮最大的一次结构改动）

原来是 `Column { 顶栏; 内容 }`——顶栏**占位**，内容从它下面开始，所以「内容滑到顶栏后面」
在结构上不可能发生，无论顶栏多透明。

改成：

```
Box {                                  // 外壳
    内容（pager 填满整个区域，顶到窗口上沿）
    顶栏   zIndex(1)                    // 浮在上面
    错误横幅 zIndex(1) + top = headerPx  // 也要浮，且不能被顶栏盖住
}
```

对话页同样 `Column` → `Box`：药丸行改成 `align(TopCenter) + padding(top = chromeDp)` 并**测量高度**，
转写列表的 `contentPadding.top = chromeDp + pillStripPx + 16dp`。

关键在 `contentPadding` 而不是 `padding`：它在**可滚动内容内部**，所以行会「穿过」这段留白，
在透明顶栏和通知栏底下可见，而不是被切在下面。

顶栏和横幅的高度都是 `onSizeChanged` 量的（横幅量在 padding 内侧，避免 headerPx 被重复计入导致
`chromeDp` 每帧变大）。

## 本轮（round 6/7）：本机终于能验证测试源码了

### 为什么之前不能，现在可以

`docs/BUILD-AND-CI.md` §0.0 说 `:core:model` 的测试在本机跑不了，因为
`processDebugUnitTestResources` 要 aapt2。这话对 **Gradle 路径**成立——但它同时意味着
每一轮 UI 改动在本机都无法验证，`ScreenBaselineTest` 里那个多余的 `}` 就是这么活到 CI 的。

资源 APK 只在**打包给 Robolectric** 时才需要。`:core:model` 的测试是纯 JVM：不碰 Context、
不碰资源、不碰 manifest。所以：

1. `:core:model:compileDebugUnitTestKotlin` 本机能编（已经能）。
2. `generateDebugRFile` **不需要 aapt2** 就能产出一份真的 `R.jar`——把它放到测试任务
   期望的打包目录，`packageDebugUnitTestForUnitTest` 就不再报 `Cannot load resources`。
3. 然后用 JUnit 直接跑编译好的 class，classpath 照 Gradle 的方式拼。

这两步脚本化成 **`tools/local-jvm-tests.sh`** 和 **`tools/skip-res.gradle`**（都已提交）：

```sh
tools/local-jvm-tests.sh . core/model     # → OK (485 tests)
tools/localbuild.sh . -I tools/skip-res.gradle :app:compileDebugUnitTestKotlin   # 编 :app 测试源码
```

### 本轮的实测结果

| 检查 | 结果 |
|---|---|
| `:core:model` 全部 49 个测试类 | **485 tests, 0 failures** |
| `:app:compileDebugKotlin`（主源码） | SUCCESSFUL |
| `:app:compileDebugUnitTestKotlin`（测试源码） | SUCCESSFUL |

`ScreenBaselineTest` 里被抓出来的那个多余 `}`（把类提前收掉，导致后面全部「未解析引用」）
是本轮最有价值的发现——它正是 CI 才会暴露、而且会浪费一整轮 CI 的那类错误。

**仍然只能走 CI 的**：`:app` 测试的**执行**（Robolectric 需要资源 APK）、以及截图基线。

## 本轮（round 7）：思考内容翻译 + 一个真 bug

### 1. 展开思考时翻译（目标里漏做的一项）

目标原文：「展开思考内容时应该调用后端翻译插件翻译思考内容」。之前只做了选择菜单里的
「翻译」按钮，**展开时自动翻译**没做。现在补上：

- `ReasoningRow` 新增 `translate: ((String, (String?) -> Unit) -> Unit)?`，
  **首次展开时触发一次**，之后不再问——宿主对同一段文本的翻译是在会话日志里留存的，
  第二次问会命中它自己的记录而不是再打一次 provider。
- 状态存在行内（`remember(reasoning)`），不是存在宿主：一段翻译只对它所属的那一行有意义，
  而且 key 在文本上，所以惰性列表复用行时不会串到上一段的译文。
- **译文画在原文上面**，不是替换：思考内容是证据，读者展开就是为了看模型实际做了什么，
  把原文换掉等于把用户来的目的藏起来。
- 不阻塞：原文立刻画，译文到了再出现，中间显示「正在翻译…」。

### 2. 吸顶收起栏其实从来没出现过（真 bug）

上一轮我加了 `ExpandedProcessCollapseBar`，但它是靠 `expandedProcess.label` 非空来画的，
而 label 只在 `onRowExpanded` 被调用时才设置——**而 `onExpandedChange` 从来没被任何调用点传过**。
也就是说那个「展开思考/工具内容时一直留在屏幕上的收起栏」在真机上根本不会出现。

现在接上了四处：消息里的思考、流式思考、工具树行、扁平工具行。label 取
`chat_think`（"Think"/"思考"）和 `chat_details`（"Details"/"详情"）。

### 3. 顺带

- 新增三条字符串 + 三条中文（`chat_translated` / `chat_translating` / `chat_details`）。
- `AssistantMessageRow` / `LiveAttemptRow` 各多了两个可选参数（都有默认值，其它调用点不受影响）。

## 本轮追加：对话内容的选择菜单也有引用 / 翻译了

目标里那句「对话内容的选择后的复制、全选等应该加个引用文本功能」和「对话内容下面选择的
弹出菜单应该也有个翻译按钮」说的**不是输入框**，是**转写里的消息**。

之前只有输入框有：`Composer.kt` 在自己内部 `CompositionLocalProvider(LocalTextToolbar provides …)`，
而转写的 `SelectionContainer` 拿到的是**平台**工具栏——只有复制/全选，没有引用和翻译。

现在转写也有自己的：

- 每行一个 `SelectionState`，把该行的选中文本 `snapshotFlow` 到一个共享 holder。
  仍然**每行一个 `SelectionContainer`**（不是整个列表一个）——跨两个惰性项的选择会在任一端
  被回收时丢掉端点，这是原来就写在注释里的理由。
- `CompositionLocalProvider(LocalTextToolbar provides transcriptToolbar)` 包住 `when (chatView)`
  整块：`SelectionContainer` 是向它的 composition 要工具栏的，一个 provider 覆盖整张列表，
  每行的菜单才是同一个菜单。
- `ComposerSelectionMenu` 在页面层画一次（popup 必须比触发它的那一行活得久），
  `onQuote = quoteIntoDraft`、`onTranslate = translateSelection`——和输入框是**同一对回调**，
  所以转写里引用一段话会落到同一个草稿上。`onFullscreen = null`（这里不是输入框）。

顺带确认：本轮编译前后 `ChatPanel.kt` 花括号 503/503 配平，`:core:model` 仍是 485 tests 全绿。

## 本轮（round 8）：把本机可验证的范围扩到四个模块

`tools/local-jvm-tests.sh` 现在对 `core/model` 和 `core/config` 都能跑，
加上 Gradle 本来就能跑的 `:core:term`（纯 JVM，任务名是 `test` 不是 `testDebugUnitTest`）
和 `:design`，本机可验证矩阵变成：

| 模块 | 怎么跑 | 结果 |
|---|---|---|
| `:core:model` | `tools/local-jvm-tests.sh . core/model` | **485 tests, 0 failures** |
| `:core:config` | `tools/local-jvm-tests.sh . core/config` | **7 tests, 0 failures** |
| `:core:term` | `tools/localbuild.sh . :core:term:test` | BUILD SUCCESSFUL |
| `:design` | `tools/localbuild.sh . :design:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:app` 主源码 + 测试源码 | `tools/localbuild.sh . -I tools/skip-res.gradle :app:compileDebugUnitTestKotlin` | SUCCESSFUL |

`:core:protocol` 仍然只能靠 CI：它的测试要 okhttp / kotlinx-serialization 的完整传递依赖，
而**手写 jar 名单会漏、通配整个缓存会遮蔽版本**（两种都会表现成 `ClassNotFoundException`，
和真的测试失败长得一模一样），从 Gradle 要 `debugUnitTestCompileClasspath` 又因为在任务图之外
解析而报 variant 歧义。与其用一条会撒谎的命令，不如明确说它不在这里跑。

`:core:protocol` 本轮没有被改动。

## 本轮（round 9）：又一个我自己引入的真 bug

### 文件页和插件页的标题被时钟压住了

把外壳从 `Column` 改成 `Box`（让内容能滑到顶栏后面）之后，**非对话页失去了状态栏留白**。

原来外壳是 `Column { 顶栏; 内容 }`，而顶栏当时在**每一页**都画——所以文件页、插件页的内容
天然从顶栏下面开始，也就自然避开了状态栏。改成 Box 之后：

1. 顶栏只在对话页画了；
2. 非对话页的 `headerPx` 被置 0；
3. 那些页面自己的标题**没有** `statusBarsPadding()`；
4. 而它们又不再 `navigationBarsPadding()`（那个 inset 归底栏付）。

结果：文件页的路径药丸和插件页的标题直接压在状态栏的时钟下面。

修法（一处）：非对话页的 `headerPx` 不再置 0，而是置**状态栏高度**。这样
`chromeDp = 状态栏 + 错误横幅` 在每一页含义一致——「顶部那条内容不能起始于其下的带子」，
而横幅的 `padding(top = headerDp)` 也顺带被推到时钟下面。

顺带：`SettingsDestination` 自己那层 `statusBarsPadding()` 删掉了（现在由外壳统一给），
否则设置页的标题会被推下两条状态栏的高度。FilesPanel / PluginsPage / TerminalScreen /
TrajectoryScreen 都确认过没有自己的 inset，不存在重复。

### 现状

- `:app` 主源码 + 测试源码编译通过。
- `:core:model` 485 tests、`:core:config` 7 tests 全绿。
- 本次修复后，本机可验证的五项全部重新跑过。

## 本轮追加：又一个「设了状态但从没画出来」的 bug

选择菜单里的「翻译」按钮会发 RPC、会收到结果、会把结果存进 `translation`——
**但没有任何地方读它**。也就是说点了翻译什么都没发生。

同一类问题上一轮已经抓到一个（吸顶收起栏的 `onExpandedChange` 没人传）。所以这次做了
一次系统检查：把 `ChatPanel.kt` 里所有 `var x by remember { mutableStateOf(...) }` 挖出来，
数每个名字在文件里出现几次。

结果只有两个：

- `translation` / `translationBusy`：**真的没画**。现在补了一个 `DshSheet`——
  用 sheet 而不是就地替换，因为读者问的是「这句什么意思」，不是「把这句换掉」；
  选中的原文还在背后。空结果（宿主没有翻译插件 / 拒绝）明确说「宿主没有返回译文」，
  而不是画一张空卡。译文可选中，带一个 Copy 药丸。
- `showSettings`：抽屉改跳 `Destination.SETTINGS` 之后留下的死变量，删掉。

这次检查值得记住：**它是一个 grep 就能做的检查，而它抓到了两个真 bug。**
`mutableStateOf` 写了不读，编译器不会说一个字。

## 本轮（round 10）：按 bug 的「形状」做系统排查

前两轮各抓到一个「接线断了但编译器不说话」的 bug（`onExpandedChange` 没人传、`translation`
没人画）。这一轮不去读代码，而是按这两类 bug 的**形状**搜：

| 检查 | 方法 | 结果 |
|---|---|---|
| 状态写了不读 | `var x by remember { mutableStateOf(...) }` 数引用次数 | 上一轮修完，**0** |
| 字符串定义了不用 | `values/*.xml` 的 name 集合 减 Kotlin 里的 `R.string.*` 引用 | **1 条真死**：`plugins_loaded`（中英各删一份） |
| private 函数定义了不调 | 每个 `private fun` 数调用点 | **2 个真死**：`LineageChip`、`RunningDot`（都是重写顶栏时留下的） |
| 参数声明了不读 | 解析每个 top-level `fun` 的参数，在函数体里数引用 | **0 个**（唯一命中是解析器把表达式体函数的下一段当成了自己的体） |

最后那一行的 0 是这轮最想确认的数字：`onExpandedChange` 那类「参数一路传下来、到某一层就断了」
的问题，如果还有第二个，就会出现在这里。

顺带：`onSaveFile` 的 lambda 里那个「表达式未使用」警告清掉了（7 → 6 条警告，剩下 6 条是既有的
废弃 API、多余的 `!!` 和一处文档化的 `INVISIBLE_REFERENCE` 抑制）。

### 本机验证（每轮重跑）

`BUILD SUCCESSFUL`；`:core:model` 485 tests、`:core:config` 7 tests 全绿；build 文件干净、无 `.btbak`、
五份 XML 全部合法。

## 本轮（round 11）：把截图基线的「预期变动」算准

CI 的截图门禁是逐张比对，**没有测试对应的基线 PNG 它不会报错**——所以它既不会帮你发现
「多了一张」，也不会帮你删掉。这种东西只能手算。

现在算清楚了：

| | 名字 |
|---|---|
| 有 capture、没基线（重录后会新建） | `settings-page`、`settings-page-dark`、`settings-page-amoled` |
| 有基线、没 capture（重录**不会**删，要手动删） | `me-tab`、`me-tab-dark`、`me-tab-amoled` |

三条 `me-tab*` 已经把仓库里的删掉了：它们截的是 Me 页，而 Me 页这一轮被 Settings 页取代。
剩 26 张基线，重录后应为 29 张。

## 本轮（round 11）：一个失败的检查，记下来免得下次再写

我试了「扫测试里断言的字符串，看是否还存在于 main」，想用来自动发现「改文案导致断言过期」。
**这个检查写不对**：正则找 `"..."` 会把 KDoc 注释里成对的引号配成大段文本，于是短字面量
被吞进更长的匹配里，结果既漏报（`Browse` 明明在源码里却说不在）又误报一堆。

可靠的版本要从测试源码做 token 级解析，或者干脆跑一遍测试。**本轮放弃了这个方向**，
没有把它提交成工具——一个会撒谎的检查比没有检查更糟。

结论：改文案引起的断言过期，本机只能靠 `:app:compileDebugUnitTestKotlin`（能抓符号，抓不住
字符串）+ CI 实跑。这是诚实的边界。
