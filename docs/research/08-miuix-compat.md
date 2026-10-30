# 08 · 把 miuix 接进 fork：真实代价与工具链可行性（实测定稿）

> **结论**：代价 **不在"加一行依赖"，而在"compileSdk 37 + AGP ≥9.1 + Gradle ≥9.3.1 + Kotlin ≥2.3 + Compose 1.12"** 这条链。链上的硬点由**传递依赖的 AAR 元数据**决定，不是作者偏好。
> 证据：本机文件 `path:line`；Maven 事实为 2026-10-30 用 `curl` 实取 `pom / .module / aar-metadata.properties`；两条真机实验（kotlinc 编译探针、D8 dex 计量）用于判定"能不能只升一点"。缩写：**MC**=`repo1.maven.org/maven2/top/yukonga/miuix/kmp/`、**GM**=`dl.google.com/dl/android/maven2/`、**F:**=`/tmp/dsh-android-prior/`、**U:**=`/tmp/miuix/`。
> ⚠️ 本文修正旧版 08 的两处错误（§8.1）。决策口径见 [09-toolchain-latest.md](09-toolchain-latest.md)。

## 0. TL;DR

| 问题 | 答案 | 证据 |
|---|---|---|
| miuix 自用栈 | Kotlin 2.4.21 / AGP 9.5.0 / Gradle 9.8.2 / CMP 1.12.2 / compileSdk 37 / minSdk 24 / JDK 21 | `U:gradle/libs.versions.toml:2-3,10`、`U:build-logic/src/main/kotlin/BuildConfig.kt:5,11,13,14,15,16`、`U:gradle/wrapper/gradle-wrapper.properties:3` |
| 消费者地板 | **Kotlin ≥2.3.0**、**compileSdk 37**、**AGP ≥9.1.0**、**androidx compose 1.12.x**、**Gradle ≥9.5.0** | §2.2 / §3 |
| 三版哪个最省 | **0.9.4**——0.9.0/.1/.2/.3/.4 **五版全 `minCompileSdk=37`**，降版本零收益 | §2.2 |
| 现有链能否承载 | **不能**，且**无任何 0.9.x 可绕开**；唯一低配解是旧聚合坐标 `miuix:0.2.9` | §3、§8.3 |
| 代价 | 一次不可回避的工具链跃迁 + 1116 测试回归 | §7、§8.2 |
| 体积 | A 集 AAR **3.90 MiB** / B 集 **4.14 MiB**；D8 dex 全量 **7.46 MB**（开 R8 前） | §6 |


## 1. miuix 自述版本与模块图

| 维度 | 值 | 证据 |
|---|---|---|
| AGP / Kotlin | 9.5.0 / 2.4.21 | `U:gradle/libs.versions.toml:2-3` |
| Compose Multiplatform | 1.12.2；window-size-class 用 1.12.0-alpha03 | 同上 `:10-11` |
| Gradle wrapper | 9.8.2 | `U:gradle/wrapper/gradle-wrapper.properties:3` |
| compileSdk / buildTools / targetSdk | 37 / "37.0.0" / 37 | `U:...BuildConfig.kt:11,13,15` |
| minSdk（库） | 24；**blur 单独 33** | 同上 `:14`；`U:miuix-blur/build.gradle.kts:31`（AAR manifest 实测 `minSdkVersion="33"`） |
| JDK / jvmTarget | 21 ⇒ class **major 65**（javap 实测 `ScaffoldKt.class`） | `U:...module.kotlin-jvm-toolchain.gradle.kts:9` |
| Kotlin metadata | `mv=[2,4,0]`、`xi=48`（ui/icons/core 三模块实测一致） | `javap -v` 各模块首个 class |
| 发布模块 | 9 个（core/ui/preference/shader/blur/squircle/icons/nav/window） | `U:settings.gradle.kts:45-53` |

| 模块 | api / impl | 证据 |
|---|---|---|
| miuix-ui | api core+squircle+JB-foundation；impl navigationevent、m3-window-size-class、materialkolor | `U:miuix-ui/build.gradle.kts:60-69` |
| miuix-preference / icons | preference: api ui+foundation；icons: api core+foundation | `U:miuix-preference/build.gradle.kts:53-56`、`U:miuix-icons/build.gradle.kts:51-54` |
| miuix-squircle / blur | api shader + foundation | `U:miuix-squircle/build.gradle.kts:196-200`、`U:miuix-blur/build.gradle.kts:54-56` |
| miuix-nav | squircle + navigationevent + JB lifecycle×4 + serialization-json + collections-immutable | `U:miuix-nav/build.gradle.kts:55-64` |
| **miuix-window** | **仅 `jvm("desktop")`，无 android 目标 ⇒ 不可用** | `U:miuix-window/build.gradle.kts:20-21` |

