# Fork 改造手术方案（bqv/dsh-android → 我们自己的客户端）

> 基线：`/tmp/dsh-android-prior`（`bqv/dsh-android` v0.6.6，MPL-2.0）。140 个 .kt / 8.0 万行；342 个 JVM 测试（model 47 / ui 40 / term 10 / scroll 9 / net 8 / widget 6 …），全部可无设备运行。
> 本文只讲“怎么改、按什么顺序改、每步验收”，视觉规范见 [DESIGN.md](DESIGN.md)，构建与 CI 见 [BUILD-AND-CI.md](BUILD-AND-CI.md)。

## 1. 第一步：让它成为“我们的仓库”（半天）

| # | 动作 | 关键点 | 验收 |
|---|---|---|---|
| 1 | GitHub 建私有仓库 → `git clone` 上游 `--depth 1` → 改 remote `origin=自己的 fork`，新增 `upstream=bqv` | 保留上游历史以便 cherry-pick；**不要 squash** | `git remote -v` 四个 URL 正确 |
| 2 | 分支模型：`main`（= 上游最新 + 我们的补丁）、`restyle/*`（设计改造分支）、`release/*` | 设计改造全部走 PR 合并，保证 `main` 随时可出包 | 分支保护开启 |
| 3 | 包名/应用名/图标替换 | 见 §2 清单 | `assembleDebug` 通过，手机装出的是新名字 |
| 4 | `LICENSE` 保留 MPL-2.0 + 新建 `NOTICE.md` 记录上游与第三方 | MPL 要求保留原许可与声明 | 仓库根可见 |

## 2. 改包名 / 改品牌 清单（按编译可活的顺序）

顺序很重要：先改名，再动结构，最后换皮。

| 步骤 | 位置 | 说明 |
| 1 | `app/build.gradle.kts:35` `applicationId`、`:8` `namespace` | 建议 `io.github.<你自己的>.dsh`；debug 后缀 `.debug` 保留 |
| 2 | Kotlin 包路径 `app/src/main/java/uk/xa0/dsh/**` → 新包名 | IDE 重构（Move + Rename package），**不要 sed 全量替换字符串** |
| 3 | `AndroidManifest.xml`：`package`、`android:label`、权限、`MainActivity` FQN、deep link scheme | 改了包名后 FQN 全变 |
| 4 | `res/values/strings.xml` 与 `themes.xml`（`Theme.DSH`） | 改显示名与窗口背景 |
| 5 | `res/mipmap-*/ic_launcher*` + `mipmap-anydpi-v26` 自适应图标 | 换自己的图标 |
| 6 | `AboutScreen.kt` / `SettingsContent.kt` 里的 “DSH” 字样与版本展示 | 文案与品牌 |
| 7 | `.github/workflows/*.yml` 中的 artifact 名、release 名称、包名 grep | CI 产物命名 |
| 8 | `README.md` / `docs/` 品牌与截图 | 对外说明，注明 fork 自何处、遵守 MPL |

> 全量核对命令：`grep -rl "uk.xa0.dsh" --include=*.kt --include=*.kts --include=*.xml . | wc -l` —— 改完应为 0（除 LICENSE/NOTICE 里的署名）。

## 3. 模块拆分（可选，建议在换皮前做，降低后续冲突面）

现状是**单模块 `:app`**（`settings.gradle.kts` 只有 `include(":app")`）。建议按“先抽纯 Kotlin、后抽 UI”的顺序：

| 阶段 | 新模块 | 从 `:app` 搬出 | 风险 |
|---|---|---|---|
| M1 | `:core:protocol` | `net/`（DshClient 1155、EndpointResolver 673、RemoteMux 575、CertTrust 273、Multipart 152、PersistentCookieJar 122、CertPins、CertProbe、ConnectivityGate、TransportReset） | 低：这些文件**不依赖 Compose**，且已有 8 个 net 单测 |
| M2 | `:core:model` | `model/`（Transcript、Trajectory、ToolCallTree、ApprovalRoster、SessionRoster…） | 低：纯数据类 + 47 个单测 |
| M3 | `:design` | 新建：`DshTheme` 语义层 + Miuix 组件封装 + `ColorMode` | 无（新代码） |
| M4 | `:feature:chat` / `:feature:sessions` / `:feature:settings` | `ui/` 按界面切分（ChatScreen 3267 行可先不切，只搬） | 中：`R` 类、资源引用、`viewModel()` 注入 |

