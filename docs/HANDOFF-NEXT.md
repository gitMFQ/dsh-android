# 交接文档（给新会话）

> 写于 2026-10-31（当日第二版）。第一版交接了「接管 + 工具链全量升级」；本版追加了结构拆分的第一步与截图基线。
> **新会话请先读本文件，再按需查其它文档。**

## 1. 一句话现状

仓库 <https://github.com/gitMFQ/dsh-android>（私有，`gitMFQ/dsh-android`）已经是一个**能构建、1116 个功能测试全绿 + 4 张截图基线**的 DSH 原生安卓客户端 fork，工具链升到最新，miuix 已接入但**尚未使用**。

**换皮的安全网已经就位**（Roborazzi，PR #5），**结构拆分的第一个前置条件也已清掉**（`model/` 不再依赖根包，PR #4）。下一步是阶段 1 的模块抽取。

## 2. 已完成（L1–L4，全部合入 master）

| 级 | 内容 | 结果 |
|---|---|---|
| **L1** | Kotlin 1.9.24 → **2.4.21**；`composeOptions` → `org.jetbrains.kotlin.plugin.compose`；`kotlinOptions` → `kotlin { compilerOptions { } }`；Robolectric 4.13 → 4.17 | 1116 绿 |
| **L2** | AGP 8.5.2 → **8.13.2**；Gradle 8.7 → **8.13**；compileSdk/buildTools/targetSdk → **36** | 1116 绿 |
| **L3** | AGP → **9.3.1**；Gradle → **9.5.0**；compileSdk → **37**；Compose BOM → **2026.09.00**；**移除 `org.jetbrains.kotlin.android`**（AGP 9 内置 Kotlin） | 1116 绿 |
| **L4** | 接入 miuix **0.9.4**（ui / preference / icons / squircle，**不含 blur**） | 1116 绿 + APK |
| **1.0** | 5 个宿主行类（`SessionItem`/`WorkspaceItem`/`JobItem`/`QueueAttachment`/`QueuedMessage`）从 `DshViewModel.kt` 移入 `model/`；`model → 根包` 的 import **14 → 0** | 1116 绿（PR #4） |
| **1.5** | Roborazzi **1.77.0** + 4 张基线（About / Startup / 抽屉 / 抽屉空态），411×891 | `tests=1116` + `screenshots=4`（PR #5） |

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

## 4. 仓库结构

```
/root/dsh-android
  app/                     # 单模块，143 个 .kt / 5.8 万行（fork 自 bqv/dsh-android v0.6.6）
    src/main/java/uk/xa0/dsh/
      net/                 # 协议层（~4000 行，纯 Kotlin，可单测）
      model/               # 领域模型 50 文件（47 个单测）
      ui/                  # 界面 54 文件 / 27k 行
      data/ widget/ diag/ term/
    src/test/              # 128 文件 / 1116 个功能 @Test + 4 张截图基线
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
| 1.1 | 抽 `:core:model`（**必须 `com.android.library`**） | 50 文件 + 45 个纯 JVM 测试；3 个 Robolectric 测试留 `:app`；3 个 `internal` 提权 | 独立绿 + 全仓仍 1116 |
| 1.2 | 抽 `:core:term`（`term/` 只依赖 `:core:model`） | 7 文件 | 仍 1116 |
| 1.2b | `AcceptedCert` + `DshConfig` 进 `:core:model` | 解 `net ⇄ data` 环 | 仍 1116 |
| 1.3 | 抽 `:core:protocol`（`net/`） | 14 文件 | 仍 1116 |
| 1.4 | 建 `:design` 模块：`KsuTokens` + 主题 7 档外观 | 设置页可切外观并持久化 |

> **抽模块时必改一处**：`verify.yml` 的汇总只扫 `app/build/test-results/**`，测试搬进新模块就会**从统计里消失而 CI 照样绿**。同一 PR 里把 glob 改成 `**/test-results/**`，并贴出前后数字。

### 7.2 阶段 2：外壳换装（1.5 周，对应 DESIGN-TOKENS 的 D2–D5）

浮动底栏 + 4 目的地 pager / TopBar 折叠 / 抽屉换装 / 设置页换装 / Sheet 统一。

## 8. 给新会话的提醒

- **别重新调研**：`docs/research/` 有 13 份带 `path:line` 证据的文档，结论都在里面（新增 13 是模块拆分蓝图）。
- **别信旧结论**：文档里凡是标注「已被 X 修正」的，以修正后的为准。
- **单变量原则**：一次只改一个版本/一件事，CI 绿了再进下一步。
- **新增的 `workflow_dispatch` 工作流在合并到默认分支前无法手动触发**：GitHub 只在默认分支上发现该工作流。要第一次跑起来，得先把文件合进 master，或临时加一个 `push:` 触发（PR #5 用了后者，随后删掉）。
- **别用 `--tests` 去「打开」被 `excludeTestsMatching` 排除的测试类**：Gradle 的 filter 不能再包含已被另一个 filter 排除的东西，会报 `No tests found for given includes`。用 `-Pscreenshots` 这类 property 切换（PR #5 踩过）。
- **本机现在能跑 Gradle 配置阶段**（`.toolchain/gradle-9.5.0` 已就位，`gradle :app:tasks` 约 1–2 分钟），改 build 脚本后可先本地验语法再花 CI 一轮。`local.properties` 指向 `/opt/android-sdk`。但 `compileDebugUnitTestKotlin` 仍因 `android-37.0` 的 SDK 元数据问题失败，**编译与测试仍只能走 CI**。
- **本机只做静态分析**：任何「能不能跑」的判断都必须由 CI 回答（配置阶段例外，见上一条）。
- **权限**：仓库是私有的，`gh` 已登录 `gitMFQ`。要公开需先处理 MPL-2.0 源码公开义务（转公开正好满足）。
