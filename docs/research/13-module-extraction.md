# 模块拆分蓝图：`:core:protocol` 与 `:core:model`

> 对象：`/root/dsh-android`（单模块 `:app`，140 个 `.kt` / 约 5.8 万行，CI 基线 **1116 个测试全绿**）。
> 对应 [ROADMAP.md](../ROADMAP.md) 阶段 1 的 1.1 / 1.2，以及 [FORK-SURGERY.md](../FORK-SURGERY.md) §3 的 M1 / M2。
> 采集时间 2026-10-31。**本机不能构建**（见 [BUILD-AND-CI.md](../BUILD-AND-CI.md) §0），本文所有结论来自静态读码，验证一律走 CI。

## 0. 先说结论

| 结论 | 依据 |
|---|---|
| `model/` 现在**已经自包含**，可以第一个抽出 | §1.1：`model/` 对 `uk.xa0.dsh`（根包）的 import 已从 14 降到 **0** |
| `:core:model` **不能是纯 JVM 模块**，必须是 `com.android.library` | §2.1：`model/TurnProcess.kt:3` import `android.util.Log`；`ChatModels.kt:4`、`TurnProcess.kt:4` import `androidx.compose.runtime.Immutable` |
| `net/` 抽不干净：它依赖 `data/` 与 `term/`，直接抽会形成环 | §1.2：`DshClient.kt:25` → `data.DshConfig`；`RouterReadoutTransport.kt:32-34`、`TerminalClient.kt:5-9` → `term.*` |
| 所以**顺序必须是 `:core:model` → `:core:term` → `:core:protocol`**，而不是 ROADMAP 里写的 1.1 先抽 `net/` | §3 |
| 真正的成本不在文件搬运，在 **`internal` 可见性**（§2.2）和 **3 个 Robolectric 测试**（§2.4） | 共 4 处 `internal` 需要提权，3 个测试必须留在 `:app` |
| 建议把 ROADMAP 的 1.1 / 1.2 顺序对调 | §3.0 |

## 1. 依赖实测

### 1.1 包间 import 矩阵（2026-10-31，`split-model-rows` 分支之后）

命令：对 `app/src/main/java/uk/xa0/dsh/**` 逐文件统计 `^import uk\.xa0\.dsh\.`，按首段包名归组。

| from \ to | root(`uk.xa0.dsh`) | model | net | data | term | ui | widget | diag |
|---|---|---|---|---|---|---|---|---|
| **model** | **0** ✅ | — | 0 | 0 | 0 | 0 | 0 | 0 |
| **net** | 0 | 26 | — | 1 | 8 | 0 | 0 | 0 |
| **data** | 0 | 1 | 2 | — | 3 | 0 | 0 | 0 |
| **term** | 0 | 1 | 0 | 0 | — | 0 | 0 | 0 |
| **root** | — | 131 | 25 | 6 | 33 | 7 | 3 | 2 |
| **ui** | 43 | 151 | 12 | 5 | 22 | — | 0 | 29 |
| **widget** | 5 | 0 | 0 | 0 | 0 | 0 | — | 0 |

**读法**：`model` 行全 0，意味着 `model/` 只依赖 JDK + `org.json` + `compose-runtime` + `android.util.Log`，是唯一一个可以**先独立、后接回**的包。

> 上一版（PR #4 之前）`model → root` 是 **14**（`SessionItem` 7、`WorkspaceItem` 3、`JobItem` 2、`QueuedMessage` 1、`QueueAttachment` 1）。那 5 个类是**宿主行数据**，却内联声明在 `DshViewModel.kt` 里（229/287/474/592/613 行）。PR #4 把它们移到 `model/SessionRows.kt`、`model/JobRows.kt`、`model/QueueRows.kt`，并改写了 47 处 import + `ui/ChatScreen.kt` 的 5 处全限定引用。**这一步是 `:core:model` 的前置条件**：模块不能依赖 app 模块。

### 1.2 `net/` 的三条外部边（拆分的主要障碍）