> 保守做法：**只做 M1+M2+M3**，UI 继续留在 `:app`。这样既拿到可复用的协议/模型层，又避免大规模资源迁移风险。

## 4. 上游同步策略

- `upstream` 拉取后，用 `git cherry-pick` 合入 `main`；冲突高发文件（我们会大改的）应**提前标注**：
  - `ui/theme/DshTheme.kt`、`ui/ChatScreen.kt`、`ui/SessionsDrawer.kt`、`ui/components/Composer.kt`、`ui/SettingsContent.kt`
- 建议把这几个文件**先原样拷贝成我们的新文件**（如 `ui/theme/AppTheme.kt`），上游文件保留不动 → 后续上游更新零冲突，我们的改动集中在自己的文件里。
- 每个上游版本升级建 issue + 一次全量测试跑（342 个 JVM 测试是主要防线）。

## 5. 测试与 CI

| 项 | 现状 | 我们怎么做 |
|---|---|---|
| 单测 | 342 个纯 JVM（无需设备/模拟器） | `bash build.sh :app:testDebugUnitTest`；CI 里作为构建前门禁 |
| UI 测试 | 无 androidTest（上游仅少量） | 先不引入；用 Roborazzi 截图测试作为**视觉回归**（换皮阶段必备） |
| 构建 | `build.sh` 唯一入口；无 wrapper；JDK 17 + Gradle 8.7 + `/opt/android-sdk` | CI 用 `ubuntu-latest` 复现 toolchain；本地见 [BUILD-AND-CI.md](BUILD-AND-CI.md) |
| 签名 | release 走环境变量（`SIGNING_STORE_FILE` 等 4 个）或 CI secrets | **首发前定死 keystore**，否则 debug 签名包无法被正式包覆盖安装 |
| 版本 | `versionCode=15`, `versionName=0.6.6`，debug 有 `.debug` 后缀 | 改为 `versionCode` 递增策略 + 自己的 `versionName`（如 `1.0.0-<sha>`） |
| 分发 | GitHub Release（`release.yml`，tag `v*`） | 沿用；接 Obtainium 需要 release APK 名稳定、附带 changelog |

## 6. 法律义务（MPL-2.0）

| 义务 | 具体做法 |
|---|---|
| 文件级 copyleft | **修改过的上游文件必须继续以 MPL-2.0 提供源码**；新增文件可用任意许可 |
| 标记修改 | 在每个改过的上游文件头部加注释：`// Modified from bqv/dsh-android (MPL-2.0) on YYYY-MM-DD` |
| 保留声明 | 保留原 `LICENSE`；新建 `NOTICE.md` 列出上游仓库、commit、第三方依赖许可 |
| 不得混入 GPL | **不得 cp KernelSU-Style-UI-Kit 的 .kt**（GPL-3.0 会传染整包）。视觉可参考，代码重写 |
| Miuix 依赖 | Apache-2.0，可安全依赖；随包附其 LICENSE 声明 |

## 7. 第一周任务（依赖顺序，每项 ≤1 天）

| # | 任务 | 验收 |
|---|---|---|
| 1 | 建仓 + 分支 + NOTICE + 上游 remote | `git remote -v`、`main` 能跑 CI |
| 2 | 改包名/应用名/图标（§2 全清单） | 手机安装后显示新名，342 测试仍绿 |
| 3 | 抽 `:core:protocol` + `:core:model`（§3 M1/M2） | 模块可独立编译，单测迁移后全绿 |
| 4 | 建 `:design` 骨架（`DshTheme` 适配 + `ColorMode` 7 档 + Miuix 依赖） | 设置页能切外观并持久化 |
| 5 | Scaffold 换装（TopBar + FloatingBottomBar + pager） | 底栏三页可切换 |

## 8. 与计划的冲突点提醒

- fork 目前**没有底部导航**，只有抽屉 + 页内 `ViewTabs`（Chat/Trajectory）。引入 pager 前先确认不与既有手势/抽屉冲突（`drawerDrag` 在 `ChatScreen.kt:2749`）。
- fork 的 `DshTheme` 是“忠实复刻 Web 端令牌”，换皮时**必须保留语义层**（见 DESIGN.md §4），否则工具卡状态色会失真。
- fork 的 Markdown 是**原生 Compose**（`ui/markdown/Markdown.kt` 591 + `MarkdownView.kt` 514），不要为了“像参考项目”而换成 WebView。
