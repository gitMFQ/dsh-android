# 工具链锁定矩阵（二次校验后的最终选型）

> 本文是 [09-toolchain-latest](09-toolchain-latest.md) 的**执行版**：把「最新」收敛成一组**互相兼容、且被官方矩阵背书**的版本号。每个数字都有实测或官方文档出处。校验日期 2026-10-30。

## 1. 核心结论：AGP 用 **9.3.1**，Gradle 用 **9.5.0**，都不是最新

我原本打算用 AGP 9.5.0 + Gradle 9.8.2（当时的最新稳定）。**Kotlin 官方兼容矩阵否定了这两个选择**：

| KGP 版本 | Gradle 支持范围 | **AGP 支持范围** |
|---|---|---|
| **2.4.20–2.4.21** | 7.6.3 – **9.7.0** | **8.5.2 – 9.3.1** |
| 2.4.0–2.4.10 | 7.6.3 – 9.5.0 | 8.5.2 – 9.1.0 |
| 2.3.20–2.3.21 | 7.6.3 – 9.3.0 | 8.2.2 – 9.0.0 |

> 出处：<https://kotlinlang.org/docs/gradle-configure-project.html> compatibility table（2026-10-30 实取）。

**两处超范围**：

| 项 | 最新 | Kotlin 2.4.21 上限 | 结论 |
|---|---|---|---|
| AGP | 9.5.0 | **9.3.1** | 9.5.0 超范围 → 用 **9.3.1** |
| Gradle | 9.8.2 | **9.7.0** | 9.8.2 超范围 → 用 **9.5.0** |

miuix 自己用 AGP 9.5.0 构建（说明「能编译」），但**消费者项目**跟着超范围，等于把兼容性赌在 JetBrains 未验证的组合上。长期可维护的基线应落在矩阵内。

## 2. 最终版本表

| 组件 | 锁定 | 依据 |
|---|---|---|
| Gradle | **9.5.0** | AGP 9.3.1 反编译 `VersionCheckPlugin` 得 `GRADLE_MIN = 9.5.0`（实测）；且在 Kotlin 上限 9.7.0 内 |
| **AGP** | **9.3.1** | Kotlin 2.4.21 官方 AGP 上限；9.3.x 最新补丁 |
| Kotlin | **2.4.21** | 最新稳定（2.5.0 仅 Beta） |
| Compose BOM | **2026.09.00** | 内含 compose ui **1.12.1** / material3 **1.4.0**（实读 POM） |
| compileSdk / buildTools | **37 / 37.0.0** | miuix 0.9.4 AAR `minCompileSdk=37`（0.9.0–0.9.4 五版实测一致） |
| targetSdk | 37（或 36） | 侧载无政策约束 |
| minSdk | **26 不动** | 保持 Android 8+ 覆盖 |
| JDK | **17 够用** | kotlinc 2.3.0 可读 major-65；D8 8.5.27 可 dex |
| Robolectric | **4.17** | 反编译 `DefaultSdkProvider` 确认支持 SDK 37 |
| miuix | **0.9.4** | 降版本无收益（全部要求 compileSdk 37） |

**互相咬合的校验链**：

```
Kotlin 2.4.21 ──官方矩阵──▶ AGP ≤ 9.3.1 ──实测──▶ Gradle ≥ 9.5.0
      │                                              │
      └─官方矩阵─▶ Gradle ≤ 9.7.0 ──────────────────┘
                        │
      compileSdk 37 ────┴──▶ miuix 0.9.4
             ▲
      Compose BOM 2026.09.00（CMP 1.12 传递）
```

## 3. 三级阶梯（用锁定版本）

| 级 | 内容 | 接 miuix | 闸门 |
|---|---|---|---|
| **L0** | 现状：AGP 8.5.2 / Gradle 8.7 / Kotlin 1.9.24 / BOM 2024.09 / compileSdk 34 | — | 1 116 测试全绿 |
| **L1** | **只升 Kotlin 2.4.21 + `plugin.compose`**，AGP/compileSdk 全不动 | 无 | `assembleDebug` + 1 116 测试 |
| **L2** | AGP 8.13.2 + Gradle 8.13 + compileSdk 36 + BOM 2026.05.00 | 无 | 同上 + 配置缓存 |
| **L3** | AGP **9.3.1** + Gradle **9.5.0** + compileSdk **37** + BOM **2026.09.00** | 无 | 同上四项 |
| **L4** | + miuix **0.9.4** 最小集（ui/preference/icons/squircle） | 0.9.4 | 同上 + `dependencyInsight` 无旧 foundation 残留 |

**L1 的关键洞察**：Kotlin 2.4.21 的 AGP 下限是 **8.5.2**——**正是我们现在的版本**。所以 L1 可以完全不动 AGP、不动 compileSdk，只做 Kotlin 迁移 + `composeOptions`→`plugin.compose` + `kotlinOptions`→`compilerOptions`。这样 L1 只有一个变量，失败必然出在 Kotlin，最容易定位。

> 这是对上一轮阶梯的进一步细化：把「Kotlin 迁移」和「AGP 大版本跳」彻底分开，中间还多插了 L2（AGP 8.13 / SDK 36）作为缓冲。五级阶梯，每级一个变量。

## 4. 待验证（只能 CI）

| 项 | 状态 |
|---|---|---|
| AGP 9.3.1 × SDK 37 实构建 | 未验证（本机无 SDK） |
| miuix 0.9.4 在 AGP 9.3.1 下 manifest 合并 | 未验证 |
| Robolectric 4.17 × Compose 1.12.1 × SDK 37 | 未验证 |
| 1 116 测试在 K2 下的实际失败面 | 未验证（预判编译期先炸在 composeOptions/kotlinOptions） |
| miuix 0.2.9 老坐标 | **已证伪**：`top.yukonga.miuix.kmp:miuix-ui:0.2.9` 返回 404，该版本不存在 |
