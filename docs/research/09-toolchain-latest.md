# 全量升级到最新：工具链矩阵（实测版本）

> ⚠️ **本文的版本结论已被 [10-version-lock.md](10-version-lock.md) 修正**：AGP 9.5.0 与 Gradle 9.8.2 **超出 Kotlin 2.4.21 官方兼容矩阵上限**（AGP ≤9.3.1、Gradle ≤9.7.0）。**执行请以 10 为准**（AGP 9.3.1 + Gradle 9.5.0）。本文保留作为「最新版本一览」与 DSL 改动清单的依据。

> 用户决定：**全部升到最新，并采用 miuix 0.9.4**。本文给出「最新」的确切含义、版本配对、以及本仓库必须改的每一处 DSL。
> 版本均于 2026-10-30 实查 Maven Central / Google Maven / services.gradle.org。

## 1. 最新稳定版一览（实测）

| 组件 | 最新稳定 | 来源 |
|---|---|---|
| Gradle | **9.8.2** | `services.gradle.org/versions/current` |
| AGP | **9.5.0**（9.6.0 仅 alpha） | Google Maven `com.android.tools.build:gradle` |
| Kotlin | **2.4.21**（2.5.0 仅 Beta） | Maven Central `kotlin-gradle-plugin` |
| Kotlin 地板 | **≥2.3.0**（1.9.24 读不了任何在售 miuix 的 metadata；实测 0.9.4 需 2.4.0） | kotlinc 编译探针实测 |
| Compose BOM | **2026.09.00** | Google Maven `androidx.compose:compose-bom` |
| androidx.compose.ui | *1.12.1*（1.13 仅 beta） | Google Maven |
| Robolectric | **4.17** | Maven Central |
| compileSdk / buildTools | **37**（由 miuix 0.9.4 强制） | miuix `aar-metadata.properties` 实测 |
| JDK | **21**（miuix 自身用 21） | `build-logic/BuildConfig.kt` `JDK_VERSION = 21` |

> 注意 Kotlin 的「最新稳定」是 **2.4.21**，不是 2.5.0（只有 Beta1/Beta2）。同理 AGP 用 9.5.0，不追 9.6.0-alpha。

## 2. 版本配对（关键约束链）

```
Gradle 9.8.2
   └─ AGP 9.5.0  （AGP 9.x 要求 Gradle 9.x）
        └─ compileSdk 37 + build-tools 37.0.0   （miuix 0.9.4 的 minCompileSdk=37）
             └─ Kotlin 2.4.21
                  └─ org.jetbrains.kotlin.plugin.compose（Kotlin 2.x 内置 Compose 编译器）
                       └─ miuix 0.9.4（Kotlin 2.4.20 stdlib + CMP 1.12.0）
```

**为什么被迫升 AGP 9**：miuix 0.9.4 的 AAR 写着 `minCompileSdk=37`，而 `androidx.compose.foundation:foundation-android:1.12.0` 的 AAR 写着 `minCompileSdk=37` **且** `minAndroidGradlePluginVersion=9.1.0`。三者锁死，无法只升一部分。

## 3. 本仓库必须改的 DSL（`app/build.gradle.kts` + 根 `build.gradle.kts`）

| # | 现状 | 新版写法 | 位置 |
|---|---|---|---|
| 1 | `id("com.android.application") version "8.5.2"` | `version "9.5.0"` | 根 |
| 2 | `id("org.jetbrains.kotlin.android") version "1.9.24"` | `version "2.4.21"` + **新增** `id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"` | 根 |
| 3 | `composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }` | **整段删除**（改由 compose 插件提供） | app |
| 4 | `kotlinOptions { jvmTarget / freeCompilerArgs }` | `kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17; freeCompilerArgs.addAll(...) } }` | app |
| 5 | `buildToolsVersion = "35.0.0"` | `"37.0.0"`（或直接删掉，让 AGP 选默认） | app |
| 6 | `compileSdk = 34` | `37` | app |
| 7 | `targetSdk = 34` | `36` 或 `37`（Play 要求 ≥35；侧载无所谓） | app |
| 8 | `namespace = "uk.xa0.dsh"` | 改成自己的（见 FORK-SURGERY） | app |
| 9 | `testOptions { unitTests { isIncludeAndroidResources = true } }` | 保留（AGP 9 语法未变） | app |
| 10 | `packagingOptions`（若有用到） | `packaging { }`（AGP 9 统一为 `packaging`） | app |
| 11 | `android.builder.sdkDownload=false` | **必须改**：现在需要 SDK 37 平台包，要么在 CI 预装，要么允许下载 | gradle.properties |
| 12 | `org.gradle.jvmargs=-Xmx2560m` | 提到 `-Xmx3g`（AGP 9 + K2 更吃内存） | gradle.properties |

## 4. 测试风险（1 116 个 `@Test`）

