# 工作流总表（fork → KernelSU 风格上线）

> 目的：把「fork + 换皮」拆成可并行、可验收的任务板。每一行都能独立交付，行内顺序即依赖顺序。
> 参考：[PLAN.md](PLAN.md) §5 里程碑 · [DESIGN-TOKENS.md](DESIGN-TOKENS.md) · [FORK-SURGERY.md](FORK-SURGERY.md) · [BUILD-AND-CI.md](BUILD-AND-CI.md)

## 阶段 0 · 接管（3–5 天）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 0.1 | 建私有仓库，`origin`=自己、`upstream`=bqv | 仓库 | `git remote -v` 正确 | — |
| 0.2 | 改包名/应用名/图标/About | 提交 | 全仓无旧包名；APK 安装显示新名 | 0.1 |
| 0.3 | `NOTICE.md` + 修改文件头标注 | 文档 | MPL 义务齐备 | 0.2 |
| 0.4 | CI：`build.yml` 出 APK + 新增 `verify.yml` 跑 1 116 个单测 | 工作流 | 两个 workflow 绿 | 0.2 |
| 0.5 | 首次真机冒烟（连本实例，聊天+审批+文件） | 记录 | 全链路通过 | 0.4 |

## 阶段 0.5 · 工具链升级（3–5 天，**必须先做**）

> miuix 0.9.2 要求 Kotlin 2.4.x + androidx compose 1.11.x + compileSdk 35 + AGP 8.6+。fork 现在是 Kotlin 1.9.24 / AGP 8.5.2 / compileSdk 34 / BOM 2024.09。**Kotlin 2.x 是躲不掉的**（1.9 读不了 miuix 的 metadata）。详见 [research/08-miuix-compat.md](research/08-miuix-compat.md) §7。

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 0.5.1 | Kotlin 1.9.24 → 2.4.x，`composeOptions` 换成 `org.jetbrains.kotlin.plugin.compose`，`kotlinOptions` → `compilerOptions` | 构建文件 | **1 116 个 JVM 测试全绿**（此时**还没引入 miuix**，可独立回滚） | 0.4 |
| 0.5.2 | AGP 8.5.2 → 8.6+，compileSdk 34 → 35，Compose 升到 androidx 1.11.x | 构建文件 | `assembleDebug` 通过 + 测试全绿 | 0.5.1 |
| 0.5.3 | 引入 miuix 0.9.2 最小集（`miuix-ui` / `miuix-preference` / `miuix-icons` / `miuix-squircle`，**不含 blur**） | 依赖声明 | 编译通过；三页能跑 | 0.5.2 |

> 三个 **不要**：不引 `miuix-blur`（模块 minSdk 33，我们 26）；不引 `miuix-nav`（0.9.2 无此模块，我们也不需要）；不为 miuix 抬 minSdk。

## 阶段 1 · 结构（1 周）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 1.1 | 抽 `:core:protocol`（`net/`） | 模块 | 独立编译 + net 单测绿 | 0.4 |
| 1.2 | 抽 `:core:model`（`model/`） | 模块 | 47 个模型单测绿 | 0.4 |
| 1.3 | 新建 `:design`：`KsuTokens` + `KsuTheme`（7 档外观） | 模块 | 设置页可切并持久化 | 1.1 |
| 1.4 | Roborazzi 截图测试接入（外壳 + 设置页先覆盖） | 测试 | CI 跑出基线图 | 1.3 |

## 阶段 2 · 外壳换装（1.5 周）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 2.1 | 浮动底栏：`MiuixIcons` + 自绘玻璃层（`Modifier.blur` API31+/描边高光；API<31 纯色）**或** 直接用 `FloatingNavigationBar` | 组件 | 三档设备均正常 | 0.5.3 |
| 2.2 | 4 目的地 pager（Chat/Trajectory/Files/Settings），宽屏切 `NavigationRail` | 导航 | 切页弹簧流畅，旋转后状态保持 | 2.1 |
| 2.3 | TopBar 折叠 + 状态胶囊（连接/运行/待审批） | 组件 | 滚动折叠无跳变 | 2.1 |
| 2.4 | 抽屉换装（搜索栏/分段列表/分组排序/归档） | 界面 | 分组排序可用，截图无回归 | 2.2 |
| 2.5 | 设置页 + 关于页换装 | 界面 | 视觉对齐参考；项全部可达 | 1.3 |
| 2.6 | Sheet/Dialog 统一：`OverlayBottomSheet` / `OverlayDialog`（**必须挂在 Miuix `Scaffold.popupHost` 下**）；危险操作红卡 + 二次确认 | 组件 | 审批走 Sheet + 二次确认 | 2.5 |

