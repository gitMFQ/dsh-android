# 交接文档（给新会话）

> 写于 2026-10-31（当日第四版）。沿革：一版「接管 + 工具链全量升级」，二版「结构拆分开头 + 截图基线」，
> 三版「1.2b」，**本版「阶段 1 全部完成（含 1.3 / 1.4 + 7 档外观）+ 阶段 2 可执行计划」**。
> **新会话请先读本文件，再按需查其它文档；动手做阶段 2 前必须读 [PHASE-2-PLAN.md](PHASE-2-PLAN.md)。**

## 1. 一句话现状

仓库 <https://github.com/gitMFQ/dsh-android>（私有，`gitMFQ/dsh-android`）已经是一个**能构建、`tests=1165` 全绿 + 11 张截图基线**的 DSH 原生安卓客户端 fork，工具链升到最新，miuix 已接入且**已验证可用（见 2.3 闸门），并在 2.4 真正用上（调色板适配器已接线）**。

**换皮的安全网已经就位**（Roborazzi，PR #5），**结构拆分的第一个前置条件也已清掉**（`model/` 不再依赖根包，PR #4），**五个模块已经抽出来了**：`:core:model`（PR #6，413 个测试）、`:core:term`（PR #7，186 个测试，**纯 JVM —— 本仓第一次能在本机跑功能测试**）、`:core:config`（PR #8）、`:core:protocol`（PR #9，73 个测试 —— **并抓出第一个生产缺陷**，见 research/13 §2.11）、`:design`（PR #10，7 个测试）。

**阶段 1 已完成**。`:design` 顺带交付了 **7 档外观**（Light / Dark / System / AMOLED / Monet ×3），测试总数 **1116 → 1123**。

**阶段 2 已开工（2026-11-01）**：2.0（窗口底色跟随外观）、2.1（删 `KsuTokens.Space`）、2.2a/b/c（基线 4 → 8 张 + 抽 `TerminalPanel`）、2.3（miuix 闸门 —— 结论是可以）、**2.4（浮动底栏 + 调色板适配器 + 状态栏 + ROUTER 出目的地列表）** 全部合入。下一步是 **2.5 pager 导航**。

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
  app/                     # 主模块（fork 自 bqv/dsh-android v0.6.6），抽走三个模块后仍是测试主体
    src/main/java/uk/xa0/dsh/
      net/                 # 协议层（~4000 行；下一步抽 :core:protocol）
      ui/                  # 界面 54 文件 / 27k 行
      data/ widget/ diag/
    src/test/              # 517 个功能 @Test + 4 张截图基线 + 4 个依赖 :app 的 model 测试
  core/model/              # 已抽出：领域模型 50 文件 + 43 个测试文件（413 个 @Test），com.android.library
  core/term/               # 已抽出：终端 7 文件 + 10 个测试文件（186 个 @Test），纯 kotlin("jvm")
  core/config/             # 已抽出：DshConfig + ThemeMode + BusyEnter + AcceptedCert + CertPins(JDK 部分)，com.android.library，0 测试
  core/protocol/           # 已抽出：net/ 14 文件 + 7 个测试文件（73 个 @Test），com.android.library
  design/                  # 已抽出：DshTheme.kt（主题 + 7 档外观）+ KsuTokens.kt，com.android.library，7 个测试
  docs/                    # 规划文档（见 §6）
  .github/workflows/       # build.yml（出 APK）/ verify.yml（测试+截图门禁）/ release.yml / screenshots.yml（手动录基线）
  build.sh                 # 唯一构建入口，找 .toolchain/gradle-9.5.0
