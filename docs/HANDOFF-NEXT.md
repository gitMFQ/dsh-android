# 交接文档（给新会话）

> 写于 2026-10-31（当日第三版）。第一版交接「接管 + 工具链全量升级」，第二版追加结构拆分的开头与截图基线；
> 本版追加 **1.2b（`:core:config`）**。
> **新会话请先读本文件，再按需查其它文档。**

## 1. 一句话现状

仓库 <https://github.com/gitMFQ/dsh-android>（私有，`gitMFQ/dsh-android`）已经是一个**能构建、1116 个功能测试全绿 + 4 张截图基线**的 DSH 原生安卓客户端 fork，工具链升到最新，miuix 已接入但**尚未使用**。

**换皮的安全网已经就位**（Roborazzi，PR #5），**结构拆分的第一个前置条件也已清掉**（`model/` 不再依赖根包，PR #4），**四个模块已经抽出来了**：`:core:model`（PR #6，413 个测试）、`:core:term`（PR #7，186 个测试，**纯 JVM —— 本仓第一次能在本机跑功能测试**）、`:core:config`（PR #8，0 个测试是预期）、`:core:protocol`（PR #9，73 个测试 —— **并抓出第一个生产缺陷**，见 research/13 §2.11）。阶段 1 的结构拆分基本完成，剩下 1.4（`:design`）。

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

每个 PR 都是**单一变量**，失败时能立刻定位。这个方法论请延续。

### 2.1 截图基线怎么用（新增，重要）

| 想知道 | 做法 |
|---|---|
| 换皮后有没有动到别的界面 | `verify.yml` 的 `Screenshot gate` 步骤会红；下载 `roborazzi-comparison` artifact 看 `*_compare.png` |
| 基线该更新了 | 手动跑 `Screenshots` workflow（`workflow_dispatch`），下载 `roborazzi-baselines`，覆盖 `app/src/test/screenshots/` 后提交 |
| 为什么不能在本机录 | aarch64 + 无可用 aapt2，`recordRoborazziDebug` 跑不了（§3 第一条） |

**基线只手动录**。若改成 push 自动录，基线会跟着代码走，比对就变成「自己和自己比」，门禁等于没有。

**两个数字别合并**：`tests=1116`（功能测试，每个 PR 的证据）与 `screenshots=4`（像素基线）。CI 分开打印，合并成 1120 会让「少跑一个测试」和「多一张基线」长得一模一样。

## 3. 关键约束（踩过的坑，别再踩）

| 约束 | 原因 |
|---|---|
| **本机不能完整构建** | aarch64 + 无可用 aapt2。`qemu-user` 能跑 x86_64 aapt2，但 AGP 的 `Custom AAPT2 location does not point to an AAPT2 executable` 校验过不去（shell 脚本和自编译 ELF 都被拒）；`binfmt_misc` 因容器无 `CAP_SYS_ADMIN` 无法注册。**所有验证走 CI。** |
| **网络需代理** | `dl.google.com` / `repo.maven.apache.org` 单文件可达但 **Gradle 并行解析器会卡死**。`settings.gradle.kts` 已把 Aliyun 镜像放最前。`git push` 偶尔 GnuTLS 失败，加 `ALL_PROXY=http://127.0.0.1:7890` 重试。 |
| **AGP 9 不允许 `org.jetbrains.kotlin.android`** | 内置 Kotlin，应用该插件直接 build fail。`kotlin { compilerOptions { } }` 仍可用。 |
| **不引 `miuix-blur`** | 模块 `minSdk = 33`，我们 26。玻璃效果自写（`Modifier.blur` API31+ + 降级）。 |
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
  docs/                    # 规划文档（见 §6）
  .github/workflows/       # build.yml（出 APK）/ verify.yml（测试+截图门禁）/ release.yml / screenshots.yml（手动录基线）
  build.sh                 # 唯一构建入口，找 .toolchain/gradle-9.5.0
```

## 5. CI

| 工作流 | 触发 | 作用 |
|---|---|---|
| `verify.yml` | push main/master + PR | `tests=1116 failures=0` + `screenshots=4` 像素比对 |
| `build.yml` | 同上 | 出 debug APK（约 20 MB） |
| `release.yml` | tag `v*` | 出 release APK 挂 Release |
| `screenshots.yml` | **手动** `workflow_dispatch` | 在 CI 录基线并上传 artifact（本机录不了） |

**提交 PR 即可验证**，`gh pr checks <n>` 看结果，约 7–10 分钟。

> `screenshots.yml` 是 `workflow_dispatch`，**只在默认分支上存在时才可手动触发**。新加的工作流要先把文件推到 master（或临时加 push 触发）才能跑第一次 —— PR #5 踩过，见 §8。

## 6. 文档地图（按需查，别全读）

| 想知道 | 读 |
|---|---|
| 下一步做什么 | [ROADMAP.md](ROADMAP.md) —— 阶段 1 起 |
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
| 1.4 | 建 `:design` 模块：`KsuTokens` + 主题 7 档外观 | 设置页可切外观并持久化 |

> **抽模块时必改两处**（1.1 实证，两处都会让 CI「绿着丢测试」）：
> 1. `verify.yml` 的汇总 glob：`app/build/test-results/**` → `**/build/test-results/**`；
> 2. **新模块的测试任务要在 `verify.yml` 和 `build.yml` 里显式写名字**（`bash build.sh :app:testDebugUnitTest :core:model:testDebugUnitTest`）。**依赖一个模块不会跑它的测试** —— 这一条比 glob 更容易漏，因为 CI 完全绿。
>
> 汇总现在还会打印分模块数字，单个 `tests=1116` 已经不足以证明两半都跑了。第 2 条每加一个模块都要再写一遍（1.2 起还要加 `:core:term:test`，1.2b 起还要加 `:core:config:testDebugUnitTest`）。

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


### 7.2 阶段 2：外壳换装（1.5 周，对应 DESIGN-TOKENS 的 D2–D5）

浮动底栏 + 4 目的地 pager / TopBar 折叠 / 抽屉换装 / 设置页换装 / Sheet 统一。

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
