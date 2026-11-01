# 交接文档（给新会话）

> 写于 2026-10-31，2026-11-01 更新至第五版。沿革：一版「接管 + 工具链全量升级」，二版「结构拆分开头 + 截图基线」，
> 三版「1.2b」，四版「阶段 1 完成 + 阶段 2 可执行计划」，**本版「阶段 2.5 全部完成（2.5a–2.5d）+ 阶段 2.6 开工清单」**。
> **新会话请先读本文件，再按需查其它文档；动手做阶段 2 前必须读 [PHASE-2-PLAN.md](PHASE-2-PLAN.md)。**

## 1. 一句话现状

仓库 <https://github.com/gitMFQ/dsh-android>（私有，`gitMFQ/dsh-android`）已经是一个**能构建、`tests=1166` 全绿 + 14 张截图基线**的 DSH 原生安卓客户端 fork，工具链升到最新，miuix 已接入且**已验证可用（见 2.3 闸门），并在 2.4 真正用上（调色板适配器已接线）**。

**换皮的安全网已经就位**（Roborazzi，PR #5），**结构拆分的第一个前置条件也已清掉**（`model/` 不再依赖根包，PR #4），**五个模块已经抽出来了**：`:core:model`（PR #6，413 个测试）、`:core:term`（PR #7，186 个测试，**纯 JVM —— 本仓第一次能在本机跑功能测试**）、`:core:config`（PR #8）、`:core:protocol`（PR #9，73 个测试 —— **并抓出第一个生产缺陷**，见 research/13 §2.11）、`:design`（PR #10，7 个测试）。

**阶段 1 已完成**。`:design` 顺带交付了 **7 档外观**（Light / Dark / System / AMOLED / Monet ×3），测试总数 **1116 → 1123**。

**阶段 2 已开工（2026-11-01）**：2.0（窗口底色跟随外观）、2.1（删 `KsuTokens.Space`）、2.2a/b/c（基线 4 → 8 张 + 抽 `TerminalPanel`）、2.3（miuix 闸门 —— 结论是可以）、2.4（浮动底栏 + 调色板适配器 + 状态栏 + ROUTER 出目的地列表）、**2.5a–2.5d（抽 `ChatPanel` + 聊天屏基线 + pager + 宽屏 rail，基线 8 → 14 张）** 全部合入。下一步是 **2.6 TopBar 折叠**，开工清单见 §7.4。

> **2.4 交付了什么，接手的先看 `docs/PHASE-2-PLAN.md` §2.4 的三个结论**：
> 1. **调色板适配器已存在**（`design/.../MiuixPalette.kt`，53 角色），且 `DshTheme` 在根部就套好了 `MiuixTheme` —— 后续 miuix 屏**不需要**再各写一遍适配器，直接摆组件即可。
> 2. **状态栏现在跟 7 档外观走**（`design/.../SystemBars.kt`）。之前 `enableEdgeToEdge()` 跟的是系统夜间模式，四档外观的图标是错的。
> 3. **底栏恒 3 项**（Chat / Trajectory / Shell），ROUTER 改成头部 seat + sheet。要第 4 项得先把 `showFiles` 提升成 `ChatView.FILES`，独立 PR。

> **2.3 闸门的结论，接手的人必须先看这两条**：
> 1. **miuix 能画** —— 但它的 0.9.4 是 **Java 21 字节码**，测试 JVM 已从 17 升到 21（`verify.yml` / `screenshots.yml`）。APK 侧 D8 一直没问题。
> 2. **调色板不会自动同步** —— miuix 默认强调色 `#3482FF` ≠ 本 app 的 `#4176E6`。**2.4 已经把适配器写好了**（`design/.../MiuixPalette.kt`）并接在 `DshTheme` 根部，所以后续 miuix 屏**不必**再各写一遍 —— 但也不要绕过 `DshTheme` 自己 `MiuixTheme(colors = miuixLight())`，那会退回到「配色不跟外观走、而且不报错」。

## 2. 已完成（L1–L4，全部合入 master）