| 边 | 证据 | 性质 |
|---|---|---|
| `net → data` | `net/DshClient.kt:25` `import uk.xa0.dsh.data.DshConfig`；使用点 `DshClient.kt:356,384,515` | **环**：`data/ConfigStore.kt:8-9` 反过来 import `net.AcceptedCert` / `net.CertTrust` |
| `net → term` | `net/TerminalClient.kt:5-9`（`TerminalEnvironmentInfo`/`TerminalFrame`/`TerminalInfo`/`TerminalShellInfo`/`TerminalState`）；`net/RouterReadoutTransport.kt:32-34`（`TerminalInfo` + `readoutTerminalToAdopt` + `readoutTerminalsToRetire`） | 单向，但 `term/` 自己也依赖 `model`（`term/TerminalProtocol.kt:3`） |
| `net → model` | 26 处，散布在 `DshClient.kt` / `EndpointResolver.kt` / `RemoteMux.kt` | 单向，方向正确 |

`term/` 的全部外部依赖只有一行：`term/TerminalProtocol.kt:3` `import uk.xa0.dsh.model.isRouterReadoutTerminal`（定义在 `model/RouterReadout.kt:82`）。也就是说 **`term/` 只依赖 `model/`**，是第二个可以抽出的包。

`data/` 同时 import `net`、`term`、`model`（`data/ConfigStore.kt:7-12`），是**最上层**，应留在 `:app`。

### 1.3 文件清单

**`:core:model`（50 文件 / 11 087 行，含 PR #4 新增的 3 个）**

```
model/  ApprovalAnswer ApprovalCopy ApprovalRoster Automations Balance ChatModels
        ConnectionRestart FamilyUsage FileDownload ForceQuit HostSettings HostVersion
        JobRows Jobs JobsMarker LandingSession LiveRunning LlamaRouter MissingMembers
        ModelSelection QuestionWaits QueueMirror QueueRows RouterReadout
        ScheduleManagement SendFailureCopy SessionFiles SessionFork SessionOrder
        SessionRoster SessionRows SessionSearch SessionStats SessionTargets
        SessionTitles StartupCopy SubagentCatalog SubagentContinuation SubagentTree
        TokenCost ToolCallTree Trajectory Transcript TurnProcess WorkspaceDrag
        WorkspaceExplorer WorkspaceFileLoader WorkspaceGroups WorkspaceOrder WorkspaceTree
```

**`:core:protocol`（14 文件 / 4 031 行）**

```
net/  CertPins CertProbe CertTrust ConnectivityGate DeepSeekAccount DshClient
      EndpointResolver JobClient Multipart PersistentCookieJar RemoteMux
      RouterReadoutTransport TerminalClient TransportReset
```

**`:core:term`（7 文件 / 2 485 行，ROADMAP 未列，但 §3 证明它是必需的一站）**

```
term/  HostShell TerminalCellSize TerminalEmulator TerminalInput TerminalKeys
       TerminalModel TerminalProtocol
```

## 2. 四个真正的坑

### 2.1 两个模块都得是 `com.android.library`

`model/` 里有两处 Android / Compose 依赖，纯 JVM 模块编不过：

| 文件:行 | 依赖 |
|---|---|
| `model/TurnProcess.kt:3` | `import android.util.Log` |
| `model/ChatModels.kt:4` | `import androidx.compose.runtime.Immutable` |
| `model/TurnProcess.kt:4` | `import androidx.compose.runtime.Immutable` |

`@Immutable` 是 Compose 编译器的**稳定性注解**，不是 UI 依赖 —— 只要把 `compose-runtime` 作为 `api` 依赖即可，不需要 `compose-ui`。`android.util.Log` 则直接决定必须是 Android library（或者把 `TurnProcess` 的日志改成可注入的 logger，那是行为改动，**不建议**在拆分 PR 里做）。

