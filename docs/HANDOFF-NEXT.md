# 交接文档（给新会话）

> 写于 2026-10-31。上一个会话完成了「接管 + 工具链全量升级」，上下文用掉 62%，在此交接。
> **新会话请先读本文件，再按需查其它文档。**

## 1. 一句话现状

仓库 <https://github.com/gitMFQ/dsh-android>（私有，`gitMFQ/dsh-android`）已经是一个**能构建、1116 个测试全绿**的 DSH 原生安卓客户端 fork，工具链升到最新，miuix 已接入但**尚未使用**。下一步是结构拆分与换皮。

## 2. 已完成（L1–L4，全部合入 master）

| 级 | 内容 | 结果 |
|---|---|---|
| **L1** | Kotlin 1.9.24 → **2.4.21**；`composeOptions` → `org.jetbrains.kotlin.plugin.compose`；`kotlinOptions` → `kotlin { compilerOptions { } }`；Robolectric 4.13 → 4.17 | 1116 绿 |
| **L2** | AGP 8.5.2 → **8.13.2**；Gradle 8.7 → **8.13**；compileSdk/buildTools/targetSdk → **36** | 1116 绿 |
| **L3** | AGP → **9.3.1**；Gradle → **9.5.0**；compileSdk → **37**；Compose BOM → **2026.09.00**；**移除 `org.jetbrains.kotlin.android`**（AGP 9 内置 Kotlin） | 1116 绿 |
| **L4** | 接入 miuix **0.9.4**（ui / preference / icons / squircle，**不含 blur**） | 1116 绿 + APK |

每个 PR 都是**单一变量**，失败时能立刻定位。这个方法论请延续。

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
  app/                     # 单模块，140 个 .kt / 5.8 万行（fork 自 bqv/dsh-android v0.6.6）
    src/main/java/uk/xa0/dsh/
      net/                 # 协议层（~4000 行，纯 Kotlin，可单测）
      model/               # 领域模型（47 个单测）
      ui/                  # 界面 54 文件 / 27k 行
      data/ widget/ diag/ term/
    src/test/              # 127 文件 / 1116 个 @Test
  docs/                    # 规划文档（见 §6）
  .github/workflows/       # build.yml（出 APK）/ verify.yml（跑测试）/ release.yml
  build.sh                 # 唯一构建入口，找 .toolchain/gradle-9.5.0
```

## 5. CI

| 工作流 | 触发 | 作用 |
|---|---|---|
| `verify.yml` | push main/master + PR | `tests=1116 failures=0 errors=0 skipped=0` |
| `build.yml` | 同上 | 出 debug APK（约 20 MB） |
| `release.yml` | tag `v*` | 出 release APK 挂 Release |

**提交 PR 即可验证**，`gh pr checks <n>` 看结果，约 7 分钟。

## 6. 文档地图（按需查，别全读）

| 想知道 | 读 |
|---|---|
| 下一步做什么 | [ROADMAP.md](ROADMAP.md) —— 阶段 1 起 |
| 版本为什么是这些 | [research/10-version-lock.md](research/10-version-lock.md) |
| 构建/CI/本机限制 | [BUILD-AND-CI.md](BUILD-AND-CI.md) |
| 设计规范 | [DESIGN-TOKENS.md](DESIGN-TOKENS.md) |
| 协议细节 | [research/01-protocol.md](research/01-protocol.md) |
| API 清单 | [research/02-api-surface.md](research/02-api-surface.md) |
| 多语言 | [I18N.md](I18N.md) |
| 插件化（1.0 后） | [PLUGIN-PLAN.md](PLUGIN-PLAN.md) / [BLOCK-SCHEMA.md](BLOCK-SCHEMA.md) |
| 全部索引 | [README.md](README.md) |

## 7. 下一步（建议顺序）

### 7.0 先做：Roborazzi 截图测试（强烈建议）

阶段 2 要改 `ChatScreen.kt`（**3267 行**），没有截图基线等于盲改。先花 2–3 天接入 Roborazzi，对现有主要界面出基线图。**这是换皮的安全网。**

### 7.1 阶段 1：结构拆分（1 周）

| 任务 | 内容 | 验收 |
|---|---|---|
| 1.1 | 抽 `:core:protocol`（`net/`，~4000 行纯 Kotlin） | 独立编译 + net 单测绿 |
| 1.2 | 抽 `:core:model`（`model/`，47 个单测） | 47 个单测绿 |
| 1.3 | 建 `:design` 模块：`KsuTokens` + 主题 7 档外观 | 设置页可切外观并持久化 |

### 7.2 阶段 2：外壳换装（1.5 周，对应 DESIGN-TOKENS 的 D2–D5）

浮动底栏 + 4 目的地 pager / TopBar 折叠 / 抽屉换装 / 设置页换装 / Sheet 统一。

## 8. 给新会话的提醒

- **别重新调研**：`docs/research/` 有 12 份带 `path:line` 证据的文档，结论都在里面。
- **别信旧结论**：文档里凡是标注「已被 X 修正」的，以修正后的为准。
- **单变量原则**：一次只改一个版本/一件事，CI 绿了再进下一步。
- **本机只做静态分析**：任何「能不能跑」的判断都必须由 CI 回答。
- **权限**：仓库是私有的，`gh` 已登录 `gitMFQ`。要公开需先处理 MPL-2.0 源码公开义务（转公开正好满足）。