## 2. Maven Central 实测

### 2.1 三个 0.9.x 的依赖矩阵（`.pom` + `.module` variants 实读）

| 版本 | compose.foundation | kotlin-stdlib | materialkolor | m3-window-size-class | navigationevent | 发布日（`curl -I`） |
|---|---|---|---|---|---|---|
| 0.9.0 / 0.9.2 | 1.10.3 / 1.11.1 | 2.3.20 / 2.4.0 | 4.1.1 | 1.9.0 | `org.jetbrains.androidx:1.0.1` / `1.1.0` | 2026-04-02 / 2026-06-05 |
| **0.9.4** | **1.12.0** | **2.4.20** | 5.0.1 | **1.12.0-alpha03** | **`androidx.navigationevent:1.1.2`** | 2026-09-20 |

模块首版实测（`MC <a>/maven-metadata.xml` 首条）：`preference/blur/core`=0.9.0、`squircle/shader`=0.9.2、`icons`=0.8.0-rc01、`nav`=0.9.4-rc01、`shapes`=**仅 0.9.0**（0.9.2 起改名 squircle）。

### 2.2 `minCompileSdk`：**五个 0.9.x 版本全部 = 37**

| 构件 | `minCompileSdk` | `minAGP` | AAR 字节 |
|---|---|---|---|
| miuix-ui-android **0.9.0** / **0.9.2** / **0.9.4** | **37** / **37** / **37** | 1.0.0 | 1,534,745 / 1,683,175 / **1,902,787** |
| miuix-preference-android 0.9.0 / 0.9.2 / 0.9.4 | **37** / **37** / **37** | 1.0.0 | 113,907 / 241,467 / 244,990 |
| miuix-icons-android 0.9.0 / 0.9.4 | **37** / **37** | 1.0.0 | 1,218,917 / 1,878,935 |
| miuix-core / squircle / shader / blur (0.9.4) | **37**（blur manifest `minSdk 33`） | 1.0.0 | 13,033 / 54,304 / 6,006 / 185,342 |
| 旧聚合 `miuix-android:0.8.8` | **36** | 1.0.0 | 1,618,019 |

读法：`unzip -p <aar> META-INF/com/android/build/gradle/aar-metadata.properties`；manifest 用 `unzip -p <aar> AndroidManifest.xml | strings | grep minSdkVersion`（0.9.0/0.9.2 为 23，0.9.4 为 24，blur 33）。

### 2.3 Compose → androidx 映射（`.module` android 变体声明 + 目标 AAR 元数据）

| CMP `foundation` | → `androidx.compose.foundation` | 该 AAR `minCompileSdk`/`minAGP` | 被哪个 miuix 用 |
|---|---|---|---|
| 1.7.0 / 1.7.1 | 1.7.0 / 1.7.1 | **34 / 1.0.0** | 0.2.9 / 0.3.0 |
| 1.7.3 | 1.7.6 | 34 / 1.0.0 | 0.3.4 |
| 1.8.0-beta01 | 1.8.0-beta02 | **35 / 1.0.0** | 0.3.8 / 0.4.0 |
| 1.8.2 | 1.8.2 | 35 / 1.0.0 | 0.4.9 |
| 1.9.3 | 1.9.4 | — | 0.6.1 / 0.7.2 |
| 1.10.3 | **1.10.5** | 35 / **8.6.0** | 0.8.8 / **0.9.0** |
| 1.11.1 | **1.11.2** | 35 / **8.6.0** | **0.9.2** |
| 1.12.0 | 1.12.0（`foundation-android`，4,441,825 B） | **37 / 9.1.0** | **0.9.4** |

**第二根锁（只有 0.9.4 有）**：`material3-window-size-class:1.12.0-alpha03` 的 android 变体声明 `androidx...material3-window-size-class:1.5.0-alpha22`，该 AAR 实测 `minCompileSdk=37 / minAGP=9.1.0`。0.9.0/0.9.2 用的 `1.9.0` 无 android 变体，实际吃 `androidx...:1.4.0`（`minAGP=8.6.0`）⇒ 0.9.4 比 0.9.2 多一根同门锁。