```kotlin
// core/model/build.gradle.kts（示意）
plugins { id("com.android.library") }   // AGP 9：不要加 org.jetbrains.kotlin.android
android {
    namespace = "uk.xa0.dsh.core.model"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(platform("androidx.compose:compose-bom:2026.09.00"))
    api("androidx.compose.runtime:runtime")      // @Immutable
    // org.json 由 Android 平台提供；单测用真实实现，见 §2.4
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
```

> **`namespace` 不要写成 `uk.xa0.dsh.model`**。Kotlin 的 `package` 和 Android 的 `namespace` 是两件事，但 `namespace` 会决定 `R` 类与 `BuildConfig` 的位置。这里给一个独立名字，避免和 `:app` 的 `uk.xa0.dsh` 混淆。

### 2.2 `internal` 可见性：4 处必须提权

Kotlin 的 `internal` 是**模块级**可见性。文件一搬进新模块，原来「同模块内可见」的调用点就全部编不过。实测清单（`net/` 5 个、`model/` 7 个 `internal` 声明，逐个查外部调用点）：

| 声明 | 定义 | 外部调用点 | 处理 |
|---|---|---|---|
| `hostOf(raw: String)` | `net/EndpointResolver.kt:22` | `ui/components/CertificateSection.kt:55`（**跨包**） | 改 `public` |
| `PERMISSION_PRESET_LABELS` | `model/HostSettings.kt:324` | `ui/SettingsContent.kt:48,1118`（**跨包**） | 改 `public` |
| `displayPresetName(name)` | `model/HostSettings.kt:372` | `ui/SettingsContent.kt:51,1119`（**跨包**） | 改 `public` |
| `CertProbe.systemTrusts(...)` | `net/CertProbe.kt:94` | 无（`internal fun` 在 object 内，仅本模块用） | 不动 |
| `CertTrust.systemTrust` | `net/CertTrust.kt:271` | 无 | 不动 |
| `isPrivateLanHost` / `subnetRewritten` | `net/EndpointResolver.kt:41,63` | 无 | 不动 |
| `choiceLabel` | `model/HostSettings.kt:358` | 无 | 不动 |
| `shellQuote` / `percentEncodeQuery` | `model/RouterReadout.kt:430,440` | `app/src/test/.../RouterReadoutTest.kt:143` 仅注释提及 | 不动 |
| `fileNameOf` | `model/SessionFiles.kt:561` | 无 | 不动 |

**只有 3 个 `internal` 需要变成 `public`**（上表前三行）。这三处恰好都是「UI 层要读的纯函数/常量」，提权是合理的，而且**不需要改动任何调用点**（`public` 是 `internal` 的超集）。

> 注意：如果为了省事把整个 `model` 的 `internal` 全改成 `public`，会丢掉这 7 个声明的封装，后续没人知道哪些是内部实现。**只改需要的 3 个。**

### 2.3 包名与模块边界的取舍

有两种做法：

| 方案 | 做法 | 代价 |
|---|---|---|
| **A. 保留原包名**（推荐） | 新模块沿用 `uk.xa0.dsh.model` / `uk.xa0.dsh.net`，只改 `namespace` | 零 import 改动；`namespace` 与 `package` 不一致是可接受的（`namespace` 只影响 `R`/`BuildConfig`，这两个模块都用不到） |
| B. 换包名 | 改成 `uk.xa0.dsh.core.model` | 47 个文件 `package` 行 + 全仓数百处 import 全改；测试、`internal`、全限定引用全部要跟 |

**选 A**。理由是这条路径上**唯一需要动的是 `build.gradle.kts` 和目录位置**，源码零改动 —— 符合本仓「单变量、CI 可验证」的方法论。

### 2.4 测试迁移：47 + 8 个测试文件，3 个必须留下

`app/src/test/java/uk/xa0/dsh/model/`（47 文件）与 `.../net/`（8 文件）里，有 3 个跑 Robolectric，**不能**跟着源码进 Android library 的 `test` 源集（那会要求 library 也配 Robolectric + Compose 测试依赖，把简单模块拖重）：