| 级 | 内容 | 结果 |
|---|---|---|
| **L1** | Kotlin 1.9.24 → **2.4.21**；`composeOptions` → `org.jetbrains.kotlin.plugin.compose`；`kotlinOptions` → `kotlin { compilerOptions { } }`；Robolectric 4.13 → 4.17 | 1116 绿 |
| **L2** | AGP 8.5.2 → **8.13.2**；Gradle 8.7 → **8.13**；compileSdk/buildTools/targetSdk → **36** | 1116 绿 |
| **L3** | AGP → **9.3.1**；Gradle → **9.5.0**；compileSdk → **37**；Compose BOM → **2026.09.00**；**移除 `org.jetbrains.kotlin.android`**（AGP 9 内置 Kotlin） | 1116 绿 |
| **L4** | 接入 miuix **0.9.4**（ui / preference / icons / squircle，**不含 blur**） | 1116 绿 + APK |
| **1.0** | 5 个宿主行类（`SessionItem`/`WorkspaceItem`/`JobItem`/`QueueAttachment`/`QueuedMessage`）从 `DshViewModel.kt` 移入 `model/`；`model → 根包` 的 import **14 → 0** | 1116 绿（PR #4） |
| **1.5** | Roborazzi **1.77.0** + 4 张基线（About / Startup / 抽屉 / 抽屉空态），411×891 | `tests=1116` + `screenshots=4`（PR #5） |
| **1.1** ✅ | 抽 `:core:model`（`com.android.library`）：50 个源文件 + 43 个测试文件搬出 `:app`；包名不变所以**全仓零 import 改动**；2 个 `internal` 提权；4 处跨模块 smart cast 修复 | `app: 703` + `model: 413` = `tests=1116` + `screenshots=4`（PR #6） |
| **1.2** ✅ | 抽 `:core:term`（**纯 JVM**）：7 源文件 + 10 测试文件（186 个 `@Test`）搬出 `:app`；`ROUTER_READOUT_TERMINAL_ID` + `isRouterReadoutTerminal` 从 `:core:model` 移入以切断依赖边；`term → model` 边清零 | `app: 517` + `model: 413` + `term: 186` = `tests=1116` + `screenshots=4`（PR #7） |
| **1.2b** ✅ | 抽 `:core:config`（`com.android.library`，`api` 依赖 model + term）：`DshConfig` + `ThemeMode` + `BusyEnter` + `AcceptedCert` + `CertPins`（**除 okhttp 的 `pinner()`**）+ 证书编解码（`CertTrust.encode/decode` → 顶层 `encodeAcceptedCerts`/`decodeAcceptedCerts`）。`pinner()` 变 `:app` 的**扩展函数**，调用点零改动 | `tests=1116` + `screenshots=4`（PR #8；`app: 517` / `model: 413` / `term: 186`，`config: 0` 是预期，见 research/13 §2.9） |
| **1.3** ✅ | 抽 `:core:protocol`（`com.android.library`，`api` 依赖 model + term + config + okhttp）：`net/` **14** 文件 + **7** 个测试文件（73 个 `@Test`）；`DshClientResetTest` 留 `:app`；`hostOf` + `CertProbe.systemTrusts` 提权；`multipart-real.bin` 随测试走。**顺带修掉一个生产缺陷**：空信任锚集合时 PKIX 抛 `RuntimeException`，被 `catch (CertificateException)` 漏掉，导致全新安装永远拿不到接受证书的入口 | `tests=1116` + `screenshots=4`（PR #9；`app: 444` / `protocol: 73`） |
| **1.4a** ✅ | 抽 `:design`（`com.android.library` + **Compose 编译器插件**）：`ui/theme/DshTheme.kt` 搬入；`:app` 零 import 改动 | `tests=1116` + `screenshots=4`（PR #10） |
| **1.4b** ✅ | 7 档外观 + `KsuTokens`。外观做成**变换**而非 7 份调色板（AMOLED 取暗色调色板把表面压黑；Monet 只取壁纸的主色族与底色，语义色不动）。**顺带修掉一个真冲突**：host 的外观枚举只有 light/dark/system，而 `adoptHostSetting` 会把它写回本地 —— 接上 host 的会话会把 AMOLED/Monet 静默重置。拆成 `themeMode`（本机）与 `hostThemeMode`（镜像）两个字段 | `tests=1123`（`config: 7`）+ `screenshots=4`（PR #10） |
每个 PR 都是**单一变量**，失败时能立刻定位。这个方法论请延续。

### 2.0 阶段 2 进展（2026-11-01，六个 PR）

| # | PR | 内容 | CI 证据 | PR |
|---|---|---|---|---|
| 2.0 | `fix/window-background` | 窗口底色跟随 7 档外观 | `tests=1136` / `screenshots=4` | #11 |
| 2.1 | `refactor/drop-ksu-spacing` | 删 `KsuTokens.Space` | 均不变 | #12 |
| 2.2a | `test/baseline-expand` | 补 Composer / Files / Permission 三张 | `screenshots=7` | #13 |
| 2.2b | `refactor/terminal-stateless` | 抽 `TerminalPanel`，纯搬运 | 7 张逐字节不变 | #14 |
| 2.2c | `test/baseline-terminal` | 补 `terminal.png` | `screenshots=8` | #15 |

**三个值得记住的结论**：

