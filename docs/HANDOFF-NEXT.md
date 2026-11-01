# 交接文档（给新会话）

> 写于 2026-11-01 深夜（阶段 3 开工当天），本版为 **第六版**。
> 沿革：一版「接管 + 工具链全量升级」→ 二版「结构拆分」→ 三版「1.2b」→ 四版「阶段 1 完成」
> → 五版「阶段 2.5 完成 + 2.6 清单」→ **本版「阶段 2.6 被 3.0 取代：整仓转 GPL-3.0 + 套件接管外壳」**。
>
> **新会话先读本文件，再读 [PHASE-3-PLAN.md](PHASE-3-PLAN.md)（唯一可执行计划）。**
> 阶段 2 的历史结论留在 [PHASE-2-PLAN.md](PHASE-2-PLAN.md)，不要删，但**别再照着开工**。

## 1. 一句话现状

仓库 <https://github.com/gitMFQ/dsh-android>（私有）是一个**已整仓转为 GPL-3.0** 的 DSH 原生安卓客户端。
阶段 1（结构拆分，5 个模块）与阶段 2.0–2.5（窗口底色、基线、pager、宽屏 rail）**全部合入**；
阶段 2.6 的原计划（TopBar 折叠）**被阶段 3 取代**。

阶段 3 在做的事：**直接复用 `chenaizhang/KernelSU-Style-UI-Kit`（GPL-3.0）的代码，把外壳换成「液态玻璃 + 药丸导航」的 Miuix 观感**。

**当前 master 实测**（2026-11-01 末）：`screenshots = 17`；CI 报 `tests=1166` / `screenshots=17`。

## 2. 已合入（master）

| PR | 内容 | 证据 |
|---|---|---|
| **#25** | 许可转 **GPL-3.0** + `NOTICE.md` + 接 `miuix-blur`（`tools:overrideLibrary`，minSdk 仍 26）+ 玻璃基座 `ui/glass/` | `tests=1166` / `screenshots=14` 均不变 |
| **#26** | 底栏改 **4 目的地**（Chat / Files / Plugins / Me），会话三视图（Chat/Trajectory/Shell）降为**页头下的药丸**；`showFiles` 双源清除 | 11 张基线逐字节不变，3 张按预期移动 |
| **#27** | 移植套件**玻璃底栏**（8 文件）+ 替换 2.4 手写底栏；旧 `components/FloatingBottomBar.kt` 删除 | `tests=1166` / `screenshots=14` |
| **#28** | Me / Me 子页 / Plugins 三条基线 | 14 张逐字节不变，新增 3 张 |
| **#30** | **修 CI 单点**：阿里云镜像不再排在官方源前面 | 见 §5 |

### 2.1 阶段 3 的关键技术结论（踩过的坑）

1. **`miuix-blur` 的 AAR 自己声明 minSdk 33**，本 app 是 26 → 靠 `tools:overrideLibrary` 接进来。
   这条豁免的安全**全靠纪律**：`:app` 不得 naming 任何 `top.yukonga.miuix.kmp.blur.*` 类型，
   只经 `:design` 的不透明句柄 `GlassBarBackdrop`（`rememberGlassBarBackdrop()` / `Modifier.glassBarLayer()`）布线。
   细则见 [NOTICE.md](../NOTICE.md)。
2. **约束的是「谁执行」不是「谁出现」**：依赖本身就打进 APK，类在任何设备上都存在；
   API 26 上会崩的是「调用了只能在 33+ 跑的东西」，编译期看不出来。
3. **三档降级**：33+ 液态（折射 + 高光）／31–32 半透明 + 描边／26–30 不透明。由 `currentGlassTier` 决定。
4. **Robolectric 会「谎报」能力**：`@Config(sdk = [34])` 下 `glassBarSupported()` 返回 true，
   但 shadow 没有 GPU 管线 —— 3.2b 首次接线时 `chat-hero` / `chat-transcript` **在 composition 阶段就炸了**
   （`IllegalArgumentException at capture` → `IllegalStateException at InlineClassHelper`）。
   解法是先例的第二个：`LocalGlassEnabled`（对标既有的 `LocalAnimatedDots`），两个 fixture 都 provide `false`。
   **后果：液态档没有截图基线，它的验证在真机 API 33+。**