| 测试 | 为什么留下 | 依据 |
|---|---|---|
| `model/SessionReplayProbeTest.kt` | `@RunWith(RobolectricTestRunner::class)`，用 Compose 测试装置 | `@Config(sdk = [34], application = HarnessApplication::class)` |
| `model/ShellRowExpansionTest.kt` | 同上 | 同上 |
| `net/DshClientResetTest.kt` | 同上 | 同上 |

其余 **52 个**是纯 JVM 测试（`model/` 45 个 + `net/` 7 个，JUnit + `org.json`），随源码一起搬到新模块的 `src/test/`。

**关键细节**：`HarnessApplication` 声明在 `app/src/test/java/uk/xa0/dsh/scroll/ScrollHarnessTest.kt:41`（测试源集里的一个 `.kt` 文件，不是独立类文件）。这 3 个测试留在 `:app` 就自然还能用；一旦有人把它们搬走，就必须先把 `HarnessApplication` 提到一个共享测试模块（`buildSrc` 或 `:testing`），**否则编译失败**。

**`org.json` 的坑**（已有前例，见 `app/build.gradle.kts` 的注释）：单测 classpath 上的 `android.jar` 是**桩实现**，`JSONObject(...)` 会抛 `Stub!`。新模块的 `testImplementation` 必须显式加 `org.json:json:20240303`，否则所有解析测试会因为与解析无关的原因红掉。

## 3. 建议的 PR 序列（单变量，每步一个 CI 门）

### 3.0 先修正 ROADMAP 的顺序

ROADMAP 阶段 1 写的是 1.1 抽 `net/`、1.2 抽 `model/`。**这个顺序执行不了**：`net/` 依赖 `data.DshConfig`（§1.2），而 `data/` 依赖 `net` + `term` + `model`，会形成 `net ⇄ data` 的环。正确顺序是**由内向外**：

```
model  →  term  →  protocol(net)  →  (data/ui 留在 :app)
```

### 3.1 序列

| # | PR | 内容 | 验收（CI） |
|---|---|---|---|
| **M0** ✅ | #4 `split-model-rows` | 5 个宿主行类移入 `model/`，`model → root` 边清零 | `tests=1116 failures=0` |
| **M1** | `core-model-module` | 新建 `:core:model`（`com.android.library`，**保留包名**，§2.3 方案 A），把 `model/` 50 文件与 45 个纯 JVM 测试搬进去；`:app` 加 `implementation(project(":core:model"))`；3 个 Robolectric 测试留在 `:app`；3 个 `internal` 提权（§2.2） | `:core:model:test` 独立绿 + 全仓 `tests=1116 failures=0` |
| **M2** | `core-term-module` | 新建 `:core:term`，搬 `term/` 7 文件；它只依赖 `:core:model`，无环 | `tests=1116 failures=0` |
| **M3** | `core-protocol-module` | 新建 `:core:protocol`，搬 `net/` 14 文件；依赖 `:core:model` + `:core:term` + okhttp；**此时 `DshConfig` 的引用仍在 `:app` 侧**（见下） | `tests=1116 failures=0` |
| **M4** | `break-net-data-cycle` | 把 `DshConfig` 从 `data/ConfigStore.kt:21` 移到 `:core:protocol`（或新建 `:core:config`），消除 `net → data` 的环 | `tests=1116 failures=0` |

**M3 的临时妥协**：`net/DshClient.kt:25` 需要 `DshConfig`。M3 时 `DshConfig` 还在 `:app`，而 `:app` 依赖 `:core:protocol` —— 会成环。三个选项：

1. **（推荐）** 把 M4 提前，在 M3 之前先把 `DshConfig` 移进 `:core:model`（它只依赖 `model.DrawerGrouping` + `net.AcceptedCert` + `term.TerminalCellSize`，拆分后 `AcceptedCert` 在 `:core:protocol`……仍然成环）。
2. 先把 `AcceptedCert`（`net/CertTrust.kt:24`）单独提到 `:core:model`，`DshConfig` 随之进 `:core:model`，M3 再搬 `net/`。**这个顺序是干净的**。
3. 在 `:core:protocol` 里定义一个最小接口 `DshConfigSource`，`DshClient` 只依赖接口，实现留在 `:app`。**不推荐**：这是为拆分而拆分的行为改动。

