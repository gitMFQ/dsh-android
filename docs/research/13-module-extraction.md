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
| **`:core:term` 可以是纯 JVM 模块**（源码无 Android），但**前提是拆掉它到 `:core:model` 的那一条边** | §2.6：`:core:model` 是 Android 库，纯 JVM 消费者变体匹配失败；M2 把 readout 的 id+谓词移入 term 后成立 |
| **`:core:model` 接不住 `DshConfig` + `AcceptedCert`**，M2.5 应改为**独立 `:core:config`** | §2.7 / §2.8：`AcceptedCert → CertPins → okhttp`；`DshConfig → TerminalCellSize`（会逼 model 反向依赖纯 JVM 的 term） |
| **判据统一为「依赖闭包」**：源码里有没有 `import android` 既不充分也不必要 | §2.6 / §2.8：两次都是源码纯、闭包不纯 |

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

> **M2 已改变这一点（2026-10-31）**：那个 import 正是把 `:core:term` 钉成 Android 库的原因，已连同 `ROUTER_READOUT_TERMINAL_ID` 一起移入 `:core:term`，`term → model` 这条边现在是 **0**。见 §2.6。

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

## 2. 八个真正的坑

> **M1 已落地（PR #6，2026-10-31）**，实测数字：`tests=1116 failures=0`、
> `screenshots=4`、`:app` 703 + `:core:model` 413。下文 §2.1–§2.4 的判断全部成立，
> 但有 3 处与实测不符，已在正文就地更正；§2.5 是 M1 发现的第五个坑。
>
> **M2 已落地（PR #7，2026-10-31）**，实测 `app: 517` / `model: 413` / `term: 186` = `tests=1116`。
> §2.6 是 M2 发现的第六个坑（纯 JVM 不能消费 Android 库）；§2.7 是对 M2.5 原计划的证伪，
> §2.8 是替代方案，§2.9 是落地结果。**§2.7/§2.8 在 M2.5 开工前必须先读。**

### 2.1 `:core:model` 必须是 `com.android.library`（`:core:term` 后来证明可以纯 JVM，见 §2.6）

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

**只有 2 个 `internal` 需要变成 `public`**（上表第 2、3 行）。这两处恰好都是「UI 层要读的纯函数/常量」，提权是合理的，而且**不需要改动任何调用点**（`public` 是 `internal` 的超集）。

> **实测更正（M1）**：`hostOf` 属于 `net/`，是 **M3（1.3）** 的事，不在 M1 范围内。M1 只提权了 `PERMISSION_PRESET_LABELS` 与 `displayPresetName` 两个，其余 4 个 `model/` 的 `internal`（`choiceLabel` / `shellQuote` / `percentEncodeQuery` / `fileNameOf`）实测**确实没有跨包调用点**，原样保留。

> 注意：如果为了省事把整个 `model` 的 `internal` 全改成 `public`，会丢掉这些声明的封装，后续没人知道哪些是内部实现。**只改需要的。**

### 2.3 包名与模块边界的取舍

有两种做法：

| 方案 | 做法 | 代价 |
|---|---|---|
| **A. 保留原包名**（推荐） | 新模块沿用 `uk.xa0.dsh.model` / `uk.xa0.dsh.net`，只改 `namespace` | 零 import 改动；`namespace` 与 `package` 不一致是可接受的（`namespace` 只影响 `R`/`BuildConfig`，这两个模块都用不到） |
| B. 换包名 | 改成 `uk.xa0.dsh.core.model` | 47 个文件 `package` 行 + 全仓数百处 import 全改；测试、`internal`、全限定引用全部要跟 |

**选 A**。理由是这条路径上**唯一需要动的是 `build.gradle.kts` 和目录位置**，源码零改动 —— 符合本仓「单变量、CI 可验证」的方法论。

#### 2.3.1 那什么时候才改包名？（2026-10-31 决策，M1 之后实测）

**结论：现在不改，而且很可能永远不需要改。** 如果将来要改，**等到 1.2–1.4 全部抽完之后，单独一个 PR 一次改干净**。

改名的真实成本（M1 之后实测）：

| 项目 | 数量 |
|---|---|
| `package` 声明行（`core/model` 内） | 50 |
| 全仓 `import uk.xa0.dsh.model.` | 481 |
| 代码体内全限定引用 | 28 |
| 受影响文件 | **165** |

对比 M1 本身：98 个文件、143 行插入、19 行删除，**93 个 rename 全是 100% 相似度**。改名会把这层「纯搬运」的性质彻底破坏 —— `git mv` 之后每个文件都变成大改动，review 里看不出任何东西，回滚也没有清晰边界。

**三个具体风险**：

1. **会撞上 split package。** `app/src/test/java/uk/xa0/dsh/model/` 里留下的 4 个测试，`package` 也是 `uk.xa0.dsh.model`。`:core:model` 一旦改成 `uk.xa0.dsh.core.model`，就是「同一个包名横跨两个模块」。现在无害（那 4 个测试不用任何 `internal`，已核），但会变成定时炸弹：将来有人把测试移回去、或往 `:core:model` 加一个 `internal`，编译会莫名其妙地失败。
2. **收益是零。** 运行时完全不依赖包名：没有 `Class.forName`、没有 `kotlinx.serialization`、没有 Parcelize、没有 Gson/Moshi，`proguard-rules.pro` 是空的（`isMinifyEnabled = false`）。改与不改，APK 行为完全一样。
3. **真正的「身份」是 `applicationId`，不是模块包名。** `uk.xa0.dsh` / `uk.xa0.dsh.debug` 决定安装、升级、签名连续性，**这个绝对不能动**，与模块内部包名无关。

**唯一的改名理由**是「模块的 Kotlin 包名和别人撞了，导致无法 import」。本项目 `uk.xa0.dsh.model` 只有一个来源，不会撞。

