# 研究 08 · 把 miuix 接入我们的 fork：升级代价（实测）

> 结论先行：**能用，但不是免费的**。miuix 0.9.4 把下限钉在 Compose Multiplatform 1.12 / Kotlin 2.4；我们的 fork 现在停在 Kotlin 1.9.24 / Compose BOM 2024.09.00 / AGP 8.5.2，**必须一次性大升级**。
> **⚠️ 决策更新（2026-10-30）**：用户已定为「全量升级到最新」，因此**走 0.9.4**（配合 AGP 9.5 / Gradle 9.8 / Kotlin 2.4.21 / compileSdk 37），最终矩阵见 [09-toolchain-latest.md](09-toolchain-latest.md)。**本文 §7 的 0.9.2 方案降级为「备选路线」**——只在全量升级三天内收敛不了时启用。

## 1. 三个版本的依赖矩阵（实测自 Maven Central `.module` 文件）

| 版本 | compose.foundation | kotlin-stdlib | materialkolor | material3-window-size-class | 说明 |
|---|---|---|---|---|---|
| 0.9.0 | 1.10.3 | 2.3.20 | 4.1.1 | 1.9.0 | 模块名还是 `miuix-shapes`（后改名 squircle） |
| 0.9.2 | 1.11.1 | 2.4.0 | 4.1.1 | 1.9.0 | 引入 squircle |
| **0.9.4** | **1.12.0** | **2.4.20** | 5.0.1 | 1.12.0-alpha03 | 最新，模块齐全（含 miuix-nav） |

**为什么 0.9.4 最省**：0.9.0/0.9.2 也要求 Kotlin 2.3/2.4，与 0.9.4 的差距只有 Compose 一个小版本；而 0.9.4 多了 `miuix-nav`（预测性返回）与完整 squircle。所以**直接上 0.9.4**。

### 1.1 真正的硬门槛：`minCompileSdk`

实测各 AAR 的 `aar-metadata.properties`：

| 依赖 | minCompileSdk | minAGP | 结论 |
|---|---|---|---|
| `androidx.compose.foundation:foundation-android:1.10.3` | **35** | 8.6.0 | 我们的 compileSdk 34 **不够** |
| `androidx.compose.foundation:foundation-android:1.11.1` | **35** | 8.6.0 | 同上 |
| `androidx.compose.foundation:foundation-android:1.12.0` | **37** | **9.1.0** | 需要 compileSdk 37 + AGP 9.1+ |

> 这是本次升级最硬的约束：**不是 Kotlin 版本，而是 compileSdk**。AGP 会直接报 `Dependency requires compileSdk 37`。

## 2. 我们的 fork 现状（`/tmp/dsh-android-prior`）

| 项 | 现value | 必须变成（miuix 0.9.4） | 备注 |
|---|---|---|---|
| Kotlin | 1.9.24 | **2.4.x** | 跨 1.9→2.4 大版本 |
| AGP | 8.5.2 | **9.1.0+** | 跨 8→9 大版本（AGP 9 有大量 breaking change） |
| Gradle | 8.7 | **9.x** | AGP 9 要求 Gradle 9 |
| Compose 编译器 | `composeOptions.kotlinCompilerExtensionVersion = 1.5.14` | **org.jetbrains.kotlin.plugin.compose**（Kotlin 2.x 的内置插件，DSL 整体替换） | 必改 |
| Compose BOM | 2024.09.00 | 至少 androidx compose **1.12** | 由 CMP 1.12 传递 |
| compileSdk | 34 | **37** | 硬要求 |
| targetSdk | 34 | 35 或 36（可选） | Play 政策；侧载可不改 |
| buildToolsVersion | 35.0.0 | **37.0.0** | 跟着 compileSdk |
| minSdk | 26 | 26（可保持） | miuix 全库 minSdk 24 |

## 3. 分阶段升级方案（每步都有测试闸门）

