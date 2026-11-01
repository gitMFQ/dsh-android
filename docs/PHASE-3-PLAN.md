# 阶段 3 · GPL 套件接管外壳（可执行计划）

> 写于 2026-11-01。**本文件取代 `PHASE-2-PLAN.md` 作为「下一步怎么做」的唯一依据**；
> 阶段 2 的历史结论仍留在那份文件里，别删。

## 0. 一句话

决定**直接复用** `chenaizhang/KernelSU-Style-UI-Kit`（GPL-3.0，108 个 .kt / 1.17 万行）的组件与外壳代码，
把本 app 的外壳换成「液态玻璃 + 药丸导航」的 Miuix 观感。前提条件已办：**整仓许可转 GPL-3.0**
（见 [NOTICE.md](../NOTICE.md)），minSdk 仍是 **26**，模糊按 API 分级降级。

## 1. 已办的闸门（2026-11-01 实测，不要重做）

| 项 | 结论 | 证据 |
|---|---|---|
| 许可 | 整仓 GPL-3.0 | `LICENSE` 已替换；`NOTICE.md` 记录来源与义务 |
| `miuix-blur` 能否进 minSdk 26 的工程 | **能**，靠 `<uses-sdk tools:overrideLibrary="top.yukonga.miuix.kmp.blur"/>` | `:app:processDebugMainManifest` 绿；合并后 `minSdkVersion="26"` |
| `miuix-blur` 自身门槛 | **minSdk 33**（AAR 的 AndroidManifest 写死） | 因此 API 26–32 必须走降级分支，详见 §2 |
| Compose API 可用性 | `Modifier.dropShadow` / `androidx.compose.ui.graphics.shadow.Shadow` 在 1.12.1 存在 | 套件 `FloatingBottomBar.kt` 直接可用，无需降级改写 |
| 参考源码位置 | `.work/ksu-kit/`（已克隆，gitignore） | `app/src/main/java/com/example/kernelsustyleuikit/` |

## 2. minSdk 26 的三档降级（写死，别再改）

| API | 底栏/Sheet 材质 | 实现 |
|---|---|---|
| **33+** | 液态玻璃：真实背景模糊 + 高光 + 内阴影 | `miuix-blur` 的 `Backdrop` / `drawBackdrop` / `Highlight` |
| **31–32** | 半透明填充 + 1dp 描边（无背景模糊） | `Modifier.blur` 只能糊自身，做不出 backdrop，所以退到描边 |
| **26–30** | 不透明 `bgLayer1` + 1dp 描边 | 纯色浮层 |

> **判据**：三档都必须是「同一套代码的三个分支」，不是三份实现。所有 `miuix.kmp.blur` 的符号只允许出现在
> `:design` 的 `glass/` 包内，且调用点必须在 `if (Build.VERSION.SDK_INT >= 33)` 之后 —— 否则 API 26 设备会在
> 加载类时崩溃（`NoClassDefFoundError`），而这在编译期完全看不出来。

## 3. 新的信息架构（用户 2026-11-01 定稿）

```
底栏 4 项（药丸式，恒 4 项，不随状态变化）：
  聊天    → 页内顶部药丸切 Chat / Trajectory / Shell
  文件    → 文件页（后期做成类 VSCode 浏览器，本期先空壳）
  插件    → 插件页（本期先空壳）
  我的    → 设置页：顶部头像+余额，下面一排设置页签，点进去是子页
```

**这推翻了阶段 2 的两个结论，必须写进 PR 描述**：

1. 阶段 2 的底栏是 **3 项**（Chat/Trajectory/Shell），因为当时 Files/Settings 是面板与 Sheet，
   而「条件性项会让底栏宽度在运行中变化」。新方案把 Files 与 Settings **提升为真目的地**，
   所以它们必须变成无条件项 —— 项数恒 4。
2. Shell/Trajectory **退出底栏，改为聊天页内的药丸切换**。它们本来就是「同一个会话的不同视图」，
   提升为顶层目的地从来就有点勉强；新方案把层级理顺了。

> **代价**：`ChatView` 枚举不再是底栏的数据源。`pagerState` 仍然是**唯一**的可写目的地状态
> （阶段 2.5c 的结论继续成立），只是 `pageCount` 从 3 变 4，且 Chat 页内部多一个**独立**的视图选择状态。

## 4. PR 序列（单变量，每步可验证）