> **建议把序列改成 M1 → M2 → **M2.5**（`AcceptedCert` + `DshConfig` 进 `:core:model`）→ M3 → M4（`data/` 改为依赖新模块，环自然消失）**。这样每一步都没有临时妥协。

### 3.2 每一步的测试数怎么算

基线是 **1116**。搬测试时总数**必须保持 1116**（不是「大概一样」）：

- 搬走的测试仍会被 `:app:testDebugUnitTest` 跑到吗？**不会** —— 它们变成 `:core:model:testDebugUnitTest`。所以 CI 的汇总步骤（`verify.yml` 里的 python 片段）要扫 `**/test-results/**` 而不是只扫 `app/build/`。
- 当前 `verify.yml` 硬编码 `app/build/test-results/**`：

  ```yaml
  for f in glob.glob('app/build/test-results/**/*.xml', recursive=True):
  ```

  **M1 必须同步改成 `**/test-results/**/*.xml`**，否则测试数会从 1116 掉到 ~1070，而 CI 仍然是绿的 —— 这正是「静默跳过」最容易被放过的地方。

## 4. 风险与对策

| 风险 | 触发条件 | 对策 |
|---|---|---|
| **测试被静默跳过** | 搬走测试后 CI 只扫 `app/build/`，数字变小但没人看 | §3.2：把 glob 改成 `**/test-results/**`，并在 PR 里贴出前后数字 |
| `internal` 提权过度 | 图省事把 7 个全改 `public` | §2.2：只改 3 个；PR 里逐条说明 |
| `namespace` 与 `package` 混淆 | 把 `namespace` 写成 `uk.xa0.dsh.model` | §2.1：用独立 namespace；两个模块都不生成 `R`/`BuildConfig` |
| `@Immutable` 编不过 | 只加 `compose-runtime` 到 `implementation` 而非 `api` | §2.1：`api("androidx.compose.runtime:runtime")`，否则下游 `:app` 看不到该注解的元数据 |
| `org.json` 桩实现 | 新模块漏加 `testImplementation("org.json:json:20240303")` | §2.4：每个新模块的 `build.gradle.kts` 都要显式加 |
| `HarnessApplication` 不可见 | 有人把 3 个 Robolectric 测试也搬走 | §2.4：它们留在 `:app`；要搬先提共享测试模块 |
| **AGP 9 插件冲突** | 新模块里习惯性写 `id("org.jetbrains.kotlin.android")` | 根 `build.gradle.kts` 已注释说明：AGP 9 内置 Kotlin，加了直接 build fail；library 模块同样只写 `com.android.library` |
| **Gradle 9 配置缓存 / 并行** | 新模块引入新的 configuration | 本机可先跑 `gradle help`（配置阶段可用，约 6–7 分钟）确认无 DSL 错误，再花 CI 一轮 |
| 目录搬迁丢文件 | `git mv` 与 `package` 行不一致 | 用 §2.3 方案 A（包名不变），`git mv` 后**源码零改动**，`git status` 应显示 rename 而非 delete+add |

## 5. 与 ROADMAP 的差异汇总

| ROADMAP 原文 | 本文建议 | 原因 |
|---|---|---|
| 1.1 先抽 `:core:protocol`（`net/`） | 先抽 `:core:model` | `net → data` 成环，`model` 已自包含（§1.1、§1.2） |
| 1.2 再抽 `:core:model` | 第二 | 同上 |
| 未提 `:core:term` | 增加 M2 | `net/` 依赖 `term/` 的 5 个类型 + 2 个函数（§1.2） |
| 「1 周」 | 5 个 PR × 1 轮 CI，**但每步都要重跑全量 1116 测试** | 纯搬运，风险低；真正的变量是 `internal` 提权（3 处） |
| 验收「独立编译 + 单测绿」 | 追加「CI 汇总的测试数仍为 1116」 | 防止静默跳过（§3.2） |