5. **「三档一种设计」是结构性的**：`FloatingBarState` 被三档共用，画法不同、运动相同。

## 3. 阶段 3 的 PR 序列（完整版见 [PHASE-3-PLAN.md](PHASE-3-PLAN.md) §4）

| # | 状态 | 内容 |
|---|---|---|
| 3.0 / 3.1 | ✅ #25 | 许可 + 闸门 + 玻璃基座 |
| 3.2 | ✅ #27 | 玻璃底栏移植 + 接线 |
| 3.3 | ✅ #26 | 4 目的地 IA + 页内药丸 |
| 3.4 | ✅ #28 | 新页面基线 |
| **3.2b** | ⏳ **PR #29** | backdrop 布线（液态档真正可达） |
| **3.5a** | ⏳ **PR #31** | 移植 `overlay/` `list/` `feedback/`（Dialog / SegmentedList / SuperSearchBar / WarningCard / ScrollToTop） |
| 3.5b | 下一步 | 把 `SegmentedList` 接进 **Me 的各设置页签**（现在子页是占位）；`SettingsContent.kt` 的段落要提权成可独立进子页 |
| 3.6 | 待做 | Files 页从「复用面板」变成类 VSCode 树浏览器 |
| 3.7 | 待做 | TopBar 折叠：**随页变，不随滚动**（见 PHASE-3-PLAN §6） |
| 3.8 | 待做 | 状态胶囊（连接 / 运行 / 待审批） |
| 3.9 / 3.10 | 待做 | Sheet/Dialog 与抽屉换装（依赖 3.5a 的 `overlay/` `list/`） |

## 4. 关键约束（别再踩）

| 约束 | 原因 |
|---|---|
| **本机不能完整构建** | aarch64 + 无可用 aapt2。`assembleDebug` 会拖到 15 分钟以上且大概率 OOM（3.9 GB）。**验证走 CI**。 |
| **本机能编译 Kotlin** | `tools/localbuild.sh . :app:compileDebugKotlin`（约 1.5–4.5 分钟）。**测试源码编不了**（`:app` 的挂 aapt2），`:design` 的测试可以跑。 |
| **不要 `git stash -u`** | 2026-11-01 踩过：`-u` 会把队友的**未跟踪产出一起收走**，切分支后就从工作区消失（已从 stash 恢复，但浪费了一轮）。多人同时写时，切分支可以（`checkout -b` 会携带未跟踪文件），**stash / clean 不行**。 |
| **杀 gradle 会留下 `*.btbak`** | `localbuild.sh` 用 `trap` 还原 `buildToolsVersion`，被 kill 时 trap 跑不到。收尾：删掉 `*.build.gradle.kts.btbak` 并 `git checkout --` 那 5 个 `build.gradle.kts`。 |
| **`pluginManagement` 必须是 settings 的第一个语句** | 所以 `DSH_LOCAL_MIRRORS` 在两个块里各读一次，不能提成顶层 val。 |
| **AGP 9 不允许 `org.jetbrains.kotlin.android`** | 内置 Kotlin，应用该插件直接 build fail。 |
| **跨模块 smart cast 编不过** | 先把属性读进局部变量再判断，别改 `var`、别加 `!!`。 |
| **新增测试任务要写进两个 workflow** | 依赖一个模块**不会**跑它的测试，CI 会「绿着丢测试」。目前 6 个模块的名字都已在 `verify.yml` / `build.yml`。 |
| **两个数字别合并** | `tests`（功能）与 `screenshots`（像素基线）。CI 分开打印。 |
| **基线只手动录** | `gh workflow run screenshots.yml --ref master -f branch=<分支>`；下载 `roborazzi-baselines` 覆盖 `app/src/test/screenshots/` 后提交。**本机录不了。** |

## 5. CI 与那个被修掉的单点