| # | 分支 | 内容 | 验收 |
|---|---|---|---|
| **3.0** ✅ | `gate/miuix-blur` | 许可转 GPL + `miuix-blur` 依赖 + manifest override + 本文件 | `:app:processDebugMainManifest` 绿且 `minSdkVersion=26` |
| **3.1** | `feat/liquid-glass` | 移植 `liquid/`（InnerShadow/Lens/Vibrancy/CombinedBackdrop）+ `rememberLiquidGlass` 到 `:design` 的 `glass/` 包，含三档降级 | `:design:testDebugUnitTest` 绿；新增 1 张 `liquid-glass-probe.png` |
| **3.2** | `feat/pill-dock` | 移植 `FloatingBottomBar` + `bottombar/`，改成 4 项药丸底栏，替换现有手写玻璃 | 4 张底栏基线全部重录；原有 14 张中聊天两张预期变化 |
| **3.3** | `feat/four-destinations` | `ChatView` 拆成「4 目的地」+「聊天内 3 视图」两个枚举；pager 改 4 页 | 4 页可切；返回键回 Chat；宽屏 rail 4 项 |
| **3.4** | `feat/files-tab` | 文件页：把现有 `FilesSurface` 提升为目的地（先复用，不做 VSCode） | 新增 `files-tab.png` |
| **3.5** | `feat/plugins-tab` | 插件页空壳（占位 + 说明文案） | 新增 `plugins-tab.png` |
| **3.6** | `feat/me-tab` | 我的页：头像/余额头部 + 设置页签列表；页签点进去是子页 | 新增 `me-tab.png` + `me-subpage.png` |
| **3.7** | `feat/topbar-collapse` | TopAppBar 折叠并入：改用 Miuix `TopAppBar` + `MiuixScrollBehavior`；**随页变，不随滚动**（见 §6） | 聊天基线重录 |
| **3.8** | `feat/status-capsule` | 状态胶囊：连接/运行/待审批 | 新增 1 张胶囊基线 |
| **3.9** | `feat/sheets-miuix` | Sheet/Dialog 统一到移植来的 `Dialog` + `OverlayBottomSheet` | 审批/提问/模型选择三处换装 |
| **3.10** | `feat/drawer-miuix` | 抽屉换装：`SuperSearchBar` + `SegmentedList` | 抽屉两张基线重录 |

## 5. 移植清单（按依赖顺序）

| 源（`.work/ksu-kit/.../kernelsustyleuikit/`） | 行数 | 目标 | 备注 |
|---|---|---|---|
| `ui/component/liquid/InnerShadow.kt` | 160 | `design/.../ui/glass/` | 无 miuix 依赖 |
| `ui/component/liquid/Lens.kt` | 216 | 同上 | 无 miuix 依赖 |
| `ui/component/liquid/Vibrancy.kt` | 15 | 同上 | 无 |
| `ui/component/liquid/CombinedBackdrop.kt` | 39 | 同上 | 依赖 `miuix-blur` |
| `ui/util/BlurExt.kt` | 49 | 同上 | `rememberBlurBackdrop` |
| `ui/component/miuix/animation/DampedDragAnimation.kt` | 145 | `design/.../ui/anim/` | |
| `ui/component/miuix/animation/InteractiveHighlight.kt` | 112 | 同上 | |
| `ui/component/FloatingBottomBar.kt` | 456 | `design/.../ui/nav/` | 首行是 Apache-2.0 归属，保留 |
| `ui/component/bottombar/BottomBar.kt` | 112 | 同上 | `MainPagerState` |
| `ui/component/bottombar/BottomBarMiuix.kt` | 123 | 同上 | |
| `ui/component/dialog/Dialog.kt` + `DialogMiuix.kt` | 493 | `design/.../ui/overlay/` | |
| `ui/component/material/SegmentedList.kt` | 493 | `design/.../ui/list/` | 抽屉/设置用 |
| `ui/component/miuix/SuperSearchBar.kt` | 338 | 同上 | 抽屉搜索栏 |
| `ui/component/miuix/WarningCard.kt` | 85 | `design/.../ui/feedback/` | 危险操作 |
| `ui/component/ScrollToTop.kt` | 90 | 同上 | |

> **不要移植**：`ui/screen/**`（页面内容是本 app 自己的）、`data/`、`permission/`、`markdown/GithubMarkdown.kt`
> （本 app 已有更好的原生 markdown）、`navigation3/`（不引 miuix-nav，见阶段 2 结论）。

## 6. 折叠语义（用户定稿：随页变）

TopBar 折叠**不跟滚动**，改成跟**页**：Chat 页展开（标题 + 目录两行），Trajectory/Shell 压缩（一行）。
这样 Shell/Trajectory 没有 `listState` 的问题自动消失，且切换页时不会有 header 弹跳。

> 这改了阶段 2.6 的原意（原意是滚动折叠），**必须写进 PR 描述**。理由：三个视图共用一个 header，
> 而只有 Chat 有滚动位置；让折叠度跟着一个只有 1/3 页面存在的信号走，必然在另外两页跳变。

## 7. 本机怎么验（不变）

```sh
tools/localbuild.sh . :app:compileDebugKotlin      # 约 1.5–2 分钟（增量）
tools/localbuild.sh . :design:testDebugUnitTest    # 约 2.5–3 分钟
:app 的测试源码仍然编不了（aapt2），基线只能由 CI 录。
```

> 本机 3.9 GB 内存，**不要跑 `assembleDebug`**（会拖到 15 分钟以上且大概率 OOM）。
> 验证 manifest 用 `:app:processDebugMainManifest`。