| 步 | 动作 | 闸门（证明这步没坏） | 风险 |
|---|---|---|---|
| S1 | Gradle 8.7 → 9.x；AGP 8.5.2 → 9.1+；compileSdk/buildTools → 37 | `./gradlew :app:assembleDebug` 通过 | AGP 9 移除的 DSL（`packagingOptions`、`buildConfigField` 语法、`namespace` 强制） |
| S2 | Kotlin 1.9.24 → 2.4.x；`composeOptions` 换成 `org.jetbrains.kotlin.plugin.compose` | **1 116 个 JVM 测试全绿** | K2 编译器行为变化、`kotlinOptions` 被 `compilerOptions` 取代 |
| S3 | Compose BOM → 1.12.x（或直接由 miuix 传递） | 同上 + Roborazzi 截图基线 | 47 个 Robolectric 测试可能因 Compose 内部变化失败 |
| S4 | 加 miuix 依赖（最小集） | 编译 + 手点 3 个页面 | 无 |
|
> **S1 与 S2 不要合并**：AGP 9 + Kotlin 2.4 一起上，出错时无法判断是谁的锅。

## 4. 会踩的坑（针对本仓库）

| 坑 | 位置 | 处理 |
|---|---|---|
| `composeOptions.kotlinCompilerExtensionVersion` | `app/build.gradle.kts:69` | Kotlin 2.x 下整段删除，改 `plugins { alias(libs.plugins.compose.compiler) }` |
| `kotlinOptions { jvmTarget/freeCompilerArgs }` | `app/build.gradle.kts:75-82` | 换成 `compilerOptions { }`（Kotlin 2.x DSL） |
| `android.builder.sdkDownload=false` | `gradle.properties` | 升级后若缺 SDK 37 平台包，构建直接失败 → CI 要先 `sdkmanager "platforms;android-37"` |
| Robolectric 版本 | `app/build.gradle.kts` testImplementation | Compose 1.12 + Robolectric 旧版会崩；需升到支持 SDK 37 的版本 |
| `security-crypto 1.1.0-alpha06` | `app/build.gradle.kts` | AGP 9 下 alpha 依赖可能被拒；建议改 `EncryptedSharedPreferences` 替代或升 stable |
| `navigation-compose 2.7.7` | 声明但零使用 | 升级时干脆删掉 |
| Compose 1.12 的 `minCompileSdk=37` | 传递依赖 | 必须把 `compileSdk` 提到 37，否则 Gradle 直接失败 |

## 5. 依赖块（`gradle/libs.versions.toml` 风格）

### A. 最小集（推荐起步）

```toml
[versions]
miuix = "0.9.4"

[libraries]
miuix-ui         = { module = "top.yukonga.miuix.kmp:miuix-ui",         version.ref = "miuix" }
miuix-preference = { module = "top.yukonga.miuix.kmp:miuix-preference", version.ref = "miuix" }
miuix-icons      = { module = "top.yukonga.miuix.kmp:miuix-icons",      version.ref = "miuix" }
miuix-squircle   = { module = "top.yukonga.miuix.kmp:miuix-squircle",   version.ref = "miuix" }
miuix-blur       = { module = "top.yukonga.miuix.kmp:miuix-blur",       version.ref = "miuix" }
```

> ⚠️ `miuix-blur` 模块 **minSdk = 33**（`miuix-blur/build.gradle.kts:31`）。我们的 minSdk 是 26，直接引入会被 manifest merger 拒绝。两条路：① `tools:overrideLibrary` + 运行时 `isRenderEffectSupported()` 判断；② **放弃 miuix-blur**，玻璃效果用 `Modifier.blur` 手写 + API<31 降级。**建议先选 ②**。

### B. 完整集（+ 导航 + 着色器）

```toml
miuix-nav    = { module = "top.yukonga.miuix.kmp:miuix-nav",    version.ref = "miuix" }
miuix-shader = { module = "top.yukonga.miuix.kmp:miuix-shader", version.ref = "miuix" }
```