**问题 1 的直接答案**：三版共同门票是 `compileSdk=37`；映射到 androidx 是 **BOM 2026.08.00（foundation 1.12.0）/ 2026.09.00（1.12.1）**（0.9.4）、**BOM 2026.05.00（1.11.1）**（0.9.2）、**BOM 2026.03.01（1.10.6）**（0.9.0）。因 AGP 8.x 最高只到 API **36.1**（AGP 8.13 发布说明），三版都省不到 AGP/Kotlin 工作 ⇒ **选 0.9.4：同价换 squircle + nav + 完整 icons**。

## 3. 硬门槛矩阵与 AGP→Gradle 下限

| 构件 | `minCompileSdk` | `minAGP` |
|---|---|---|
| miuix-ui-android **0.9.0/.1/.2/.3/.4** | **37**（五版全部，实测） | 1.0.0 |
| `androidx.compose.foundation:foundation-android:1.12.0 / 1.12.1` | **37** | **9.1.0** |
| `androidx.compose.ui:ui-android:1.12.1` / `ui-test-junit4-android:1.12.1` | **37** | **9.1.0** |
| `androidx...material3-window-size-class-android:1.5.0-alpha22` | **37** | **9.1.0** |
| `androidx.navigationevent:navigationevent-compose-android:1.1.2` | **36** | **8.9.1** |
| `androidx.compose.foundation-android:1.9.4/1.10.0/**1.11.1**` 与 `material3-android:1.4.0` | **35** | 8.6.0 |
| `androidx.security:security-crypto:1.1.0` / `1.1.0-alpha06` | **34** / 33 | — |

**AGP → Gradle 下限**（逐个 AGP jar 反编译 `VersionCheckPlugin.GRADLE_MIN_VERSION`，并与官方发布说明交叉核对）：

| AGP | 8.5.2（现） | 8.6.0 | 8.7.3 | 8.13.2 | 9.0.0 | **9.1.0** | **9.2.x** | **9.3.x** | **9.4.1/9.5.0** |
|---|---|---|---|---|---|---|---|---|---|
| 需 Gradle | 8.7 | 8.7 | 8.9 | 8.13 | 9.1.0 | **9.3.1** | 9.4.1 | 9.5.0 | **9.6.0** |
| 最高 API | 34 | 35 | 35 | **36.1** | 36.1 | — | 37.0 | 37 | 37 |

> 只要引入 miuix 0.9.x，Gradle 会把 `androidx.compose.foundation` 抬到 1.12.x ⇒ **AGP ≥9.1.0 且 compileSdk =37**，否则 `CheckAarMetadata` 直接失败。**此门槛与选 0.9.0 还是 0.9.4 无关。**

**KGP 官方矩阵相关行**（`kotlinlang.org/docs/gradle-configure-project.html` 实抓）：`2.4.20–2.4.21 → Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1`；`2.2.20–2.2.21 → Gradle ≤8.14 / AGP ≤8.11.1`；`2.0.20–2.0.21 → Gradle ≤8.8 / AGP ≤8.5`。
⇒ **AGP 上限 9.3.1，不是 9.5.0**：上游自用 9.5.0 已超出 JetBrains 文档矩阵，**不要照抄**；fork 取 `AGP 9.3.1 + Gradle 9.5.0 + Kotlin 2.4.21` 才是文档内自洽组合。

## 4. 与 fork 的逐项对照

fork 现状：`F:build.gradle.kts:2-3`（AGP 8.5.2 / Kotlin 1.9.24）、`F:app/build.gradle.kts:8`（compileSdk 34）、`:32`（buildTools 35.0.0）、`:36-37`（minSdk 26 / targetSdk 34）、`:69-71`（`composeOptions 1.5.14`）、`:78-84`（`kotlinOptions`）、`:110`（BOM 2024.09.00 = ui/foundation 1.7.0、m3 1.3.0）、`:126`（navigation-compose 2.7.7）、`:127`（security-crypto 1.1.0-alpha06）、`:129`（okhttp 4.12.0）、`:135`（coroutines 1.8.1）、`:148`（Robolectric 4.13）、`:88`（`isMinifyEnabled=false`）、`F:build.sh:44`（Gradle 8.7）、`F:build.sh:11`（JDK 17.0.20.1）、`F:gradle.properties:8`（`sdkDownload=false`）。