> **例外**：如果只是觉得 `namespace`（`uk.xa0.dsh.core.model`）与 `package`（`uk.xa0.dsh.model`）看着不一致，改 `namespace` 是**零成本**的（一行、不生成任何类）。但现状其实更有用 —— 它明确表达「本模块不产出 R/BuildConfig」，改了反而丢掉这层信息。

### 2.4 测试迁移：47 + 8 个测试文件，4 个必须留下

`app/src/test/java/uk/xa0/dsh/model/`（47 文件）与 `.../net/`（8 文件）里，有 3 个跑 Robolectric，**不能**跟着源码进 Android library 的 `test` 源集（那会要求 library 也配 Robolectric + Compose 测试依赖，把简单模块拖重）：

| 测试 | 为什么留下 | 依据 |
|---|---|---|
| `model/SessionReplayProbeTest.kt` | `@RunWith(RobolectricTestRunner::class)`，用 Compose 测试装置 | `@Config(sdk = [34], application = HarnessApplication::class)` |
| `model/ShellRowExpansionTest.kt` | 同上 | 同上 |
| `net/DshClientResetTest.kt` | 同上 | 同上 |

> **实测更正（M1）**：`model/` 里要留下的是 **4 个**，不是 3 个。除了上面两个 Robolectric，`DurableRunningTest` 与 `SessionRosterTest` 都 `import uk.xa0.dsh.ui.indexSubagentRollups`，而那个函数定义在 `ui/SubagentRollup.kt`（留在 `:app`）。这两个不是 Robolectric，纯粹是**依赖了 `:app` 的代码**，搬走就编不过。判据应该是「是否依赖 `:app` 的任何包」，而不只是「是否跑 Robolectric」。

其余 **43 个**是纯 JVM 测试（`model/` 43 个 / **413 个 `@Test`**），随源码一起搬到新模块的 `src/test/`。`net/` 的 7 个属于 M3。

**关键细节**：`HarnessApplication` 声明在 `app/src/test/java/uk/xa0/dsh/scroll/ScrollHarnessTest.kt:41`（测试源集里的一个 `.kt` 文件，不是独立类文件）。这几个测试留在 `:app` 就自然还能用；一旦有人把它们搬走，就必须先把 `HarnessApplication` 提到一个共享测试模块（`buildSrc` 或 `:testing`），**否则编译失败**。

**`org.json` 的坑**（已有前例，见 `app/build.gradle.kts` 的注释）：单测 classpath 上的 `android.jar` 是**桩实现**，`JSONObject(...)` 会抛 `Stub!`。新模块的 `testImplementation` 必须显式加 `org.json:json:20240303`，否则所有解析测试会因为与解析无关的原因红掉。

### 2.5 跨模块 smart cast：M1 实测出的第五个坑（**新**）

Kotlin 的 smart cast 依赖**能看见属性的幕后字段**。属性一旦声明在另一个模块里，`val` 不再足够 —— 编译器无法证明别的模块的属性永不变化，于是拒绝从 `is` / `!= null` 判断里收窄类型：

```
Smart cast to 'ChatEntry.AssistantMessage' is impossible, because 'entry' is a
public API property declared in different module
```

M1 里正好 **4 处**踩到，全是 `:app` 的 UI 代码：

| 文件:行 | 属性 | 修法 |
|---|---|---|
| `ui/ChatScreen.kt:1346` | `row.entry`（`DisplayRow.Single.entry`） | 先 `val closing = (row as? Single)?.entry as? AssistantMessage` |
| `ui/RouterTab.kt:118` | `target.model?.name` | 先 `val declaredName = target.model?.name` |
| `ui/components/ChatRows.kt:161` | `entry.summary` | 先 `val pluginSummary = entry.summary` |
| `ui/components/FilesPanel.kt:626` | `entry.size` | 先 `val entrySize = entry.size` |

**特征与对策**：

- 这是**编译错误**，不是静默行为变化 —— 属于良性失败，不会漏进产物。
- 修法机械：把属性读进局部变量，条件与取值都不变。**不要**为此把 `val` 改成 `var`，也不要加 `!!`。
- 后续抽 `:core:term` / `:core:protocol` 时**必然还会有**，因为 UI 大量消费 `term.*` 与 `net.*` 的类型。提前有预期，别当成设计问题。
- 静态预判不可靠（可空属性名太泛），**直接编译一遍最快**。本机现在可以 `:app:compileDebugKotlin`（见 §6）。

### 2.6 纯 JVM 模块不能消费 Android library：M2 实测出的第六个坑（**新**）

§3.1.1 原判断「`term/` 源码零 `android`/`androidx` import，所以可以用 `kotlin("jvm")`」**只对了一半**：源码是纯的，但**依赖不是**。`term/` 唯一的外部依赖 `model.isRouterReadoutTerminal` 住在 `:core:model`，而后者是 `com.android.library`。

在 AGP 9.3.1 + Gradle 9.5.0 上实测，纯 JVM 消费者引入 `implementation(project(":core:model"))` 会死在变体匹配：

```
Could not resolve all dependencies for configuration ':probe:compileClasspath'.
> ... Variant 'debugApiElements' ... Unmatched attributes:
      - Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')
      - Provides attribute 'com.android.build.api.attributes.AgpVersionAttr' with value '9.3.1'
        but the consumer didn't ask for it
```

三个层次的修法都试过，**全部无效**：

| 尝试 | 结果 |
|---|---|
| 直接依赖 | 变体歧义（debug/release 都匹配不上） |
| 强制 `KotlinPlatformType.androidJvm`（正确类型的 attribute） | 仍歧义 |
| 逐配置补 `AgpVersionAttr` + `BuildTypeAttr` + `VariantAttr` + `androidJvm` | 仍是歧义，且出现 `artifactType 'r-class-jar' but the consumer didn't ask for it` |

**另一条独立的地雷**（同为实测）：AGP 9 把 Kotlin 放上了 build classpath，所以模块里写