1. **2.0 的原计划写错了**：「`MainActivity` 在 Compose 之前就持有 `ui.themeMode`」只在 `setContent` **之内**成立。真实做法是把调色板决议提成纯函数 `dshPalette()`，让 `ConfigStore` 记住外观，在 `onCreate` 里先画 `decorView`。**「窗口」和「Compose」是两棵树，基线看不到窗口** —— 这也解释了为什么 2.0 需要新增测试（+13）而基线一张不动。
2. **基线之间可以互相验证**：2.2b 是纯搬运，判据不是「我看了 diff 觉得是搬运」，而是 **2.2c 录制时另外 7 张 sha256 逐字节相同**。先 refactor 再 baseline、且分成两个 PR，才有这条证据。
3. **能补的基线已经补完了**。剩聊天主界面 / Trajectory / Router 抓不到，因为**入口就是 view model**（`ChatScreen(vm)`）；要给它们录基线必须先拆无状态屏，那是独立重构 PR，不能塞进「只加截图」的 PR。

### 2.1 截图基线怎么用（新增，重要）

| 想知道 | 做法 |
|---|---|
| 换皮后有没有动到别的界面 | `verify.yml` 的 `Screenshot gate` 步骤会红；下载 `roborazzi-comparison` artifact 看 `*_compare.png` |
| 基线该更新了 | 手动跑 `Screenshots` workflow（`workflow_dispatch`），下载 `roborazzi-baselines`，覆盖 `app/src/test/screenshots/` 后提交。**可以直接录某个分支**：`gh workflow run screenshots.yml --ref master -f branch=<分支名>` —— 在默认分支上派发（工作流只在默认分支上可触发），checkout 的却是目标分支。2.2a / 2.2c 都是这么录的，比 PR #5 当年「临时加 `push:` 触发再删掉」干净 |
| 新基线怎么加 | 在 `ScreenBaselineTest` 加一个 `@Test` → 走上面的录制流程 → 从 artifact 里取出 PNG 提交。**本机录不了**（aarch64 无可用 aapt2） |
| 为什么不能在本机录 | aarch64 + 无可用 aapt2，`recordRoborazziDebug` 跑不了（§3 第一条） |

**基线只手动录**。若改成 push 自动录，基线会跟着代码走，比对就变成「自己和自己比」，门禁等于没有。

**两个数字别合并**：`tests=1136`（功能测试，每个 PR 的证据）与 `screenshots=8`（像素基线）。CI 分开打印，合并成一个数会让「少跑一个测试」和「多一张基线」长得一模一样。
>
> **`tests` 从 1116 涨到 1123 是 1.4b 有意为之**（+7 个 `ThemeMode` 测试）。1116 那条不变量只约束**纯搬运 PR**；功能 PR 的判据是「原有测试一个不少 + 新增测试有理由」。
>
> **再涨到 1136 是 2.0 有意为之**（+8 `DshPaletteTest` / +5 `WindowBackgroundTest`）。理由很具体：那一步改的是**窗口**，而截图基线抓的是 **Compose 树**，结构上永远看不到窗口 —— 没有这两组测试，2.0 就是一个**零证据**的 PR。`screenshots` 同期从 4 涨到 8（2.2a 三张 + 2.2c 一张）。

## 3. 关键约束（踩过的坑，别再踩）