| 项 | 现状 | 需要 | 判定 | 目标 |
|---|---|---|---|---|
| Kotlin/KGP | 1.9.24 | **≥2.3.0** | 强制 | 2.4.21 |
| Compose 编译器 | `composeOptions{1.5.14}` `:69-71` | K2 内置 `plugin.compose` | 强制（1.9.24 的编译器插件在 MC **404**，2.4.21 在架 200） | 插件版=Kotlin 版 |
| Compose BOM | 2024.09.00 | androidx 1.12.x | 强制（1.7.0 与 1.12 混搭必炸） | 2026.09.00（ui 1.12.1 / m3 1.4.0） |
| compileSdk / buildTools | 34 / 35.0.0 | **37 / 37.0.0** | 强制 | 37 |
| AGP / Gradle | 8.5.2 / 8.7 | **≥9.1.0 / ≥9.5.0**（文档上限 9.3.1 / 9.7.0） | 强制 | 9.3.1 / 9.5.0 |
| JDK | 17.0.20.1 | 产物 major 65 | **不必升**（kotlinc 2.3.0 在 JDK 17 读 major-65 编译成功；D8 8.5.27 亦可 dex） | 保持 17 |
| minSdk / targetSdk | 26 / 34 | 24（blur 例外 33）/ 无要求 | 不强制 | 26 / 37 可选（**blur 排除**） |
| Robolectric | 4.13 `:148` | 提 compileSdk 后需 ≥37 | 条件强制 | 4.17（实测 21→37） |
| security-crypto / navigation | alpha06 `:127` / 2.7.7 `:126` | GA 1.1.0 `minCompileSdk=34` / **源码 0 引用** | 可选 / 直接删 | 1.1.0 / — |
| okhttp / coroutines | 4.12.0 `:129` / 1.8.1 `:135` | 无约束（35 处引用）/ 与 JB lifecycle 2.11.0 对齐 | 不动 / 建议升 | 4.12.0 / 1.11.0 |
| `kotlinOptions{}` | `:78-84` | `kotlin { compilerOptions {} }` | 强制 | AGP 9 迁移文档明文 |

### 4.1 注解处理器扫描（"冲突"部分）：**全仓 0 命中**

`grep -rn 'kapt|ksp|kotlinx-serialization|plugin.serialization|parcelize'` 于 `F:**/*.kts` 与 `F:app/src/**/*.kt` **均无输出**，源码 0 处 `kotlinx.serialization` 引用 ⇒ **K2 迁移无 APT/serialization 风险**。
唯一例外：`miuix-nav` 自带 serialization 插件并依赖 `kotlinx-serialization-json:1.11.0`（`miuix-nav-0.9.4.pom`），接 B 集须给 app 加 `org.jetbrains.kotlin.plugin.serialization:2.4.21`，否则路由类无法序列化。

### 4.2 真 kotlinc 编译探针：`import top.yukonga.miuix.kmp.theme.MiuixTheme`

| kotlinc | 0.9.4 (mv 2.4.0) | 0.8.8 (mv 2.3.0) | 0.7.2 (mv 2.2.0) | 0.0.1 (mv 2.0.0) |
|---|---|---|---|---|
| 1.9.24（现用） | ❌ `expected 1.9.0` | ❌ `actual 2.3.0` | ❌ `actual 2.2.0` | ✅（但缺全部现用组件） |
| 2.2.21 | ❌ `expected 2.2.0` | ❌ | ✅ | ✅ |
| 2.3.0 / 2.3.20 / 2.4.x | ✅ | ✅ | ✅ | ✅ |

⇒ **Kotlin ≥2.3.0 是任何现役 miuix 的绝对地板**；0.9.x 再叠加 compileSdk 37。


## 5. 两套依赖块（`gradle/libs.versions.toml` 风格）

共同前置：compileSdk 37、buildTools 37.0.0、AGP 9.3.1、Gradle 9.5.0、Kotlin 2.4.21、BOM 2026.09.00、`alias(libs.plugins.composeCompiler)`、删 `composeOptions`（`:69-71`）、`kotlinOptions{}`→`compilerOptions{}`（`:78-84`）。

### A. 最小集（ui + preference + icons + squircle，**不含 blur**）