## 阶段 3 · 聊天精修（1 周）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 3.1 | 消息气泡/状态点/工具卡折叠行/统计胶囊换皮 | 界面 | 语义色不变；`DshTheme.colors` 调用点不变 | 2.6 |
| 3.2 | 动效：切页弹簧、弹窗 scale、流式段落淡入、危险操作触觉 | 打磨 | 60fps；无卡顿 | 3.1 |
| 3.3 | 深色/AMOLED/大字体/无障碍回归 | 测试 | 无 P0 视觉缺陷 | 3.2 |

## 阶段 4 · 差异化与发布（1–2 周）

| # | 任务 | 产出 | 验收 | 依赖 |
|---|---|---|---|---|
| 4.1 | 本地通知（任务完成/待审批，点击直达会话/审批） | 功能 | 后台 30 分钟内可送达 | 3.3 |
| 4.2 | 多实例切换（数据模型预留 `instanceId`） | 功能 | 两台实例可切换不串数据 | 3.3 |
| 4.3 | 平板/折叠屏自适应（双栏或 Rail 阈值） | 功能 | 展开/折叠无布局错乱 | 3.3 |
| 4.4 | 弱网与断线重连压测 + 导出诊断包 | 质量 | 断网 5 分钟后恢复不丢消息 | 3.3 |
| 4.5 | 签名 keystore 固化 + Release + Obtainium + 变更日志 | 发布 | 可侧载安装并订阅更新 | 4.4 |

## 关键路径与并行建议

```
0.1 → 0.2 → 0.4 → 1.1/1.2（并行） → 1.3 → 2.1 → 2.2 → 2.4 → 2.6 → 3.1 → 3.2 → 4.1 → 4.5
```

- 阶段 2 内部：2.3 / 2.5 可与 2.2 并行（不同文件）。
- 阶段 4 的 4.2 / 4.3 可与 4.1 并行。
- **单人全职约 5–6 周**；两人（一人外壳/一人聊天精修）可压缩到 3.5–4 周。

## 每周节奏建议

| 周 | 主题 | 周末应能演示 |
|---|---|---|
| W1 | 接管 + 结构 | 新名字的 APK 能跑，设置页能切外观 |
| W2–W3 | 外壳换装 | 底栏 + 四页 + 抽屉 + 设置全部新皮 |
| W4 | 聊天精修 + 动效 | 完整聊天链路视觉达标 |
| W5 | 通知/多实例/平板 + 发布 | 可对外发内测包 |

## 风险闸门（任一触发就停下来复盘）

| 闸门 | 触发条件 | 处理 |
|---|---|---|
| **Kotlin 2.x 迁移卡住** | 0.5.1 测试红且 2 天内无法收敛 | 先只升 Kotlin 不加 miuix，逐测试修复；若仍不行 → 走"方案 B"：不引 miuix，纯自绘外观（工期 +1 周，但零工具链风险） |
| 换皮回归失控 | 截图测试差异文件 > 20 个/次 | 缩小改动粒度，先补测试 |
| ChatScreen 改不动 | 单文件改动 > 300 行仍无法通过编译/测试 | 拆文件（把 Scaffold 段抽成 `ChatScaffold.kt`） |
| Miuix 版本冲突 | Gradle 解析冲突或 Compose 版本不兼容 | **锁 0.9.2**，不要追 0.9.4（会逼出 AGP 9 + Gradle 9 + compileSdk 37 连锁升级） |
| Sauce 依赖断供 | miuix 停更或 Maven 撤包 | 源码已 Apache-2.0，可 vendored（`/tmp/miuix`）自建 |
| 上游停更/大改 | upstream 3 个月无提交或有破坏性改动 | 冻结 fork，自持；不再 cherry-pick |