| 约束 | 原因 |
|---|---|
| **本机不能完整构建** | aarch64 + 无可用 aapt2。`qemu-user` 能跑 x86_64 aapt2，但 AGP 的 `Custom AAPT2 location does not point to an AAPT2 executable` 校验过不去（shell 脚本和自编译 ELF 都被拒）；`binfmt_misc` 因容器无 `CAP_SYS_ADMIN` 无法注册。**所有验证走 CI。** |
| **网络需代理** | `dl.google.com` / `repo.maven.apache.org` 单文件可达但 **Gradle 并行解析器会卡死**。`settings.gradle.kts` 已把 Aliyun 镜像放最前。`git push` 偶尔 GnuTLS 失败，加 `ALL_PROXY=http://127.0.0.1:7890` 重试。 |
| **AGP 9 不允许 `org.jetbrains.kotlin.android`** | 内置 Kotlin，应用该插件直接 build fail。`kotlin { compilerOptions { } }` 仍可用。 |
| **不引 `miuix-blur`** | 模块 `minSdk = 33`，我们 26。玻璃效果自写（`Modifier.blur` API31+ + 降级）。 |
| **miuix 是 Java 21 字节码**（2.3 实测） | 0.9.4 的 class file 版本是 **65**。D8 打进 APK 没问题，但**任何加载它的 JVM 必须 ≥21** —— 测试 JVM 卡在 17 时表现为 `UnsupportedClassVersionError`，而且是在**用到 miuix 的那一行**才炸，编译期完全正常。`verify.yml` / `screenshots.yml` 已改 21。 |
| **miuix 的配色不会跟 `DshTheme` 走**（2.3 实测） | 不给 `MiuixTheme` 参数时用的是 miuix 自己的 `#3482FF`，本 app 亮色强调色是 `#4176E6`。**每个 miuix 屏都要先有 `DshColors → miuix Colors` 适配器**（`Colors` 有 `copy`，可行），否则 7 档外观换不动它，且不报错。 |
| **`Screenshots` workflow 用哪份文件** | 用**被 `--ref` 指定那个分支上的 workflow 文件**。`--ref master -f branch=feat/x` 跑的是 master 的 JDK/步骤，只是 checkout 了 feat/x 的代码。改了 workflow 的分支要录基线，必须 `--ref feat/x`。2.3 第一次就是这么失败的。 |
| **不引 `miuix-nav`** | 要求所有路由改成 `@Serializable NavKey`，收益不抵成本。 |
| **Compose 1.11 的 Password 语义 bug** | `CoreTextFieldSemanticsModifier.updateNodeSemantics` 从不写回 `this.isPassword`，`BasicTextField(value=)` 路径的 Password 语义无法变化。测试已改为断言可见文本。 |
| **跨模块 smart cast 编不过** | Kotlin 不能对「声明在另一个模块里的属性」做 smart cast（`val` 也不行，编译器无法证明别的模块不会改）。抽模块后 UI 里所有 `x.prop is T && x.prop.y` / `x.prop != null && x.prop.z` 都会报 `Smart cast ... is impossible ... declared in different module`。**修法：先把属性读进局部变量再判断**，不要改成 `var`、不要加 `!!`。1.1 一次踩了 4 处。 |
| **本机可以编译 Kotlin 了**（1.1 起） | `:app:compileDebugKotlin` 只需 build-tools 能解析，**不依赖 aapt2**。本机需临时把 `platforms/android-37/source.properties` 的 `ApiLevel` 从 `37.0` 改成 `37`，并把两处 `buildToolsVersion` 临时改 `36.0.0`（**验证完还原**）。改 UI/抽模块时先在本机把编译错误收干净，别用 CI 的 7 分钟试错。详见 [BUILD-AND-CI.md](BUILD-AND-CI.md) §0.0b。 |
| **本机可以跑 `:core:term` 的 186 个测试了**（1.2 起） | 纯 JVM 模块不碰 aapt2，**不需要任何本机 hack**：`bash build.sh :core:term:test` 直接出 `tests=186 failures=0`（约 4 分半）。`:core:model` 的 413 个仍跑不了（它是 Android library，`processDebugUnitTestResources` 挂在 aapt2）。这是往纯 JVM 拆的真收益，不只是模块漂亮。 |
| **纯 JVM 模块不能依赖 Android library**（1.2 踩到） | 变体匹配直接失败，补 attribute 也救不回来。要么把依赖边拆掉（1.2 的做法），要么该模块就用 `com.android.library`。**判据是依赖闭包，不是源码里有没有 `import android`**。详见 research/13 §2.6。 |
| **`kotlin("jvm")` 的版本只能写在根 build** | AGP 9 已把 Kotlin 放上 build classpath，模块里写版本会报 `already on the classpath with an unknown version`。根 `build.gradle.kts` 加 `apply false`，模块只写 `kotlin("jvm")`。 |
| **`:core:term` 必须保持零出边**（不变量） | 它是唯一能在本机跑完整测试的模块，前提是**纯 JVM + 它自己不依赖任何 Android 库**。1.2b 的「`DshConfig` 进 `:core:model`」之所以被否，是那样会让 **`:core:model` → `:core:term`**，而 `DshConfig` 真正需要的是 `TerminalCellSize`，不是反过来。**别人依赖 term 是对的**（`:core:config` 现在就是 `api(project(":core:term"))`，合法）；**term 依赖别人是错的**。方向永远是 **term ← 别人**。 |

## 4. 仓库结构

```
/root/dsh-android
  app/                     # 主模块（fork 自 bqv/dsh-android v0.6.6），抽走五个模块后仍是测试主体
    src/main/java/uk/xa0/dsh/
      net/                 # 协议层（已抽到 :core:protocol，这里只剩接线）
      ui/                  # 界面 21 文件 / 14k 行（ChatPanel.kt 3.4k 是最大的一块）
      data/ widget/ diag/
    src/test/              # 476 个功能 @Test + 14 张截图基线（含 WideBaselineTest）
  core/model/              # 已抽出：领域模型 50 文件 + 43 个测试文件（413 个 @Test），com.android.library
  core/term/               # 已抽出：终端 7 文件 + 10 个测试文件（186 个 @Test），纯 kotlin("jvm")
  core/config/             # 已抽出：DshConfig + ThemeMode + BusyEnter + AcceptedCert + CertPins(JDK 部分)，com.android.library，0 测试
  core/protocol/           # 已抽出：net/ 14 文件 + 7 个测试文件（73 个 @Test），com.android.library
  design/                  # DshTheme.kt（主题 + 7 档外观）+ KsuTokens.kt + MiuixPalette.kt + SystemBars.kt + WindowBackground.kt，25 个测试
  docs/                    # 规划文档（见 §6）
  .github/workflows/       # build.yml（出 APK）/ verify.yml（测试+截图门禁）/ release.yml / screenshots.yml（手动录基线）
  build.sh                 # CI 用构建入口，找 .toolchain/gradle-9.5.0（写死 JDK 17）
  tools/localbuild.sh      # 本机验证入口：JDK 21 + 临时改 build-tools，trap 还原（见 §7.4.6）
```