| 工作流 | 触发 | 作用 |
|---|---|---|
| `verify.yml` | push master + PR | `tests=` + `screenshots=` 像素比对 |
| `build.yml` | 同上 | 出 debug APK |
| `release.yml` | tag `v*` | 出 release APK |
| `screenshots.yml` | 手动 | 在 CI 录基线并上传 artifact |

**#30 修的坑（重要）**：`settings.gradle.kts` 曾把阿里云镜像排在官方源**前面**（为**本机**网络），
但 Gradle **没有**「试一个仓库、失败就继续」：返回 5xx 的仓库会在**本次构建内被禁用**，
于是一个 `502 Bad Gateway` 变成 43 个解析失败、一个与提交无关的红。
现在**默认官方源在前**，`tools/localbuild.sh` 才导出 `DSH_LOCAL_MIRRORS=1` 把镜像排回前面。

## 6. 仓库结构

```
app/                 主模块（476+ 功能测试 + 17 张截图基线）
  src/main/java/uk/xa0/dsh/
    ui/ChatPanel.kt       3519+ 行，外壳与聊天树（最大的一块）
    ui/ShellTabs.kt       Destination 枚举 / 4 项底栏 / ViewPill / MeTab / PlaceholderTab
    ui/components/        行内组件（ChatRows 1856、JobsCard、FilesPanel …）
    ui/glass/             （无）—— 玻璃在 :design
core/model/           领域模型 50 文件 + 413 测试
core/term/            终端 7 文件 + 186 测试（**纯 JVM，本机可跑**）
core/config/          DshConfig + ThemeMode + AcceptedCert
core/protocol/        net/ 14 文件 + 73 测试
design/               主题 + 7 档外观 + **玻璃与套件移植层**
  ui/theme/            DshTheme / MiuixPalette / SystemBars / WindowBackground
  ui/glass/            LiquidGlass（三档裁决）/ InnerShadow / Lens / Vibrancy / GlassBarBackdrop
  ui/nav/              玻璃底栏 + FloatingBarState + SideRail + 动画（8 文件）
  ui/overlay list feedback/  3.5a 移植的组件（Dialog / SegmentedList / SuperSearchBar / WarningCard / ScrollToTop）
tools/localbuild.sh    本机验证入口（JDK 21 + 临时 build-tools + DSH_LOCAL_MIRRORS=1）
docs/                  规划文档；上游参考源码在 .work/ksu-kit（gitignored）
```

## 7. 文档地图

| 想知道 | 读 |
|---|---|
| **下一步做什么** | [PHASE-3-PLAN.md](PHASE-3-PLAN.md)（含 §4 PR 表、§5 移植清单、§6 折叠语义） |
| 许可与来源规则 | [../NOTICE.md](../NOTICE.md) |
| 阶段 2 历史结论 | [PHASE-2-PLAN.md](PHASE-2-PLAN.md) |
| 版本为什么是这些 | [research/10-version-lock.md](research/10-version-lock.md) |
| 构建/CI/本机限制 | [BUILD-AND-CI.md](BUILD-AND-CI.md) |
| 设计规范 | [DESIGN-TOKENS.md](DESIGN-TOKENS.md) |
| 模块拆分的可执行步骤 | [research/13-module-extraction.md](research/13-module-extraction.md) |

## 8. 给新会话的提醒

- **别重新调研**：`docs/research/` 有 13 份带 `path:line` 证据的文档。
- **别信旧结论**：凡标注「已被 X 修正」的以修正后的为准；本版明确作废的是「阶段 2.6 = TopBar 折叠」与「不复制 GPL 代码」。
- **单变量原则**：一个 PR 只改一件事，CI 绿了再进下一步。
- **PR 描述必须写**：这一屏**故意**改成什么样、基线 diff 里哪几处是预期的、哪几处不是。
- **真机上的第一件事**（阶段 2 遗留、至今未验证）：抽屉左缘右滑在 pager 之下是否仍能打开。若不能，修法是在 `DRAWER_EDGE` 内关掉 pager 的 `userScrollEnabled`。
- **液态档的验收也只能在真机**：胶囊是否折射对话、首帧指示器是否有内容（录音层天然慢一帧）。