```kotlin
plugins { kotlin("jvm") version "2.4.21" }
```

会直接失败：`Error resolving plugin ... The plugin is already on the classpath with an unknown version`。
**版本要声明在根 `build.gradle.kts`（`apply false`），模块只用无版本的 `kotlin("jvm")`。**

> 顺带确认一件**成立**的事：`kotlin("jvm")` 模块可以正常用 `androidx.compose.runtime`（`@Immutable` 编译通过），
> 因为那是普通 JVM artifact。所以阻碍不是 Compose，而是 `:core:model` 的 **Android 库形态**。

**M2 的选择**：把 `ROUTER_READOUT_TERMINAL_ID` 与 `isRouterReadoutTerminal` 从 `:core:model` 移到 `:core:term`。

理由：识别「这是不是 readout 的终端」本来就是**终端规则**，而不是模型解析规则。移过去之后：

- `:core:term` 变成真正的纯 JVM 模块，**186 个测试可以在本机直接跑**（`bash build.sh :core:term:test`，实测 `tests=186 failures=0`，不需要 SDK/aapt2）。这是本仓第一次能在本地验证功能测试。
- `:core:term` 不再依赖任何模块，`term → model` 这条边被**消除**（不是保留）。
- 代价：`:app` 里 3 处 import 改指向 `uk.xa0.dsh.term.*`（`DshViewModel`、`RouterReadoutTransport`、`RouterReadoutTest`），全是纯改名。

> 判据记下来：**「源码是纯 JVM」不等于「能做成纯 JVM 模块」**。真正的判据是
> 「它的依赖闭包里有没有 Android 模块」。抽 `:core:protocol` 时还会再遇到。

### 2.7 `DshConfig` 装不进 `:core:model`：M2.5 的证伪（**新**）

原文认为 `DshConfig` 依赖面很小（`model.DrawerGrouping` + `net.AcceptedCert` + `term.TerminalCellSize`），所以把它和 `AcceptedCert` 一起搬进 `:core:model` 就解环了。实际逐字段查下来，**它有三条独立的外部边，而且其中两条会把 `:core:model` 拽向它不该去的方向**。

**`DshConfig` 的真实依赖（`data/ConfigStore.kt:21-139`）**

| 字段 | 类型 | 来自 | 含义 |
|---|---|---|---|
| `:58` | `DrawerGrouping` | `model` ✅ | 已经是 `:core:model` 的 |
| `:38` | `ThemeMode` | **`data` 自己**（`ConfigStore.kt:141`） | 与 `DshConfig` 同文件，一起搬即可 |
| `:51` | `BusyEnter` | **`data` 自己**（`ConfigStore.kt:149`） | 同上 |
| `:123` | `TerminalCellSize` | **`term`** ⚠️ | 现在是 `:core:term` |
| `:133` | `List<AcceptedCert>` | **`net`** ⚠️ | 下一步才是 `:core:protocol` |

**`AcceptedCert` 的真实依赖（`net/CertTrust.kt:24-35`）**

它自己不难，难在它的属性：

```kotlin
data class AcceptedCert(
    val host: String,
    val acceptedAt: Long,
    val certificate: X509Certificate,
) {
    val pin: String get() = CertPins.pinOf(certificate)   // CertTrust.kt:30
    ...
}
```

`pin` 调用 `CertPins.pinOf`（`net/CertPins.kt:53`）。所以 `AcceptedCert` 搬进 `:core:model` 就**必须**把 `CertPins` 一起搬。而 `CertPins` 里有一个函数碰 okhttp：

| `CertPins` 成员 | 依赖 | 能否留在 `:core:model` |
|---|---|---|
| `pinOf(...)` ×3、`sha256`、`display`、`isWellFormed`、`decodeOrNull` | 纯 JDK（`java.security`/`Base64`） | ✅ 可以 |
| `pinner(...)` — `CertPins.kt:106` | **`okhttp3.CertificatePinner`** | ⚠️ 要 okhttp |
| `isPinFailure(...)` — `CertPins.kt:123` | 纯 JDK（只读 `Throwable.message`/`cause`） | ✅ 可以 |

也就是说 **`CertPins` 里 okhttp 的接触面只有一个函数 `pinner()`（106–113 行）**，`isPinFailure` 虽然注释里讲 OkHttp，实现上只遍历 cause 链，不 import。

**三条阻塞链**

1. **`DshConfig → AcceptedCert → CertPins → okhttp`**：要么 `:core:model` 加 okhttp 依赖，要么把 `pinner()` 劈出去。
2. **`DshConfig → TerminalCellSize → :core:term`**：`:core:model` 若接 `DshConfig`，就要反过来依赖 `:core:term` —— **而 `:core:term` 是为了保持纯 JVM 才刚把依赖清零的**（§2.6）。方向完全反了，且会让 M2 的收益全部作废。
3. **`DshConfig → data`（`ThemeMode` / `BusyEnter`）**：这两条同文件、可直接搬，不构成阻塞，但说明 `DshConfig` 是「配置横切概念」，本来就不属于任何叶子模块。

第三条之外，前两条都指向同一个结论：**`DshConfig` 是横切点，`AcceptedCert`/`CertPins` 是证书概念，`TerminalCellSize` 是终端概念。把它们塞进 `:core:model` 会让 model 从「最内层」变成「什么都依赖」。**

### 2.8 正解：独立 `:core:config`（M2.5 改写）

依赖方向应当是：

```
        :core:model        (最内层，无出边)
            ↑
        :core:term         (纯 JVM，无出边 —— 必须保住，见 §2.6)
            ↑
        :core:config       ← DshConfig / ThemeMode / BusyEnter / AcceptedCert / CertPins(JDK 部分)
            ↑
        :core:protocol     ← net/，okhttp / CertTrust / CertPins.pinner
            ↑
           :app
```