> `miuix-nav` 与我们自研的 pager 二选一；`miuix-shader` 只在需要 AGSL 时才要。

## 6. 体积

| 依赖 | AAR 大小 |
|---|---|
| miuix-ui-android-0.9.4.aar | **1 858 KB**（`.module` 实测） |
| miuix-preference / icons / squircle / blur | 未逐个下载，但 icons 有 156 个图标文件，预计 300–600 KB |
| 合计增量（压缩后 APK） | **约 1.5–2.5 MB** |

## 7. 判定：**当前走 0.9.4（用户已选全量最新）**；0.9.2 是降级备选

关键发现：**0.9.4 逼你上 AGP 9 + compileSdk 37，0.9.2 只要 AGP 8.6 + compileSdk 35** —— 而 AGP 8.6 正好兼容 fork 现有的 **Gradle 8.7 + build-tools 35.0.0**。0.9.2 路线**不换 Gradle、不换 build-tools**。

| 项 | 0.9.2 路线（推荐） | 0.9.4 路线 |
|---|---|---|
| Kotlin | 2.4.x（**必升**） | 2.4.x（必升） |
| Compose | CMP 1.11.1 → androidx compose **1.11.x** | CMP 1.12.0 → androidx compose 1.12 |
| AGP | **8.6+**（与现有 8.5.2 只差一版） | **9.1+**（大版本跳跃） |
| Gradle | **8.7（可不动）** | 9.x（必升） |
| compileSdk | **35** | 37 |
| buildTools | **35.0.0（可不动）** | 37.0.0 |
| squircle / preference / icons / blur | 都有 | 都有 |
| miuix-nav | 没有（我们本就不需要） | 有 |

> Kotlin 2.x 的升级**任何版本都躲不掉**：miuix 0.9.0 起就用 Kotlin 2.3+ 编译，其 metadata 版本 Kotlin 1.9 读不了。既然躲不掉，就选代价最小的 0.9.2。

### 落地顺序（每步可独立回滚）

| 步 | 动作 | 闸门 |
|---|---|---|
| **S1** | Kotlin 1.9.24 → 2.4.x；`composeOptions` → `org.jetbrains.kotlin.plugin.compose`；`kotlinOptions` → `compilerOptions` | **1 116 个 JVM 测试全绿**（不带任何 miuix） |
| **S2** | AGP 8.5.2 → 8.6+；compileSdk 34 → 35；Compose 升到 androidx 1.11.x | `assembleDebug` 通过 + 测试全绿 |
| **S3** | 引入最小集（ui / preference / icons / squircle，**不含 blur**） | 编译通过 + 手点三页 |
| **S4** | 外壳换装（DESIGN-TOKENS §8.1 的组件逐个替换） | 截图测试无回归 |

### 明确不做

- ❌ **不引 `miuix-blur`**（模块 minSdk=33，我们 26）。玻璃底栏自写：`Modifier.blur`(API31+) + 描边高光，API<31 纯色降级。
- ❌ **不引 `miuix-nav`**（0.9.2 也没有）。根导航保持 fork 的 `AppPhase` + 自研 pager。
- ❌ **不用 Miuix 的 Markdown**（它没有）；继续用 fork 的 `MarkdownView.kt` 原生渲染。
- ❌ **不为 miuix 抬 minSdk**（保持 26；miuix 全库 minSdk 24，兼容）。

### 版本证据说明

- `minCompileSdk=37` 是从 miuix **0.9.4** 的 `miuix-ui-android-0.9.4.aar` 与 `miuix-preference-android-0.9.4.aar` 的 `META-INF/com/android/build/gradle/aar-metadata.properties` **实测**得来。
- 0.9.2 的 `minCompileSdk` 未直接取到（下载多次截断），**推断值 = 35**，依据是其传递依赖 `androidx.compose.foundation:foundation-android:1.11.1` 实测 `minCompileSdk=35`、`minAndroidGradlePluginVersion=8.6.0`。**落地前需在 CI 跑一次确认**。