## 5. CI

| 工作流 | 触发 | 作用 |
|---|---|---|
| `verify.yml` | push main/master + PR | `tests=1166 failures=0` + `screenshots=14` 像素比对 |
| `build.yml` | 同上 | 出 debug APK（约 20 MB） |
| `release.yml` | tag `v*` | 出 release APK 挂 Release |
| `screenshots.yml` | **手动** `workflow_dispatch` | 在 CI 录基线并上传 artifact（本机录不了） |

**提交 PR 即可验证**，`gh pr checks <n>` 看结果，约 7–10 分钟。

> `screenshots.yml` 是 `workflow_dispatch`，**只在默认分支上存在时才可手动触发**。新加的工作流要先把文件推到 master（或临时加 push 触发）才能跑第一次 —— PR #5 踩过，见 §8。

## 6. 文档地图（按需查，别全读）

| 想知道 | 读 |
|---|---|
| 下一步做什么 | [ROADMAP.md](ROADMAP.md) —— 阶段总表 |
| **阶段 2 怎么做** | **[PHASE-2-PLAN.md](PHASE-2-PLAN.md)** —— 可执行的 PR 序列、每步验收、开工检查清单 |
| 版本为什么是这些 | [research/10-version-lock.md](research/10-version-lock.md) |
| 构建/CI/本机限制 | [BUILD-AND-CI.md](BUILD-AND-CI.md) |
| 设计规范 | [DESIGN-TOKENS.md](DESIGN-TOKENS.md) |
| 协议细节 | [research/01-protocol.md](research/01-protocol.md) |
| API 清单 | [research/02-api-surface.md](research/02-api-surface.md) |
| 模块拆分的可执行步骤 | [research/13-module-extraction.md](research/13-module-extraction.md) |
| 多语言 | [I18N.md](I18N.md) |
| 插件化（1.0 后） | [PLUGIN-PLAN.md](PLUGIN-PLAN.md) / [BLOCK-SCHEMA.md](BLOCK-SCHEMA.md) |
| 全部索引 | [README.md](README.md) |

## 7. 下一步（建议顺序）

### 7.0 ✅ 已做：Roborazzi 截图测试

见 §2.1。4 张基线已在 master，`verify.yml` 已把它做成门禁。**不需要再做。**

### 7.1 阶段 1：结构拆分（**顺序已修正**）

⚠️ **原文写的「1.1 先抽 `net/`」执行不了**：`net → data` 成环。完整证据与可执行步骤见 [research/13-module-extraction.md](research/13-module-extraction.md)。正确顺序：

| # | 任务 | 内容 | 验收 |
|---|---|---|---|
| 1.0 ✅ | 宿主行类移入 `model/` | `model → root` 边 14 → 0 | 1116 绿（PR #4） |
| 1.1 ✅ | 抽 `:core:model`（`com.android.library`） | 50 文件 + 43 个纯 JVM 测试（413 个 `@Test`）；4 个测试留 `:app`；2 个 `internal` 提权；4 处跨模块 smart cast | 独立绿 + 全仓仍 1116（PR #6，`app: 703` / `model: 413`） |
| 1.2 ✅ | 抽 `:core:term`（**纯 JVM**，`kotlin("jvm")`，PR #7） | 7 文件 + 10 个测试文件 / 186 个 `@Test`；0 个 `internal`；readout 的 id + 谓词从 `:core:model` 移入以切断依赖边 | 仍 1116（PR #7，`app: 517` / `model: 413` / `term: 186`） |
| 1.2b ✅ | 抽 **`:core:config`**（`com.android.library`，PR #8） | ⚠️ **原计划「进 `:core:model`」已证伪**（`DshConfig` 闭包横跨 model+term；`AcceptedCert → CertPins → okhttp`）。独立模块：`DshConfig` + `ThemeMode` + `BusyEnter` + `AcceptedCert` + `CertPins`（除 `pinner()`）+ 编解码。`pinner()` 成 `:app` 扩展函数，调用点零改动。详见 research/13 **§2.7 / §2.8 / §2.9** | 仍 1116（PR #8，`config: 0` 是预期：源码搬、测试留） |
| 1.3 ✅ | 抽 `:core:protocol`（`net/`，PR #9） | **14** 文件；`com.android.library`；提权 **2** 个（`hostOf` + `CertProbe.systemTrusts`）；**环已解，不需要 M4** | 仍 1116（`app: 444` / `protocol: 73`）。**并抓到空信任锚集合的生产缺陷**（详见 research/13 **§2.11**） |
| 1.4 ✅ | 建 `:design` 模块：`KsuTokens` + 主题 **7 档外观**（PR #10） | 设置页可切外观并持久化 —— **已交付**，`tests=1123` |