`:core:config` 依赖 `:core:model` + `:core:term`，**不**依赖 `:core:protocol`。

> **必须是 `com.android.library`，不能纯 JVM。** `DshConfig` 与 `AcceptedCert` 两个类本体都没有
> `import android`，很容易误判成纯 JVM —— 但它的闭包里有 `:core:model`（Android 库），
> 纯 JVM 消费者会立刻撞上 §2.6 的变体匹配失败。**这是 §2.6 判据的第二次应用**：
> 看闭包，不看源码。`:core:term` 之所以能纯 JVM，是因为它把唯一那条边拆掉了；
> `:core:config` 拆不掉（`DshConfig` 真的需要 `DrawerGrouping`，而它在 model 里）。

它要装：

| 内容 | 出处 | 备注 |
|---|---|---|
| `DshConfig` | `data/ConfigStore.kt:21-139` | 连同 `isConfigured` / `usesManualCookie` 两个派生属性 |
| `ThemeMode` / `BusyEnter` | `data/ConfigStore.kt:141,149` | 与 `DshConfig` 同文件，天然一起走 |
| `AcceptedCert` | `net/CertTrust.kt:24-35` | 只依赖 `X509Certificate`（JDK） |
| `CertPins`（除 `pinner()`） | `net/CertPins.kt:53-...` | 纯 JDK 部分 |
| `CertTrust.encode` / `decode` | `net/CertTrust.kt:228,233` | **在 `CertTrust.companion object` 里**（`:219`），见下方警告 |

**`encode` / `decode` 不是免费的**：它们虽然只用 JDK（`Base64` + `CertificateFactory`），但声明在
`CertTrust.companion object` 内，而 `CertTrust` 本体要留在 `:core:protocol`（它用 `KeyStore`/`TrustManager`/OkHttp 的信任管理）。
两个选择：

1. **（推荐）把 `encode`/`decode` 搬到 `:core:config` 的顶层**（例如 `AcceptedCerts.kt`），`CertTrust` 的 companion 改为转调。
   `ConfigStore.kt:212,240` 的两个调用点改成新名字。这是纯搬运，行为不变。
2. 让 `:core:config` 依赖 `:core:protocol` 只为拿这两个函数 —— 把上面的箭头方向整个反过来，**不可接受**。

**`pinner()` 留下的两种处理**（M3 时决定，二选一）：

1. **留在 `:core:protocol`** 作为一个扩展/工具函数（`fun CertPins.pinner(...)` 或搬成 `CertPinning.pinner(...)`）。调用点只有 `DshClient.kt:211` 一处，成本极低。**推荐**。
2. 让 `:core:config` 也带 okhttp —— 不推荐，okhttp 是协议层的依赖，配置层不该有。

**`ConfigStore` 本体留在 `:app`**：它直接 import `android.content.Context` / `SharedPreferences` / `androidx.security.crypto.*`（`ConfigStore.kt:3-5`），是真正的 Android 存储层，与它的数据类分开是对的。

> **判据复用（§2.6 的推广）**：判断一个类能不能进某模块，看的是**依赖闭包**，不是「它看起来属于这个概念」。
> `DshConfig` 听名字像 model，实际闭包横跨 model + term + net。

> **`:core:term` 零出边是被保护的不变量**（2026-10-31 措辞修正，M2.5 实测）：它**不能**依赖任何 Android 库，否则纯 JVM 消费者立刻撞上 §2.6 的变体匹配失败。
> 但「别的模块依赖 `:core:term`」是**允许且必要**的：`:core:config` 是 Android 库，消费纯 JVM 的 `:core:term` 完全合法，依赖方向就是 `config → term`（见上面 §2.8 的图）。
> 上一版这句写成「`:core:config` 反过来依赖 `:core:term` 就违反」，与本节的图自相矛盾 —— 真正被禁的是 `term → 任何 Android 库`，不是 `config → term`。**

> **建议的序列（已按实测修正）**：M1 ✅ → M2 ✅ → **M2.5 `:core:config` 已建成**（不动 M3 的 `net/`）→ M3 抽 `:core:protocol`（`pinner()` 留在此层）→ M4 `data/` 改为依赖 `:core:config`。**每一步都不再有临时妥协。**

### 2.9 M2.5 落地：`:core:config` 建成后的实测（**新**）

本节记录 §2.8 方案的实施结果。**总测试数 1116 保持不变**，CI 实测（PR #8）：`tests=1116 failures=0 errors=0 skipped=0` + `screenshots=4`，分模块 `app: 517` / `model: 413` / `term: 186`，`:core:config` 自身 **0** 个 —— 这是预期的，理由见下方「测试为什么一个都没搬」。

**模块形状**

| 项 | 值 |
|---|---|
| 路径 | `core/config/` |
| 插件 | `com.android.library`（**不是** `kotlin("jvm")`，理由见 §2.8：闭包含 `:core:model`） |
| `namespace` | `uk.xa0.dsh.core.config`（**空 namespace**，不产出 `R`/`BuildConfig`，与 `:core:model` 同一做法） |
| 依赖 | `api(project(":core:model"))` + `api(project(":core:term"))` |
| 出边 | **okhttp：0**（见下方 `pinner()`） |

两个依赖都用 `api` 而不是 `implementation`：`DshConfig` 的**构造参数**就是 `DrawerGrouping` 和 `TerminalCellSize` 类型的字段，消费者不看见这两个类型就无法调用它。这与 `:core:model` 用 `api` 带 `compose-runtime` 是同一个理由。

**搬了什么（包名全部不变，所以 `:app` 侧只有 3 处 import 需要动）**

| 内容 | 从 | 到 |
|---|---|---|
| `DshConfig` + `ThemeMode` + `BusyEnter` | `data/ConfigStore.kt:14-155`（同一个文件的三块） | `core/config/.../data/DshConfig.kt` |
| `AcceptedCert` | `net/CertTrust.kt:16-35` | `core/config/.../net/AcceptedCert.kt` |
| `CertPins`（除 `pinner()`） | `net/CertPins.kt` 整体 `git mv` | `core/config/.../net/CertPins.kt` |