```toml
[versions]
miuix      = "0.9.4"
composeBom = "2026.09.00"
[plugins]
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
[libraries]
miuix-ui         = { module = "top.yukonga.miuix.kmp:miuix-ui",         version.ref = "miuix" }
miuix-preference = { module = "top.yukonga.miuix.kmp:miuix-preference", version.ref = "miuix" }
miuix-icons      = { module = "top.yukonga.miuix.kmp:miuix-icons",      version.ref = "miuix" }
miuix-squircle   = { module = "top.yukonga.miuix.kmp:miuix-squircle",   version.ref = "miuix" }
# miuix-core 由 ui/icons 传递（api），通常无需显式声明
```

**A 集传递拉进来的 androidx**（`MC */0.9.4/*.pom` 逐一实测）：

| 传递项 | 版本 | 由谁带入 | 该 AAR 硬门槛 |
|---|---|---|---|
| `androidx.compose.foundation:foundation` | **1.12.0** | ui / preference | `minCompileSdk=37, minAGP=9.1.0` |
| `androidx...material3-window-size-class` | **1.5.0-alpha22**（经 JB 1.12.0-alpha03） | ui | `37 / 9.1.0` |
| `androidx.navigationevent:navigationevent-compose` | 1.1.2 | ui | `36 / 8.9.1`；再拉 `ui-android 1.11.2` |
| `com.materialkolor:material-color-utilities` | 5.0.1 | ui | 226,131 B |
| `org.jetbrains.kotlin:kotlin-stdlib` | **2.4.20** | 全部 | — |
| `top.yukonga.miuix.kmp:miuix-core` | 0.9.4 | ui / icons | 37 |

> ⚠️ **miuix-blur 排除**：`U:miuix-blur/build.gradle.kts:31` `minSdk = 33`（AAR manifest `minSdkVersion="33"`），fork 是 `minSdk 26`（`:36`）⇒ manifest merger 拒绝。玻璃效果用 `Modifier.blur` + 描边自写；`miuix-squircle` 自身 `minSdk 24` 可提供 squircle 形状。

### B. 全量（+ nav + shader，blur 仅当 minSdk 提到 33）

```toml
[versions]
kotlinxSerialization        = "1.11.0"
kotlinxCollectionsImmutable = "0.5.1"
[plugins]
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
[libraries]
miuix-nav    = { module = "top.yukonga.miuix.kmp:miuix-nav",    version.ref = "miuix" }
miuix-shader = { module = "top.yukonga.miuix.kmp:miuix-shader", version.ref = "miuix" }  # 已由 squircle 传递
miuix-blur   = { module = "top.yukonga.miuix.kmp:miuix-blur",   version.ref = "miuix" }  # 仅 minSdk≥33
```

`miuix-nav-0.9.4.pom` 额外拉：`navigationevent-compose:1.1.2`、`JB foundation:1.12.0`、`org.jetbrains.androidx.lifecycle:{runtime,runtime-compose,viewmodel,viewmodel-compose}:2.11.0`（android 变体声明 `androidx.lifecycle:lifecycle-runtime:2.11.0`）、`kotlinx-collections-immutable:0.5.1`、`kotlinx-serialization-json:1.11.0`。
**不要写 `miuix-window`**：无 Android 产物（metadata 404，模块仅 desktop 目标）。

## 6. 体积

### 6.1 AAR 实重与内部构成（`.module` 的 `size` 字段 = HTTP `content-length`，两者一致）

| 产物 | AAR 字节 | `classes.jar` | class 数 | class 未压缩字节 |
|---|---|---|---|---|
| `miuix-ui` | **1,902,787**（=1858 KiB） | 1,956,683 | 568 | 5,063,818 |
| `miuix-icons` | 1,878,935 | 1,910,326 | 156 | 4,112,084 |
| `miuix-preference` | 244,990 | 249,538 | 31 | 764,818 |
| `miuix-nav` | 235,818 | 253,301 | 114 | 579,645 |
| `miuix-blur` | 185,342 | 194,575 | 66 | 484,378 |
| `miuix-squircle` | 54,304 | 57,055 | 8 | 423,294 |
| `miuix-core` / `miuix-shader` | 13,033 / 6,006 | 14,588 / 6,129 | 10 / 4 | 23,485 / 10,408 |

| 组合 | AAR 合计 |
|---|---|
| **A 集**（ui+pref+icons+squircle+core） | **4,094,049 ≈ 3.90 MiB** |
| **A'+blur+shader**（若 minSdk 33） | 4,285,397 ≈ 4.09 MiB |
| **B 集**（A+shader+nav） | **4,335,873 ≈ 4.14 MiB** |