> **抽模块时必改两处**（1.1 实证，两处都会让 CI「绿着丢测试」）：
> 1. `verify.yml` 的汇总 glob：`app/build/test-results/**` → `**/build/test-results/**`；
> 2. **新模块的测试任务要在 `verify.yml` 和 `build.yml` 里显式写名字**（`bash build.sh :app:testDebugUnitTest :core:model:testDebugUnitTest`）。**依赖一个模块不会跑它的测试** —— 这一条比 glob 更容易漏，因为 CI 完全绿。
>
> 汇总现在还会打印分模块数字，单个总数已经不足以证明每半都跑了。第 2 条每加一个模块都要再写一遍（1.2 起还要加 `:core:term:test`，1.2b 起还要加 `:core:config:testDebugUnitTest`）。

> **1.2b 的一个反例，别把「测试数没涨」当漏搬**：`:core:config` 搬的是**源码**，`CertTrustTest`（12 个 `@Test`）留在 `:app` —— 它同时用 `CertTrust`（M3 才走），现在搬进去就编不过。**模块建好了、测试数没变，是正确结果。** 但任务名仍要写进两个 workflow：现在跑 0 个，M3 搬进来的才会真的跑。

> **⚠️ 1.2b 之后 `net/` 的状态变了，M3 前必读 [research/13](research/13-module-extraction.md) §2.10**：
> 1. **`net → data` 环已不存在** —— `DshClient.kt:25` 那行 import 现在指向 `:core:config` 的 `DshConfig`。**M3 不再被 M4 阻塞。**
> 2. **`pinner()` 已是一个独立文件**（`net/CertPinning.kt`），整体搬即可，不用再从 `CertPins` 里切一次。
> 3. **要提权的是 2 个 `internal`**（`hostOf` + `CertProbe.systemTrusts`），原文只写了 1 个。
> 4. **`:core:protocol` 必须是 `com.android.library`**，不能照 `:core:term` 用 `kotlin("jvm")`。
>
> 教训（值得记）：**包名不变（方案 A）让「一条 import 指向哪个模块」看不出来** —— 源码一个字没改，依赖闭包却换了模块，而 CI 编译是通过的，不会报错。判断归属只能看**符号现在住在哪个模块**。

> **1.2b 为什么不是「塞进 `:core:model`」**（复盘，[research/13](research/13-module-extraction.md) **§2.7 + §2.8**）：
> 原文推荐的「`AcceptedCert` + `DshConfig` 进 `:core:model`」**已实测证伪**。三条阻塞：
> `AcceptedCert` 的属性依赖 `CertPins` → okhttp；`DshConfig` 依赖 `:core:term`（会逼 model 反向依赖纯 JVM 模块）；
> `encode`/`decode` 卡在 `CertTrust.companion object` 里。**正解是独立 `:core:config`。**
>
> 反复出现的判据（§2.6 / §2.8 共用）：**看依赖闭包，不看源码里有没有 `import android`。**
> 「源码纯」是必要条件，不是充分条件。


### 7.2 阶段 2：外壳换装 —— **详细计划见 [PHASE-2-PLAN.md](PHASE-2-PLAN.md)**

一句话：浮动底栏 + pager / TopBar 折叠 / 抽屉换装 / 设置页换装 / Sheet 统一。

> **⚠️ 2.0–2.5 全部完成，别再照着旧文开工。当前进度与下一步见 §7.4。**
>
> 已完成：2.0（窗口底色跟随外观）→ 2.1（删 `KsuTokens.Space`）→ 2.2a/b/c（基线 + 抽 `TerminalPanel`）→ 2.3（miuix 闸门）→ 2.4（浮动底栏 + 调色板适配器 + 状态栏 + ROUTER 出目的地列表，PR #17）→ **2.5a（抽 `ChatPanel`，会话屏终于可拍照）**。
>
> **阶段 2.5 已全部完成**：2.5a（抽 `ChatPanel`）→ 2.5b（聊天屏基线 + 抓到两个真缺陷）→ 2.5c（pager）→ 2.5d（宽屏 rail）。
>
> 剩余：2.6（TopBar 折叠）→ 2.7（抽屉换装）→ 2.8（设置/关于换装）→ 2.9（Sheet 统一）。
>
> **2.5 的四条结论见 [PHASE-2-PLAN.md](PHASE-2-PLAN.md) §2.5a / §2.5b / §2.5c**：`ChatPanel` 收整个 `UiState` 而不拆字段；拆分时最容易丢的是没被测试覆盖的那条连线；`diag/` 里的帧循环会让任何截图尝试失败；`pagerState` 是唯一可写状态，`view` 必须派生。