**`encode`/`decode` 的处置**：按 §2.8 的「选择 1」提到了顶层，但**没有沿用原名** —— 改为 `encodeAcceptedCerts` / `decodeAcceptedCerts`。
原因：它们原来在 `CertTrust.companion object` 里，叫 `CertTrust.encode`；提成顶层后如果还叫 `encode`，`import uk.xa0.dsh.net.encode` 就是一个与任何类型都不挂钩的裸动词。加上主语后，`ConfigStore` 的两处调用点读起来仍然是「编码证书」，而且与 `:core:term` 已有的 `encodeHostShellSessions` / `decodeHostShellSessions` 命名一致。
`CertTrust` 本体**没有**跟着搬（它是 KeyStore/TrustManager 的信任管理，M3 随 `net/` 走），所以 `CertTrust` 现在只消费 `AcceptedCert`，不再拥有它的持久化格式。

**`pinner()` 的处置**：按 §2.8 的「推荐项 1」，但它**没有**留在 `:core:protocol`（那个模块还不存在），而是作为**扩展函数** `fun CertPins.pinner(...)` 放进 `:app` 的 `net/CertPinning.kt`。
做成扩展函数是为了**调用点零改动**：`DshClient.kt:211` 与 `CertTrustTest.kt:136` 写的 `CertPins.pinner(...)` 原样可用，不需要 import 改动，也不需要改成 `CertPinning.pinner(...)`。`OKHTTP_PREFIX` 常量随函数一起搬过去，变成函数体内的字面量（它只被这一处用）。

**测试为什么一个都没搬**：`CertTrustTest`（12 个 `@Test`）留在 `:app`。它同时用 `CertPins`（已进 config）和 `CertTrust`（还在 app），而 `CertTrust` 要到 M3 才动 —— 现在搬进 `:core:config` 会立刻编不过。**这是 M2.5 与 M1/M2 的一个真实差别：这次搬的是源码不是测试，测试数不变是正确结果，不是漏搬。**
`:core:config:testDebugUnitTest` 仍然写进了 `verify.yml` 和 `build.yml` 的任务列表：它现在跑 0 个测试，但任务名必须在，否则 M3 搬进来的测试会**静默不跑**（§3.2 那个坑）。

**实测（2026-10-31，PR #8）**：本机 `:core:config:compileDebugKotlin` 与 `:app:compileDebugKotlin` 均通过，**0 处跨模块 smart cast 错误**；CI `tests=1116 failures=0` + `screenshots=4`，截图门禁 `BUILD SUCCESSFUL`（像素未动，符合「拆模块不改变像素」的预期）。
这与 §2.5 的预期相反，值得记下来：`DshConfig` 虽然被 UI 大量消费，但消费方式全是 `.copy(...)` 和直接读**值类型**字段（`String` / `Boolean` / `Float`），没有一处是「先判 `is`/`!= null` 再从属性上取值」。§2.5 的 4 处错误全部出自 **`ChatEntry` 这类 sealed 层级**，而 config 里没有 sealed 类型。**判据：smart cast 错误来自「对跨模块属性做类型收窄」，不是「类型跨了模块」。**

### 2.10 M3 开工前的实测基线：`net/` 现在是什么状态（**新**，取代原文对 M3 的假设）

以下全部在 M2.5（PR #8）合入后重新采集。**原文对 M3 的描述有 4 处已经过时**，逐条列出，否则 M3 会按错误的假设开工。

**① `net → data` 环已不存在，M3 不再被 M4 阻塞**

原文说 `net/DshClient.kt:25` 用 `data.DshConfig`，与 `data → net` 形成环，所以要先做 M4。**现在这条边指向的是 `:core:config` 里的 `uk.xa0.dsh.data.DshConfig`**，不是 `:app` 的 `data/` 包 —— 包名不变（§2.3 方案 A）让同一行 import 的**归属模块**换了。

实测 `net/` 的对外 import，按包名归组：

| 指向包 | 条数 | 实际归属模块 |
|---|---|---|
| `uk.xa0.dsh.model.*` | 25 | `:core:model` |
| `uk.xa0.dsh.term.*` | 9 | `:core:term` |
| `uk.xa0.dsh.data.DshConfig` | 1 | **`:core:config`**（不是 `:app`） |

**`net/` 对 `:app` 的依赖边 = 0。** 反向确认：`net/` 里没有任何 `import uk.xa0.dsh.<根包>` 的语句。所以 M3 可以直接做。

> 这正是「判据看依赖闭包」的第三次应用，但形态和前两次不同：前两次（§2.6 / §2.8）是「源码看着纯、闭包不纯」。这次是**源码一个字没改，闭包却变干净了** —— 因为搬动的是它依赖的那个类，而不是它自己。**判断一条边指向哪个模块，要看被 import 的符号现在住在哪个模块，不能只看包名。** 包名不变（方案 A）让这条判断变得容易出错，而 CI 不会报错，因为编译是通过的。

**② `pinner()` 不在 `:app` 的 `CertPins` 里等着被"留下"，它已经是一个独立的文件**

1.2b 把 `pinner()` 从 `CertPins` 拆出来做成了 `:app` 的扩展函数，落在 `net/CertPinning.kt`（31 行，import 只有 `okhttp3.CertificatePinner`）。
M3 时**整个文件搬进 `:core:protocol`** 即可，`CertPins` 本身不动（它已在 `:core:config`）。原文「`pinner()` 留在此层」表述含糊，容易被读成「从 `CertPins` 里再切一次」。

**③ 要提权的是 2 个 `internal`，不是 1 个**

原文只列了 `hostOf`。实测跨模块调用点后，`CertProbe.systemTrusts` 也是：