> 题干"1858 KB"实为 **1858 KiB**（1,902,787 ÷ 1024），非 1858 kB。

### 6.2 D8 实测（`--min-api 26`，无 minify）

| 组合 | dex 字节 | ≈APK 增量（dex 再压缩约减半） |
|---|---|---|
| ui + core + squircle + preference + blur | **3,598,800** | ≈ +1.8 MB |
| + icons | 3,577,828 | 累计 ≈ +3.6 MB |
| + nav | 268,376 | 累计 ≈ +3.7 MB |
| 全量合计 | **7,456,820** | ≈ **+3.7 MB**（含 icons） |

fork release 目前 `isMinifyEnabled=false`（`:88`）⇒ 上述为上界；开 R8 后 156 个图标对象与未用组件显著回落（`miuix-icons` 占 A 集 class 未压缩字节 **40%**，最大可裁项；`miuix-ui.aar` 已带 `baseline-prof.txt` 529,963 B）。**不开 R8 就别接 A 集；`miuix-icons` 必须按需引用。**

## 7. 风险：1116 个 JVM 测试会坏在哪

fork 现实（实测）：单模块（`F:settings.gradle.kts:18`）、主源 267 个 .kt、**1116 个 `@Test`**（model 437 / ui 231 / term 186 / widget 78 / net 77 / scroll 25 / markdown 19 / diag 13 / components 12 / composer 10）、**47 个测试文件跑 Robolectric**（`@RunWith(RobolectricTestRunner)` + `@Config(sdk=[34], application=HarnessApplication::class)`）、**37 个用 `createComposeRule`**、无 kapt/KSP/serialization。`F:README.md:63` 仍写"342 tests"，已过时，以 `grep -rho '@Test' app/src/test | wc -l` = 1116 为准。

| 序 | 风险面 | 失败形态 | 证据 |
|---|---|---|---|
| 1 | `composeOptions` | K2 下 `kotlinCompilerExtensionVersion` unknown ⇒ **全仓 `@Composable` 报错**（**编译期**炸，不等测试） | `:69-71` |
| 2 | `kotlinOptions{}` | AGP 9 内置 Kotlin 下 `Unresolved reference: kotlinOptions`；Gradle 9 配置缓存更易挂 | `:78-84` |
| 3 | 工具链落位 | `build.sh` 硬编码 `gradle-8.7`；仓内**无 wrapper、无 `gradle/` 目录** | `F:build.sh:44`、`F:.github/workflows/build.yml:75` |
| 4 | **47 Robolectric + 37 Compose-rule 测试** | Compose 1.12 帧调度/语义树与 Robolectric 4.13 组合易红；`createComposeRule` 的 test-manifest 变化 | `:148`、`:149-150` |
| 5 | 纯 JVM 大头（≈778 个） | **基本不会红**：无 Android 依赖、无协程调度假设 | 目录分布见上 |
| 6 | security-crypto | Tink + R8 需 keep；alpha06 与新 Compose 无直接耦合 | `F:app/src/main/java/uk/xa0/dsh/data/ConfigStore.kt:5-6` |
| 7 | SDK platform 37 | `/opt/android-sdk` 不存在；`sdkDownload=false` ⇒ 无法自动补装 | `F:gradle.properties:8` |

**Robolectric 特有坑**：`:63-67` 把测试 JVM 的 `user.home` 改到 `.toolchain/robolectric-home`（沙箱只读）。升级后 `android-all` 版本会变（SDK 34 jar 名仍为 `14-robolectric-10818077-i7`），缓存目录必须保留，否则 47 个测试全部去下载并因只读而挂。
许可：miuix 为 **Apache-2.0**（`miuix-ui-0.9.4.pom` `<license>`），与 fork MPL-2.0 并存无冲突，需记入 `NOTICE.md`（`F:docs/FORK-SURGERY.md:12`）。

## 8. 迁移阶梯（每步独立闸门）

### 8.1 两处必须纠正的旧说法

| 旧说法 | 实测 | 影响 |
|---|---|---|
| "0.9.2 只要 AGP 8.6 + compileSdk 35" | ❌ `miuix-ui-android-0.9.2.aar` 元数据 **`minCompileSdk=37`**（五版逐一实测全 37） | **降级路线不成立** |
| "0.8.8 可作中间站（ui+icons）" | ⚠️ `miuix-ui-android` 在 0.9.0 前**不存在**（0.8.8 该 URL 返回 554 B 的 404 页）；只有聚合坐标 `miuix-android:0.8.8`（`minCompileSdk=36`、mv 2.3.0、manifest minSdk 23） | 中间站只能整包引入旧坐标，价值低 |