### 7.3 ✅ 阶段 2.5 已完成（**本节只作历史记录，别照着开工**）

2.5a–2.5d 全部合入。原始开工清单已随工作完成而作废，保留的只有三条**仍然成立**的结论：

| 仍然成立 | 现状 |
|---|---|
| 底栏恒 3 项 | `BOTTOM_BAR_ITEMS` + `ChatView` 仍是三个值；2.5c 只改「怎么显示」，没改「有几个」 |
| 不写第二份「当前目的地」状态 | `pagerState` 是唯一可写状态，`view = ChatView.entries[pagerState.currentPage]` 是派生值 |
| 不引 `material3-window-size-class` | 它**在类路径上有两份**（`org.jetbrains.compose` 与 `androidx`）。2.5d 改用 `BoxWithConstraints`，零依赖 |

> 2.5 的完整结论见 [PHASE-2-PLAN.md](PHASE-2-PLAN.md) **§2.5a / §2.5b / §2.5c**。

### 7.4 阶段 2.6 开工清单（TopBar 折叠 + 状态胶囊）

> 写于 2026-11-01，2.5 全部合入之后。**动手前先读 [PHASE-2-PLAN.md](PHASE-2-PLAN.md) §2.5c**，那里解释了 pager 之后「当前目的地」只有一个可写来源。
>
> 当前 master 实测：`git log --oneline -1` = `4038d8b`；`src @Test = 1180`；`screenshots = 14`；CI 报 `tests=1166` / `screenshots=14`。
> （`1180` 与 `1166` 差 14，正是截图类；两个数字别合并，见 §2.1。）

#### 7.4.1 2.6 现在**没有**可执行计划，这一节是「开工前必须先定的三件事」

`ROADMAP.md` 与 `PHASE-2-PLAN.md` 的 PR 表里，2.6 都只有一行：「TopBar 折叠 + 状态胶囊 / 滚动折叠无跳变」。**这不足以开工**，因为下面三个问题都还没有答案，而且每一个都会改变实现。

#### 7.4.2 待定一：滚动信号与 header 在同一个作用域，但**语义**要选

先说清楚，免得重走弯路：

- `listState` 在 `ChatPanel.kt:284`，`ChatHeader` 的调用点在 `:1021`，两者**都在同一个 `ChatPanel` 函数体内**。
- 消费 `listState` 的 `LazyColumn` 在 `:1368`，位于 pager 的 CHAT 页里。

所以 header **本来就能读到** `listState` —— pager 只是布局容器，不是作用域边界。折叠所需的 `listState.firstVisibleItemScrollOffset` 直接可用，**不需要提升状态，也不需要 CompositionLocal**。

> 真正要决定的是**语义**：Shell 与 Trajectory 页**没有** `listState`。切到那两页时 header 该保持折叠还是展开？三种做法都自洽，但只能选一个：
> 1. 只在 CHAT 页折叠，切页即展开（最简单，但切页会看到 header 弹一下）；
> 2. 记住离开 CHAT 时的折叠度并保持（要额外 `remember`，且 Trajectory 自己的滚动不同步）；
> 3. header 不随滚动折叠，改成随**页**变（Chat 展开、其余压缩）。**这条最省事且不会跳变，但等于改掉了 2.6 的原意，要写进 PR 描述。**

#### 7.4.3 待定二：「状态胶囊」是新组件，还是重新包装已有的三个？

`ChatPanel` 里已经有三样东西在表达状态，**新胶囊必须先和它们对齐，否则会出现第四种说法**：

| 已有 | 位置 | 表达什么 |
|---|---|---|
| `ConnectionPill(label)` | `ChatPanel.kt:3366` | 连接非 OPEN 时的原因，浮在会话上方 |
| `ErrorBanner` | `:3317` | `ui.error`，带「Sign in again」动词 |
| `JobsSeat` 里的 `StateDot` | `:2536` 调用、`components/JobsCard.kt:79` 定义 | 后台任务在跑 |

要折叠进胶囊的三类状态（连接 / 运行 / 待审批）**在 `ui` 上都有现成字段**：`connectionLabel`（`:809` 读出）、`ui.running`、`ui.approval` / `ui.questions`。

> **判据**：胶囊若只是把这三者**并排画小一号**，那是纯 UI 改动；若它要**替代** `ConnectionPill` 的位置或 `ErrorBanner` 的措辞，那就是行为改动，必须单独一个 PR。**开工前先在 PR 描述里写死是哪一种。**

#### 7.4.4 待定三：header 高度是**被测量的**，折叠会牵动一个已有偏移

`headerPx` / `bannerPx`（`:853-854`）由 `onSizeChanged` 写入，被 `:1212` 的 `LoadingSessionBody` 偏移用来把加载内容对到视口中心：