```

## 5. CI

| 工作流 | 触发 | 作用 |
|---|---|---|
| `verify.yml` | push main/master + PR | `tests=1136 failures=0` + `screenshots=8` 像素比对 |
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

> **⚠️ 2.0–2.4 全部完成，别再照着旧文开工。当前进度与下一步见 §7.3。**
>
> 已完成：2.0（窗口底色跟随外观）→ 2.1（删 `KsuTokens.Space`）→ 2.2a/b/c（基线 + 抽 `TerminalPanel`）→ 2.3（miuix 闸门）→ **2.4（浮动底栏 + 调色板适配器 + 状态栏 + ROUTER 出目的地列表，PR #17）**。
>
> 剩余：2.5（pager / rail）→ 2.6（TopBar 折叠）→ 2.7（抽屉换装）→ 2.8（设置/关于换装）→ 2.9（Sheet 统一）。

### 7.3 阶段 2.5 开工清单（2.4 已合入 master）

> 写于 2026-11-01，2.4（PR #17）合入之后。**动手前先读 [PHASE-2-PLAN.md](PHASE-2-PLAN.md) §2.4 的三个结论**，那里解释了为什么调色板适配器、状态栏、ROUTER 都已经就位。
>
> 当前 master 实测：`git log --oneline -1` = `60d8ee2`；`src @Test = 1176`；`screenshots = 11`；CI 报 `tests=1165` / `screenshots=11`。

#### 7.3.1 先确认三件事已经不用做了

2.4 刻意把它们提前做完了，**2.5 不要再做一遍**：

| 已经就位 | 位置 | 对 2.5 的意义 |
|---|---|---|
| 调色板适配器 | `design/.../MiuixPalette.kt` + `DshTheme` 根部套 `MiuixTheme` | 直接用 miuix 组件，**不要再写适配器**，也不要绕过 `DshTheme` 自己 `MiuixTheme(colors = …)` |
| 状态栏跟随外观 | `design/.../SystemBars.kt` | 新屏不需要管状态栏 |
| 底栏恒 3 项 | `ChatScreen.kt` 的 `BOTTOM_BAR_ITEMS` | **条目数是常量**，2.5 的 pager 必须沿用这个前提 |

#### 7.3.2 依赖：两个都不用新引

实测过，别凭直觉加依赖：

- **`HorizontalPager` / `rememberPagerState` 已经在 `androidx.compose.foundation` 里**（`foundation.aar` 里 `androidx/compose/foundation/pager/` 共 61 个 class），而 `:app` 已经 `implementation("androidx.compose.foundation:foundation")`（`app/build.gradle.kts:183`）。**零新增依赖。**
- **`material3-window-size-class` 已经作为 miuix 的传递依赖在类路径上**（`miuix-ui/0.9.4` 的 `metadataApiElements` 声明了 `org.jetbrains.compose.material3:material3-window-size-class:1.12.0-alpha03`）。要用 `calculateWindowSizeClass()` 就直接写，**不要**再 `implementation` 一遍。

> 但要注意：传递来的那个是 **`org.jetbrains.compose` 版本（CMP）**，与 `androidx.compose` 的包名相同但坐标不同。若出现类冲突，显式声明 `androidx.compose.material3:material3-window-size-class`（BOM 2026.09.00 管版本）覆盖传递版本，**这是唯一可能需要加依赖的地方**。

#### 7.3.3 开工顺序（单变量，每步一个 PR）

| # | 分支 | 内容 | 验收 | 风险 |
|---|---|---|---|---|
| **2.5a** | `refactor/chat-stateless` | **先拆 `ChatScreen`**：`ChatScreen(vm: DshViewModel)` 是唯一入口（`ChatScreen.kt:191`），100+ 处 `ui.` 读取 + `LaunchedEffect` 里发网络。抽出 `ChatPanel(state, callbacks…)` 无状态壳，`ChatScreen` 退化为取 `StateFlow` 的外壳 | `tests` 原数不减；`screenshots` **11 张逐字节不变**（纯搬运） | 中 |
| **2.5b** | `test/baseline-chat` | 给 `ChatPanel` 录基线：空会话 hero、有消息的会话、（可选）宽屏 | 新增 2~3 张；原有 11 张不变 | 低（纯测试） |
| **2.5c** | `feat/pager-navigation` | `HorizontalPager` 接管 `ChatView` 三个目的地；弹簧切页；底栏点按 → `animateScrollToPage` | 切页流畅；旋转保持；**底栏仍恒 3 项** | 中 |
| **2.5d** | `feat/nav-rail` | 宽屏（`WindowWidthSizeClass.Expanded`）把底栏换成 `NavigationRail` | 宽屏可用；窄屏不变 | 中 |

> **2.5a 不能跳过。** 没有它，2.5c 改的就是一个没有像素保护、且入口是 view model 的 3300 行文件 —— 和 2.4 之前 `TerminalPanel` 的处境一模一样，而那次的做法（先 refactor、再 baseline、分两个 PR）就是本仓的标准答案。

#### 7.3.4 2.5c 的接入点（已实测，不用再找）

- **当前 body 分支**：`ChatScreen.kt:1003` `if (showFiles)` / `:1038` `else if (view == ChatView.TERMINAL)` / `:1058` `else if (view == ChatView.TRAJECTORY)` —— pager 要把后两个分支换成 page 内容，`showFiles` 留在 pager **之外**（它是覆盖面板，不是页）。
- **底栏**：`ChatScreen.kt:1643-1653`，`selectedKey = view.name`，`onSelect` 现在直接 `view = it`；2.5c 要改成「同时 `pagerState.animateScrollToPage(index)`」，**并让滑动手势反向更新底栏选中态**（用 `pagerState.currentPage` 驱动，别维护两份状态）。
- **状态保存**：`view` 是 `rememberSaveable(ui.currentSessionId)`（`:489`）。换 pager 后要么保留它作为唯一真相、pager 只是视图，要么把 `pagerState` 也 save。**别两个都存**，那会产生两个可写状态。

#### 7.3.5 必须一起定的一件事：第 4 项要不要补

现在底栏 3 项（Chat/Trajectory/Shell）。`ROADMAP.md` 原来的「4 目的地」是笔误，2.4 已改正。要补第 4 项，**正确做法是把 `showFiles` 提升成 `ChatView.FILES`**（它已经有整块 body + 自带返回，`ChatScreen.kt:1003-1036`），而不是把 Settings 塞进来（那是 sheet，属 2.8）。

> 若做，建议并进 **2.5c**：pager 的页数、底栏项数、`ChatView` 枚举三者必须是同一个数字，分两个 PR 改必然有一边不同步。**若不做，就在 PR 描述里写一句「故意保持 3 项」**，别让它悬着。

#### 7.3.6 本机怎么验（别用 `build.sh`）

`:design` 的测试要 **JDK 21** 且要临时改 build-tools，`build.sh` 写死 17 跑不了。用已提交进仓库的脚本：

```sh
tools/localbuild.sh . :app:compileDebugKotlin
tools/localbuild.sh . :design:testDebugUnitTest
```

- **`:app` 的测试源码仍然编不了**（aapt2，§0）—— 2.5 新增的 `:app` 测试（包括所有基线）只能由 CI 验。
- 改完记得 `git status` 是干净的：脚本用 `trap` 还原 `buildToolsVersion`，漏还原会出现在 diff 里。
- 一轮 `:design:testDebugUnitTest` 实测 **1.5–3 分钟**，比 CI 一轮 7 分钟省一半。

#### 7.3.7 基线流程（2.4 刚走过，照做）

```sh
# 录基线：必须 --ref <分支>，否则跑的是 master 的 workflow 文件
gh workflow run screenshots.yml --ref <分支> -f branch=<分支>
gh run watch <run-id>
gh run download <run-id> -n roborazzi-baselines -D /tmp/bl
```

**录完先逐张比对 sha256**，确认「该变的是预期的那几张、不该变的一张没动」，再提交。2.4 就是这么验的：11 张里 2 张新增、1 张（`miuix-probe.png`）按预期变化、8 张逐字节相同。

#### 7.3.8 两条 2.4 留下的判据

1. **整个测试类因 `NoClassDefFoundError` / `ExceptionInInitializerError` 全挂 → 先查 JDK 版本**，不是测试写坏了。`build.yml` 曾因此在 JDK 17 上挂掉 25 个测试。
2. **六个测试按同一方向失败 → 几乎总是命名与语义反向**，不是六个独立 bug。`SystemBars` 曾把 `isAppearanceLightStatusBars` 理解反了。

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