| 声明 | 定义 | 跨模块调用点 | M3 处理 |
|---|---|---|---|
| `hostOf(raw: String)` | `net/EndpointResolver.kt:22` | `ui/components/CertificateSection.kt:55` | 改 `public` |
| `CertProbe.systemTrusts(chain)` | `net/CertProbe.kt:94` | `DshViewModel.kt:3927` | 改 `public` |
| `CertTrust.systemTrust` | `net/CertTrust.kt:220` | 无（仅 `CertProbe.kt:97` 同包） | 不动 |
| `isPrivateLanHost` | `net/EndpointResolver.kt:41` | 无（仅 `DshClient.kt:517` 同包） | 不动 |
| `subnetRewritten` | `net/EndpointResolver.kt:63` | 无 | 不动 |

> 原文只数了 1 个，是因为当时 `net/` 和 `ui/` 都在 `:app` 里 —— `hostOf` 那是因为**跨包**才被注意到，`systemTrusts` 被漏了。**提权判据是「调用点是否在新模块之外」，跨包不是判据。** 模块一划，跨包调用全变成跨模块调用。

**④ `:core:protocol` 必须是 `com.android.library`**

`net/` 不是纯 JVM：`DshClient.kt:3-8` 用 `android.content.Context` / `android.net.ConnectivityManager|LinkProperties|Network|NetworkCapabilities` / `android.util.Log`，`PersistentCookieJar.kt:3` 用 `Context`，`RemoteMux.kt:3` 用 `Log`。这一点原文没写死，容易在 M3 时照 `:core:term` 的样子误用 `kotlin("jvm")`。

**⑤ 测试迁移：7 个搬、1 个留**

`app/src/test/java/uk/xa0/dsh/net/` 现有 8 个测试文件、77 个 `@Test`：

| 文件 | `@Test` | 去向 |
|---|---|---|
| `CertTrustTest` | 12 | 搬 |
| `EndpointResolverTest` | 33 | 搬 |
| `MultipartTest` | 12 | 搬 |
| `ConnectivityGateTest` | 5 | 搬 |
| `GatewayAnswerTextTest` | 5 | 搬 |
| `DshClientResetTest` | 4 | **留** —— `@RunWith(RobolectricTestRunner)` + `@Config(application = HarnessApplication::class)`，后者定义在 `app/src/test/.../scroll/ScrollHarnessTest.kt:41` |
| `DoorFaultForAnswerTest` | 3 | 搬 |
| `TransportResetTest` | 3 | 搬 |

搬走 73、留下 4，**总数 77 不变**，所以全仓仍应是 `app: 517 - 73 = 444` / `model: 413` / `term: 186` / `config: 0` / `protocol: 73` = **1116**。

> `:core:protocol` 的测试需要 `testImplementation("org.json:json:20240303")`（`MultipartTest` / `GatewayAnswerTextTest` 解析 JSON，桩实现会抛 `Stub!`，§2.4）与 okhttp 的 test 依赖；`DshClientResetTest` 留下后 `:app` 侧不需要额外改动。

**⑥ `:app` 侧要改的 import：`net.` 前缀基本不动**

因为包名不变，`import uk.xa0.dsh.net.X` 在 M3 之后仍然正确 —— 和 1.1 / 1.2 / 1.2b 一样，**源码零改动**。唯一要改的是 `DshViewModel.kt` 对 `CertProbe.systemTrusts` 的可见性（提权，不改调用）与 `CertificateSection.kt` 的 `hostOf`（同上）。此外 `:app` 需要加 `implementation(project(":core:protocol"))`。

**风险提示（已被 M3 实测推翻，保留原文以便对照）**：原文写「消费面最大，smart cast 错误大概率不是 0」。**实测是 0**（见 §2.11）。错在把「类型跨模块」当成了「会触发 smart cast 失败」的充分条件 —— 实际判据见 §2.11：**只有「从跨模块属性上取值」才会失败，「对局部变量/参数做 `is` 收窄」永远合法**。

### 2.11 M3 落地：`:core:protocol` 建成，并抓出第一个**生产缺陷**（**新**）

**实测（PR #9）**：`tests=1116 failures=0` + `screenshots=4`，分模块 `app: 444` / `model: 413` / `term: 186` / `protocol: 73` / `config: 0` —— 与 §2.10 ⑤ 的预测逐项吻合（预测 `app: 444` / `protocol: 73`）。

**① 拆模块第一次抓到真 bug，而不是证明没有 bug**

前面三次（1.1 / 1.2 / 1.2b）都是纯搬运，全部绿。**这次不同**：`CertTrustTest` 里两个测试在 CI 上红了，而且**不是搬运造成的**。

根因在 `CertTrust.ComposedTrustManager.checkServerTrusted`：当用户的信任锚集合为**空**（`CertTrust()` 的初始状态，也就是每个全新安装的状态）时，平台 PKIX 校验器不把它当"拒绝"，而是抛

```
RuntimeException: Unexpected error:
  InvalidAlgorithmParameterException: the trustAnchors parameter must be non-empty
```

而那段代码只 `catch (e: CertificateException)`。于是运行时异常**穿出 trust manager**，OkHttp 看到的是"传输失败"而不是"证书问题"，**设置页因此永远不提供接受证书的入口 —— 全新安装连不上自签名主机**。

**② 为什么这条以前在 `:app` 里是绿的**

`:app` 的 `debugUnitTest` 类路径上有 `androidx.security:crypto` 带来的 **conscrypt + BouncyCastle**（`:core:protocol` 没有这个依赖）。它们改变了校验器对这个输入的行为。搬进不带该依赖的模块后，**纯 JDK 的答案**（也就是真机在没有这些 provider 时的答案）才暴露出来。