```kotlin
.offset { IntOffset(0, -(headerPx + bannerPx) / 2) }
```

header 一旦能折叠，`headerPx` 就会随滚动变 —— **那个偏移会跟着抖**。两条路：

1. 折叠用 `graphicsLayer` / `translationY` 而不是改高度 → `headerPx` 不变，偏移不受影响（**推荐**，也是「无跳变」最容易达成的做法）；
2. 折叠真的改高度 → 必须让偏移读一个**稳定的** header 高度（例如折叠前的值），否则加载态会随滚动上下漂。

#### 7.4.5 开工顺序（建议，单变量）

| # | 分支 | 内容 | 验收 |
|---|---|---|---|
| **2.6a** | `test/baseline-header` | 给 `ChatHeader` 单独录基线（展开态）。它现在是 `private`，要先提到 `internal` 才能被 fixture 调用 —— **这是生产代码变更，但只有可见性** | 新增 1 张；原有 14 张逐字节不变 |
| **2.6b** | `feat/topbar-collapse` | 折叠本体。按 §7.4.2 选定的语义实现；优先用 §7.4.4 的第 1 条 | `chat-transcript.png` / `chat-wide.png` 预期变化，其余不变 |
| **2.6c** | `feat/status-capsule` | 状态胶囊。按 §7.4.3 选定的那种做 | 新增 1 张胶囊基线 |

> **先 2.6a 再 2.6b**，理由和 2.2b→2.2c、2.5a→2.5b 完全一样：header 一旦开始动，没有基线就只能靠肉眼。而 `ChatHeader` 目前**没有**单独基线 —— 它只出现在 `chat-transcript` 的整屏里。

#### 7.4.6 本机怎么验（与 2.5 相同，不变）

```sh
tools/localbuild.sh . :app:compileDebugKotlin
tools/localbuild.sh . :design:testDebugUnitTest
```

`:app` 的测试源码**仍然编不了**（aapt2）—— 2.6 新增的基线只能由 CI 验。

#### 7.4.7 一条 2.5 留下的、**尚未在设备上验证**的事

pager 之后，抽屉的左缘滑动要先穿过 pager。预期在 CHAT 页仍能开抽屉（第 0 页往右无处可去，不能移动的 scrollable 不应吃掉手势），但**本机跑不了 `:app` 的 Compose 测试，所以这是预期而不是测量**。代码里已在 pager 的注释标了 `unverified`。

> **接手后第一件事**：在真机上试一次「从屏幕最左缘往右滑」。若抽屉打不开，修法是**在 `DRAWER_EDGE` 范围内关掉 pager 的 `userScrollEnabled`**，而不是把抽屉的感应带加宽。

## 8. 给新会话的提醒

- **别重新调研**：`docs/research/` 有 13 份带 `path:line` 证据的文档，结论都在里面（新增 13 是模块拆分蓝图）。
- **别信旧结论**：文档里凡是标注「已被 X 修正」的，以修正后的为准。
- **单变量原则**：一次只改一个版本/一件事，CI 绿了再进下一步。
- **新增的 `workflow_dispatch` 工作流在合并到默认分支前无法手动触发**：GitHub 只在默认分支上发现该工作流。要第一次跑起来，得先把文件合进 master，或临时加一个 `push:` 触发（PR #5 用了后者，随后删掉）。
- **别用 `--tests` 去「打开」被 `excludeTestsMatching` 排除的测试类**：Gradle 的 filter 不能再包含已被另一个 filter 排除的东西，会报 `No tests found for given includes`。用 `-Pscreenshots` 这类 property 切换（PR #5 踩过）。
- **本机能跑 Gradle 配置阶段；1.1 起能跑 `:app:compileDebugKotlin`；1.2 起能直接跑 `:core:term:test`**（纯 JVM，无需 hack）。（`.toolchain/gradle-9.5.0` 已就位）。改 build 脚本可先本地验语法；改 Kotlin 代码可先本地编译。两个前提见 §3「本机可以编译 Kotlin 了」与 [BUILD-AND-CI.md](BUILD-AND-CI.md) §0.0b，**临时改动验证完必须还原**。测试与资源打包仍只能走 CI（`processDebugUnitTestResources` 挂在 x86_64 aapt2）。
- **`:core:term` 的测试任务是 `:test`，不是 `testDebugUnitTest`** —— 它是纯 JVM 模块，没有 `debug` 变体。写错任务名 Gradle 会报 `Task not found`，比静默跳过好，但仍要注意。
- **「能不能跑通」的最终判断仍由 CI 回答**：本机能编译不代表能跑测试，更不代表像素门禁通过。
- **权限**：仓库是私有的，`gh` 已登录 `gitMFQ`。要公开需先处理 MPL-2.0 源码公开义务（转公开正好满足）。