### 8.2 阶梯 + 每步闸门命令

| 级 | 内容 | 覆盖 miuix | 闸门命令（证明本步没坏） |
|---|---|---|---|
| **L0** 基线 | AGP 8.5.2 / Gradle 8.7 / Kotlin 1.9.24 / BOM 2024.09.00 / compileSdk 34 | 无 | `bash build.sh :app:testDebugUnitTest` + `:app:assembleDebug`（1116 绿） |
| **L1** 工具链先动 | Kotlin 2.4.21 + `plugin.compose`（与 L0 同提交换掉 `composeOptions`）；BOM 2026.01.00（ui 1.10.1）；AGP 8.13.2 + Gradle 8.13 + compileSdk 36 + buildTools 36 + Robolectric 4.17 | 无 | ① `assembleDebug` 0 error ② **`testDebugUnitTest` 1116 全绿**（37 个 compose 测试重点） ③ `--configuration-cache` ④ 真机装包 |
| **L2** 跳 AGP 9 | AGP 9.3.1 + Gradle 9.5.0 + compileSdk 37 + buildTools 37.0.0 | 无 | 同 L1 四项 |
| **L3** 接 miuix | BOM 2026.09.00 + **A 集**（不含 blur） | 0.9.4 A 集 | ① 四项 ② `gradle :app:dependencyInsight --dependency androidx.compose.foundation` 确认全 1.12.x 无 1.7.0 残留 ③ 47+37 个测试全绿 ④ APK 增量 ≤4 MB ⑤ API<33 降级路径 |
| **L4** 全量 | + nav（+ serialization 插件） | 0.9.4 B 集 | 同 L3 + 返回栈行为回归 |

**不要**五处版本 + miuix 依赖一个提交（失败无法定位）。L1 与 L2 合并时仍须**分两个提交**（工具链 / BOM）。**L1 的 `composeOptions` 必须在同提交换成 `plugin.compose`**——这是"第一个坏掉"的地方，且它在**编译期**就炸，不会等到测试。

### 8.3 「太贵」的替代路线（问题 4）

**有**，但等于退回单模块时代（聚合坐标 `miuix` 发到 0.8.8 为止，0.9.0 起才拆 9 模块）。

| 方案 | miuix | Kotlin | AGP / Gradle | compileSdk | androidx foundation | 模块面 |
|---|---|---|---|---|---|---|
| **最省（近乎零改）** | **0.2.9** | 2.0.21 | **8.5.2 ✓ / 8.7 ✓** | **34 ✓** | 1.7.0（`minCompileSdk=34`） | 仅单体（509,073 B、`minCompileSdk=1`、minSdk 26、`major 61`/`mv=[2,0,0]`） |
| 折中 / 较可用 | 0.3.8 / 0.4.9 | 2.1.20 / 2.2.20 | 8.7.2 / 8.12.1 · 8.11.1 / 8.14 | 34–35 / 35 | 1.8.0-beta02 / 1.8.2（35） | 单体（978,131 B `mv=[2,1,0]` / 947,707 B `mv=[2,2,0]`） |
| 有独立 icons | 0.8.8 | 2.3.20 | 9.0.0 / 9.1.0 | **36** | 1.10.5（`minAGP=8.6.0`） | `miuix` + `miuix-icons` |
| 全功能 | 0.9.4 | 2.4.20 | 9.3.1 / 9.5.0 | **37** | 1.12.0（`minAGP=9.1.0`） | 9 模块 |

**最小可行组合**：`miuix 0.2.9` + `Kotlin 2.0.21` + `AGP 8.5.2` + `Gradle 8.7` + `compileSdk 34` + `androidx Compose 1.7.x` —— fork 现状**除 Kotlin 与 compose 插件外全部满足**，约 1 天工作量。
**代价**：`miuix-preference/blur/nav` 都不存在（首版 0.9.0/0.9.0/0.9.4-rc01），`miuix-icons` 要 0.8.0-rc01 起；且 0.2.9 包结构为 `anim/basic/extra/icon/miuix/theme/utils`（`classes.jar` 顶层目录实测），与 [07-miuix-api.md](07-miuix-api.md) 盘点的 0.9.4 API **不是同一套** ⇒ 07 文档全部路径无法复用，等于重写接入层。**只在"必须这周出 Miuix 观感"时才值得。**