> 这不是"搬坏了"，是**把一层遮蔽搬掉了**。教训要说清：**测试在哪个模块跑，决定它看到的是哪套 provider**。`:app` 的测试类路径被 UI 依赖污染得很重，纯协议层的测试在 `:app` 里跑本来就是失真的。**这是抽模块的收益第一次以"抓到 bug"的形式兑现，而不只是"跑得更快"。**

**③ 修法**

两个 store 的调用都包住 `RuntimeException`，转成 `CertificateException`。系统库那条用 `catch (_: RuntimeException)` 吞掉并 fall through —— 它在无法开始校验时会抛 **NPE**（实测），同样不是拒绝。
面向用户的文案**不用 JDK 原话**（`trustAnchors parameter must be non-empty` 对谁都不是解释），改为 `The certificate could not be verified.`，原始异常保留为 cause。

**④ 没有新增测试，总数仍是 1116**

那两个既有测试**本来就覆盖这条路径**，没有修复就会红 —— 它们已经是最好的回归测试。再加一个只会把计数抬到 1117，破坏"总数恒定"这条不变量。**推理写进了测试自己的注释里**，下一个人读测试时就会遇到，而不是留在 PR 描述里。

**⑤ `:app` 侧的真实改动 = 4 行**

包名不变，所以 `:app` 里数百处 `import uk.xa0.dsh.net.*` 一行没改；只加了 `implementation(project(":core:protocol"))` 一行、两处 `internal` → `public`、以及 `multipart-real.bin` 这个测试 fixture 跟着测试走。

**⑥ 又一次预期被推翻：smart cast 错误是 0**

§2.10 末尾预测"消费面最大，大概率不是 0"，**实测 0**。判据修正：

| 写法 | 跨模块后是否合法 |
|---|---|
| `val e = error as? DshRpcException; e?.code` | ✅ 合法（局部变量） |
| `when (error) { is DshRpcException -> error.code }` | ✅ 合法（`is` 收窄的是**主语**，不是属性） |
| `row.entry is AssistantMessage && row.entry.x` | ❌ 失败（收窄的是**跨模块属性**） |

`:app` 消费 `net.*` 几乎全走前两种，所以 0 处失败。§2.5 那 4 处全是第三种。**判据是"对跨模块属性做类型收窄"，不是"类型跨了模块"。**

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
| **M1** ✅ | #6 `core-model-module` | 新建 `:core:model`（`com.android.library`，**保留包名**，§2.3 方案 A），`model/` 50 文件 + **43** 个纯 JVM 测试（413 个 `@Test`）搬进去；`:app` 加 `implementation(project(":core:model"))`；**4** 个测试留在 `:app`；**2** 个 `internal` 提权（§2.2）；**4 处跨模块 smart cast 修复**（§2.5）；`verify.yml` + `build.yml` 显式加 `:core:model:testDebugUnitTest` 并把 glob 改成 `**/build/test-results/**` | ✅ 实测 `tests=1116 failures=0` + `screenshots=4`，分模块 `app: 703` / `model: 413` |
| **M2** ✅ | #7 `core-term-module` | 新建 `:core:term`（**纯 JVM**，`kotlin("jvm")`，版本声明在根 build），搬 `term/` 7 文件 + **10 个测试文件（186 个 `@Test`）**；`ROUTER_READOUT_TERMINAL_ID` + `isRouterReadoutTerminal` 从 `:core:model` 移入（§2.6），`term → model` 边清零；`:app` 3 处 import 改指向 | ✅ 实测 `tests=1116 failures=0` + `screenshots=4`，分模块 `app: 517` / `model: 413` / `term: 186` |
| **M2.5** ✅ | #8 `core-config-module` | 新建 **`:core:config`**（**`com.android.library`**，理由见 §2.8：闭包含 `:core:model`，做不了纯 JVM），搬 `DshConfig` + `ThemeMode` + `BusyEnter` + `AcceptedCert` + `CertPins`（除 `pinner()`）+ `encode`/`decode` → 顶层 `encodeAcceptedCerts`/`decodeAcceptedCerts`（§2.9）。`pinner()` 成 `:app` 的扩展函数，调用点零改动。**不动 `net/`、不动 `data/` 的存储层**。解 `net ⇄ data` 环 | ✅ 实测 `tests=1116 failures=0` + `screenshots=4`，分模块 `app: 517` / `model: 413` / `term: 186`（`config` 未出现 = 0 个，预期，§2.9） |
| **M3** ✅ | #9 `core-protocol-module` | 新建 `:core:protocol`（`com.android.library` —— `net/` 有 `android.net`/`Context`/`Log`，见 §2.10），搬 `net/` **14** 文件（原文 14 件 − `CertPins` 已去 `:core:config` + 1.2b 的 `CertPinning.kt`，数目仍是 14）；依赖 `:core:model` + `:core:term` + **`:core:config`**（`api`，因 `DshClient.config` 是公开 `var`）+ okhttp。**要提权 2 个**：`hostOf`（`ui/components/CertificateSection.kt:55` 跨模块）与 `CertProbe.systemTrusts`（`DshViewModel.kt:3927` 跨模块）。**`net → data` 环已不存在**（M2.5 已解，见 §2.10），不需要先做 M4 | ✅ 实测 `tests=1116` + `screenshots=4`，`app: 444` / `model: 413` / `term: 186` / `protocol: 73`；**并抓出空信任锚集合的生产缺陷**（§2.11） |
| **M4** | `data-depends-on-config` | **范围已被 M2.5 吃掉大半**：`ConfigStore` 在 1.2b 起就已经从 `:core:config` 拿 `DshConfig` / `AcceptedCert` / 两个 codec 了（靠包名不变，import 一行没改）。剩下要做的是**显式化**：确认 `data/` 对 `net/` 只剩零出边，必要时删掉残留 import | `tests=1116 failures=0` |