| 风险 | 说明 | 应对 |
|---|---|---|
| **Robolectric 4.17 对 SDK 37** | 反编译 `DefaultSdkProvider.class` 实测：**支持到 SDK 37**（`bipush 37` + `17-robolectric-15733970-i7`），且 `android-all-instrumented` 里确有此 jar | 可直接 `@Config(sdk = [37])`；若个别 API 行为差异再降级 |
| Compose 1.12 的测试语义变化 | 47 个 Robolectric/Compose 测试可能因内部变化红 | 先跑一遍，逐个修；保留截图基线 |
| K2 编译器严格性 | Kotlin 2.x 对某些推断更严格 | 预计少量 `@Suppress` 或显式类型 |
| `security-crypto 1.1.0-alpha06` | AGP 9 对 alpha 依赖不拒，但建议升 | 升到最新 stable 或换 androidx.security 的替代 |

## 5. 依赖块（`gradle/libs.versions.toml`，全量最新）

```toml
[versions]
agp = "9.5.0"
kotlin = "2.4.21"
gradle = "9.8.2"
composeBom = "2026.09.00"
miuix = "0.9.4"
robolectric = "4.17"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }

[libraries]
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
miuix-ui = { module = "top.yukonga.miuix.kmp:miuix-ui", version.ref = "miuix" }
miuix-preference = { module = "top.yukonga.miuix.kmp:miuix-preference", version.ref = "miuix" }
miuix-icons = { module = "top.yukonga.miuix.kmp:miuix-icons", version.ref = "miuix" }
miuix-squircle = { module = "top.yukonga.miuix.kmp:miuix-squircle", version.ref = "miuix" }
```

> **仍然不引 `miuix-blur`**：它 `minSdk = 33`，而我们是 26。玻璃效果继续自写（`Modifier.blur` API31+ + 降级）。这一条不因「全量升级」而改变——除非把 minSdk 提到 33。

> `miuix-nav` 现在可用了（0.9.4 才有），但仍**不建议**：根导航保持 fork 的 `AppPhase`，避免迁移全部路由到 `@Serializable NavKey`。

## 6. 升级阶梯（每级一个闸门；★=经二次验证的关键纠正）

> **重要纠正**：原计划的「先升 Kotlin，再跳 AGP 9」顺序不成立——**Kotlin 2.x 与 AGP 8.5.2 不兼容**，且 miuix **没有任何版本**能用 compileSdk 35（见下）。必须走三级阶梯。

### 6.1 ★ 两个被实测推翻的假设

| 假设 | 实测结论 |
|---|---|
| 「miuix 0.9.2 只要 compileSdk 35，可以不换 Gradle」 | **假**。`miuix-ui-android` 的 **0.9.0 / 0.9.2 / 0.9.4 三个版本 AAR 全部 `minCompileSdk=37`**（我独立下载解包复核过）。**没有中间站** |
| 「Gradle 9.8.2 是随便选的」 | 不精确。**AGP 9.5.0 的 `GRADLE_MIN_VERSION = 9.6.0`**（反编译 `VersionCheckPlugin$Companion` 得到），9.8.2 满足；AGP 9.1.0 要求 9.3.1 |

### 6.2 阶梯

| 级 | 内容 | 接 miuix | 闸门 |
|---|---|---|---|
| **L0** | 基线：AGP 8.5.2 / Gradle 8.7 / Kotlin 1.9.24 / BOM 2024.09 / compileSdk 34 | 无 | 1 116 测试全绿（现状） |
| **L1** | Kotlin **2.4.21** + `plugin.compose`；**AGP 8.13.2** + **Gradle 8.13**；compileSdk **36**；buildTools 36 | 无 | `assembleDebug` + 1 116 测试 + 配置缓存 |
| **L2** | AGP **9.5.0** + Gradle **9.6.0+**（用 9.8.2）；compileSdk **37**；buildTools 37.0.0 | 无 | 同上四项 |
| **L3** | Compose BOM **2026.09.00** + miuix **0.9.4** 最小集 | 0.9.4 | 同上 + `dependencyInsight` 确认无旧 foundation 残留 |
| **L4** | + `miuix-nav`（可选） | 0.9.4 全量 | 同上 + 返回栈回归 |

**为什么 L1 要停在 AGP 8.13 / compileSdk 36**：Kotlin 2.4 的编译器迁移（`composeOptions` 删除、`kotlinOptions`→`compilerOptions`）是最容易出错的一步，把它和 AGP 9 的大跳分开，失败时能立刻定位是谁的锅。

> **绝不**把五个版本 + miuix 依赖放进一个提交。即使工期紧，L1 与 L2 也必须两个提交。

## 7. 已知必须验证的点（本机做不了）

| 点 | 为什么 | 怎么验证 |
|---|---|---|
| AGP 9 的最低 JDK | 未取到常量（jar 内检索失败）；**但已验证 JDK 17 能跑**：kotlinc 2.3.0 可读 major-65 类、D8 8.5.27 能 dex 它 | CI 用 17 起步，若报错再上 21 |
| Robolectric 与 compileSdk 37 | **已验证支持**（4.17 内置 SDK 37 映射） | CI 跑测试确认 |
| miuix 0.9.4 在 AGP 9 下的 manifest 合并 | 未实测 | CI `assembleDebug` |
| aapt2 37 在 aarch64 | 本机无 aarch64 aapt2 | 只能 CI（见 BUILD-AND-CI.md） |