## 9. 未验证项（落地前须在 CI 复现）

| 项 | 状态 | 影响 |
|---|---|---|
| AGP 9.5.0 的最低 JDK | 未取到常量（jar 内字符串检索失败） | 若要求 21，改 `F:build.sh:11` 一处 |
| AGP 9.x DSL 细节 | 仅确认 `BuildFeaturesImpl`/`ComposeOptionsImpl` 仍存在 | `F:app/build.gradle.kts:42-106` 可能需少量改写；`packaging`（`:100`）已是新写法 |
| 本机能否真跑 Gradle / Robolectric 4.13×Compose 1.12 | **否**（`/opt/android-sdk` 缺失、`gradle` 命令不存在）；后者未测 | 结论来自构件元数据 + kotlinc/D8 实验，**非完整构建验证**；已按"升 4.17"给保守方案 |
| `miuix-navigation3-*` / `miuix-shapes` | 不在源码树（`U:settings.gradle.kts:45-53`）；shapes 仅 0.9.0 | 未来使用需重新评估 |

## 10. 判定（问题 7）

**有条件采纳：0.9.4 + A 集（不含 blur），但只能作为 L1–L2 升级链的最后一步（L3），不能当先决条件。**

唯一收益是 Miuix 视觉还原度（[07-miuix-api.md](07-miuix-api.md) 的 30+ composable 与 15 项需求覆盖），代价是一次**不可拆的横切**：`compileSdk 34→37` 由 miuix `minCompileSdk=37` 强制；`AGP→≥9.1` 由 androidx compose 1.12 的 `minAGP=9.1.0` 强制（0.9.4 还叠加 `material3-window-size-class 1.5.0-alpha22` 一根同门锁）；`Kotlin→≥2.3`（实际 2.4）由 `kotlin-stdlib 2.4.20`+`mv=[2,4,0]` 强制；`Gradle→≥9.5` 由 AGP 9.3.1 强制。五项无一能"只升一半"，而它们压在本就单模块的 fork 与 **1116 个 JVM 测试**上——其中仅 47 Robolectric + 37 Compose-rule 会被 Compose 1.7→1.12 语义变化打中，≈778 个纯 JVM 测试几乎不受影响，这是本项目最有利的一点；而 `kotlinOptions`/`composeOptions` 两处在编译期就炸，不会等到测试。"降版本省钱"是伪选项：**0.9.0/0.9.2 的 AAR 同样 `minCompileSdk=37`**（五版全 37），省不到任何 AGP/Kotlin 工作，只少两个模块。真正便宜的替代是退回单体 0.2.9（Kotlin 2.0.21 + AGP 8.5.2 + compileSdk 34 全部现成），但那等于作废 07 文档的 API 盘点、重写接入层。因此：先按 L1→L2 把工具链搬到 `Kotlin 2.4.21 / AGP 9.3.1 / Gradle 9.5.0 / compileSdk 37 / BOM 2026.09.00`，每步以 `bash build.sh :app:testDebugUnitTest`（1116 绿）验证，L3 再落 miuix；`miuix-blur` 永久排除（minSdk 33 vs 26），玻璃效果自写。

### 附：可复现命令

```sh
curl -s MC/miuix-ui/maven-metadata.xml ; curl -s MC/miuix-ui/0.9.4/miuix-ui-0.9.4.{pom,module}
curl -s MC/miuix-ui-android/0.9.4/miuix-ui-android-0.9.4.module        # size=1902787
unzip -p <aar> META-INF/com/android/build/gradle/aar-metadata.properties
unzip -p <aar> AndroidManifest.xml | strings | grep minSdkVersion
unzip -p <aar> classes.jar > c.jar && unzip -p c.jar 'top/yukonga/miuix/kmp/basic/ScaffoldKt.class' > S.class && javap -v S.class | grep -E 'major version|mv=|xi='
curl -s MC/org/jetbrains/compose/foundation/foundation/1.12.0/foundation-1.12.0.module
curl -s GM/androidx/compose/foundation/foundation-android/1.12.0/foundation-android-1.12.0.aar
```

**证据索引**：fork 行号取自当前 checkout（`git log -1` = `bd0848a Cut 0.6.6`）；上游行号取自 `/tmp/miuix`（`23b469e`）。所有 Maven 断言均带具体坐标与文件类型，未使用记忆值。