> **M2 起每步必做的三件事**（M1 实证）：
> 1. 新模块的测试任务要在 `verify.yml` **和** `build.yml` 里**显式写名字**（依赖模块 ≠ 跑它的测试）；
> 2. 先本机 `:app:compileDebugKotlin` 一遍，把 smart cast 一次收干净，别一轮 CI 才发现；
> 3. 顺手把 `docs/research/13-module-extraction.md` 的实测差异就地更正。

#### 3.1.1 M2（1.2）的实测前置事实（2026-10-31 采集）

M1 之后重新查过 `term/`，比文档原先假设的干净得多：

| 项目 | 实测 | 含义 |
|---|---|---|
| 源码文件 | 7 个 | 与文档一致 |
| **能否纯 JVM** | **源码能** —— 零 `import android` / `androidx` | 见下方⚠️：**但依赖不行** |
| 外部依赖 | 只有 `uk.xa0.dsh.model.isRouterReadoutTerminal` | **这一条就是纯 JVM 方案的致命伤**，见 §2.6 |
| `internal` 声明 | **0 个** | **不需要任何提权**（M1 改了 2 个，M2 是 0） |
| 测试文件 | **10 个 / 186 个 `@Test`** | 全部可搬，**没有**需要留在 `:app` 的 |
| 测试的 `:app` 依赖 | 无 | 唯一的 Robolectric 命中是 `TerminalCellSizeTest.kt:14` 的**注释** |

**所以 M2 的预期是**：`tests=1116`（`:app` 703−186=517 + `model: 413` + `term: 186`），`screenshots=4`。
**smart cast 风险**：`term/` 的类型被 UI 消费的程度远低于 `model/`，但仍需本机编译验证一遍再推 —— 不要假设为 0。

> **实测更正（M2，2026-10-31）**：上面「用 `kotlin("jvm")`」和「依赖 `:core:model` 即可」两条**同时成立不了**。
> `:core:model` 是 `com.android.library`，纯 JVM 消费者会卡在 Gradle 变体匹配上
> （`Doesn't say anything about org.jetbrains.kotlin.platform.type (required 'jvm')`），
> 加成 `androidJvm` 又撞 `r-class-jar`，补满 AGP 属性也无解。详见 §2.6。
>
> 另：`plugins { kotlin("jvm") version "2.4.21" }` 在 AGP 9 构建里会直接失败
> （`already on the classpath with an unknown version`）。**版本必须声明在根 `build.gradle.kts`**，
> 模块里只写 `kotlin("jvm")`。
>
> M2 实际做法见 §3.1.2。

**M3 的临时妥协（已作废，见下）**：`net/DshClient.kt:25` 需要 `DshConfig`。M3 时 `DshConfig` 还在 `:app`，而 `:app` 依赖 `:core:protocol` —— 会成环。原文给了三个选项，其中第 2 条（`AcceptedCert` + `DshConfig` 进 `:core:model`）被标为「**这个顺序是干净的**」并推荐为 M2.5。

> **实测证伪（2026-10-31，M2 之后）**：**这个顺序不干净**，`:core:model` 接不住 `AcceptedCert` + `DshConfig`。逐条证据见 §2.7。正确的做法是**独立出 `:core:config`**，而不是塞进 `:core:model`。

#### 3.1.2 M2 实际怎么做的（2026-10-31）

纯 JVM 之所以能成立，靠的是**先把那条依赖边拆掉**，而不是给它加 Android 插件：

| 动作 | 文件 | 说明 |
|---|---|---|
| 符号搬移 | `model/RouterReadout.kt` → `term/TerminalProtocol.kt` | `ROUTER_READOUT_TERMINAL_ID` + `isRouterReadoutTerminal` 移到用它们的地方 |
| 保留 | `model/RouterReadout.kt` | `ROUTER_READOUT_TITLE` 留在原地（无人引用，但属于 readout 的文案） |
| 改 import | `DshViewModel.kt`、`net/RouterReadoutTransport.kt`、`test/RouterReadoutTest.kt` | 指向 `uk.xa0.dsh.term.*`，纯改名 |
| `git mv` | 7 源文件 + 10 测试文件 | 包名不变，`git status` 全是 `R`（rename），源码零改动 |
| 新 build | `core/term/build.gradle.kts` | `kotlin("jvm")` + `junit` 两项，无 Android、无 `org.json` |

**根 `build.gradle.kts` 加了一行** `id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false` —— 这是纯 JVM 模块能存在的前提，见 §2.6。

**本机实测（这是本仓第一次能在本地验证功能测试）**：

```sh
bash build.sh :core:term:test
# files=10 tests=186 failures=0 errors=0     ← 4 分 30 秒，不需要 SDK/aapt2
```

**`:app:compileDebugKotlin` 实测照常通过，0 处跨模块 smart cast 错误。**
这比 §2.5 的预期好：`term/` 的类型被 UI 消费得浅，`when (action) { is TerminalFrameAction.X -> }` 这类写法不触发
（`is` 分支收窄的是 `val` 参数而不是跨模块属性）。**但 M3 抽 `net/` 时不要据此假设为 0** —— 那时消费面大得多。

**M3 之前请先读 §2.10**：M2.5 之后 `net/` 的状态与原文假设不同 —— 环已解、`pinner()` 已外移、要提权的是 2 个而不是 1 个。

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
| 未提 `:core:term` | 增加 M2 ✅ | `net/` 依赖 `term/` 的 5 个类型 + 2 个函数（§1.2） |
| 未提 `:core:config` | 增加 M2.5 | `DshConfig` 闭包横跨 model+term+net，塞进 `:core:model` 会把最内层变成「什么都依赖」（§2.7、§2.8） |
| 「1 周」 | 6 个 PR × 1 轮 CI，**但每步都要重跑全量 1116 测试** | 纯搬运，风险低；真正的变量是 `internal` 提权（2 处）与依赖边方向 |
| 验收「独立编译 + 单测绿」 | 追加「CI 汇总的测试数仍为 1116」 | 防止静默跳过（§3.2） |
