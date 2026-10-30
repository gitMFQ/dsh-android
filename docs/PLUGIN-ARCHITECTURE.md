# 可行性研究：安卓 App 也做「一切皆插件」

> 你的想法（我的理解）：**App 自身有一套插件接口**（面板/卡片/设置页/命令等扩展点），插件装上后能往 App 里注册 UI；其中一些插件需要 **DSH 后端也装对应插件**，此时**App 里就会出现那个后端插件对应的界面**；而这需要有人为 App 再写一个「前端插件」来对接。
> 本文判断这个架构是否可行、代价多大、以及和「fork bqv + 换皮」的现有路线怎么共存。

## 1. 结论先行

| 判断 | 结论 |
|---|---|
| **技术上可行吗** | **可行**，而且 DSH 自己的架构就是最好的证明（60+ 个 `dsh-client-*` 包，靠 Cordis 的 **`ctx.slots`** 插槽系统组合出整个 Web GUI；`ctx.uiRenderer` 只负责 `mount()`） |
| **该不该现在做** | **不该**。它会让当前 7–9 周的交付变成 4–6 个月，且**与「fork 现有 App」路线直接冲突** |
| **有没有低成本替代** | **有**：先做「**能力探测 + 动态面板**」（数据驱动，无需加载代码），把 80% 的价值拿到手 |
| **额外发现** | 后端已有 **`pluginManager/*`（16 个 Remote）**：App **可以直接在手机上浏览/安装/启停后端插件**，再用 `pluginInventory/list` 确认装好了。这条链**今天就能做，不需要后端改动** |
| **什么时候做完整插件系统** | App 1.0 稳定、协议层封装成熟之后；且要等一个前提：**插件分发与安全模型想清楚** |

## 2. 为什么技术上可行（而且 DSH 就是这么干的）

DSH 的 Web 客户端本身就是「一切皆插件」的范本：

| 机制 | DSH 实现 | 证据 |
|---|---|---|
| 插件包 | 每个 `dsh-client-ui-*` 是一个 npm 包，`package.json` 的 `dsh` 字段声明入口 | `packages/boot/plugin-manager/src/index.ts:101`（`INSPECT_FIELDS` 含 `dsh`） |
| 前端代码分发 | Host 提供 `/plugins/<id>/client.js` 路由 | `packages/client/modules/src/index.ts:353,1147,1265` |
| 资源 | `/plugins/<pkg>/assets/<path>` | `packages/client/modules/src/assets.ts:79-85` |
| 组合 | Cordis profile + 有序 patch 文件；插件模块可导出函数/类/`{apply}` 三选一 | `packages/boot/plugin-manager/src/index.ts:975`；`vendor/cordis/src/registry.ts:120-233` |
| 发现 | `pluginInventory/list` Remote 只读快照 | `dsh-host-plugin-inventory/README.zh.md` |

所以「App 也做成插件化」在架构上是**有先例、有模式可抄**的。

## 3. 但安卓端有三道 DSH 没有的坎

### 坎 1：Android 不能 `import()` 任意代码（安全与签名）

DSH 是 Node 进程，插件的 `client.js` 直接在主进程里跑，权限等同于宿主。**安卓不允许这个模型**：

| 方案 | 可行动性 | 代价 |
|---|---|---|
| A. App 内 WebView/JS 引擎跑插件 UI | 可行（React/Vue 写面板，宿主注入 bridge） | 只支持**声明式 UI**，复杂交互要桥；性能与流式渲染是弱项 |
| B. 每个插件是独立 APK，跨进程 IPC | 可行（AIDL/Messenger 共享 UI 需 `SurfaceView`） | 体验碎片化，安装门槛高，跨进程 UI 极难做 |
| C. 动态加载 DEX（DexClassLoader） | 技术上可行 | **Play 政策禁止**；自签侧载可绕但用户风险高，且 AGP 9 / Android 14+ 对动态代码限制更严 |
| D. **只做「数据驱动」，不加载代码** | **最可行** | 插件只能声明 UI 结构（JSON schema），由宿主渲染 |

**结论：安卓上的「插件」几乎必然是 D**——除非接受 WebView 方案（A）。

### 坎 2：插件需要「后端也装」，配对关系必须显式建模

你的描述里关键的一点：*「有些插件可能需要 dsh 后端也安装对应插件」*。这要求 App 有能力判断：

```
App 插件清单（本地 manifest）
    └─ requires: hostPlugin = "dsh-foo@^1.2"
          ↓ 启动时
Host 能力探测（pluginInventory/list + 端点探活）
    ├─ 宿主有 → 显示该插件面板
    └─ 宿主没有 → 面板置灰 + 「需要服务端安装 dsh-foo」引导
```

**这个东西 DSH 已经给了一半**：`pluginInventory/list` 能列出宿主装了哪些插件、是否启用、是否存活。所以「App 知道后端装了什么」是**现成能力**，不需要新协议。

但**反向**（App 告诉后端「我装了你的前端插件」）目前**没有通道**——DSH 没有「客户端插件注册」的 Remote。这是必须新增的后端能力，意味着**你要能改 DSH 服务端**（或写一个 DSH 插件来做这件事）。

### 坎 3：安全边界

| 风险 | 说明 |
|---|---|
| 插件能调哪些 Remote？ | 若不给白名单，一个恶意插件能读全部会话、发任意 prompt |
| 插件能拿凭据吗？ | Cookie/Token 必须对插件不可见，只能通过受控代理调用 |
| 插件能画审批弹窗吗？ | **绝对不能**——冒领审批等于绕过人机确认，这是最危险的面 |

> 简言之：**审批、提问、权限这类 HITL 面板必须写死在宿主里，不允许插件接管。** 这应是硬性架构约束。

## 4. 更划算的替代方案：能力探测 + 动态面板（强烈建议先做这个）

你要的价值其实是：**「后端装了什么，App 里就能看到对应界面」**。这件事**不需要插件系统**也能做到一大半：

### 4.1 机制

```kotlin
// 宿主能力探测（启动时一次 + 每次连上时刷新）
data class HostCapabilities(
    val endpoints: Set<String>,      // 从 $events ready 后探测 P0 端点
    val plugins: List<PluginEntry>,  // pluginInventory/list 的结果
    val features: Set<String>,       // 由插件名映射出的功能标签
)

// App 内置的「功能模块」按 capability 门控
@Composable
fun SettingsScreen(caps: HostCapabilities) {
    if ("schedule" in caps.features) ScheduleSection()
    if ("agent-team" in caps.features) AgentTeamSection()
    if ("forge" in caps.features) ForgeSection()
}
```

**效果**：后端装了 `dsh-schedule`，App 里「定时任务」区块自动出现；没装则隐藏。**零插件加载、零安全风险、纯 Kotlin**。

### 4.2 用数据描述面板（进一步）

对真正「未知」的后端插件，可以让**后端插件自己声明 UI 描述**：

```json

{ "panel": { "id": "dsh-foo",
              "title": "Foo 管理",
              "sections": [
                { "type": "list", "source": "foo/list" },
                { "type": "switch", "label": "启用", "source": "foo/toggle" },
                { "type": "text", "source": "foo/description" } ] } }

```

App 只需实现 8–10 种「区块类型」（列表/开关/输入/只读文本/按钮/图表…），就能渲染任意后端插件声明的面板。**这就是你想要的「装了后端插件，App 里就有界面」，只是把「写前端插件」换成「写 JSON 描述」。**

| | 代码插件（你的原方案） | 数据描述（推荐） |
|---|---|---|
| 业主需写 | DSH 插件 + Android 插件 | DSH 插件 + 一段 JSON |
| 安全性 | 差（加载代码） | 好（纯数据） |
| 表现力 | 无上限 | 覆盖 80% 表单/列表类面板 |
| App 改造量 | 极大（插件运行时 + 沙箱 + 商店级审核） | 中（区块渲染器） |

## 5. 要不要现在做？——不建议，给三个理由

| 理由 | 说明 |
|---|---|
| **1. 与 fork 路线冲突** | 你已定 fork `bqv/dsh-android`（MPL-2.0，5.8 万行、1 116 测试）。要插件化就得先把它**重构成模块化架构**，等于先拆掉再重建 |
| **2. 插件系统需要「先有宿主」** | 插件接口的形态取决于宿主稳定后的扩展点。现在协议层、UI 层都还在动，此刻定插件 API 会**返工两次** |
| **3. 需求尚未证明** | 目前明确的痛点是「手机上能聊天/审批/看文件」，这些**不需要插件**。等你自己用了一个月，会知道到底缺什么 |

## 6. 如果仍要做：推荐路径（分三期）

| 期 | 目标 | 工作量 | 产出 |
|---|---|---|---|
| **P1（1.0 之后）** | 建立扩展点抽象：面板注册表、卡片渲染器注册表、设置项注册表（**纯 Kotlin 接口，编译期**） | 1–2 周 | 内部模块化，为未来插件打基础 |
| **P2** | **能力探测 + 动态面板**（§4）：后端声明 JSON，App 渲染 | 2–3 周 | 80% 的「装了后端插件就有界面」 |
| **P3** | 真插件运行时：WebView 沙箱承载复杂 UI，或 DexClassLoader（仅侧载通道） | 4–6 周 | 完整的第三方生态 |

## 7. 需要 DSH 服务端配合的地方（必须提前知道）

| 需求 | 现状 | 要做的事 |
|---|---|---|
| App 知道后端装了哪些插件 | ✅ **有**（`pluginInventory/list`，只读快照：标识、启用状态、存活阶段） | 无需改动 |
| App 管理后端插件（装/卸/启停） | ✅ **有**（`pluginManager/listPlugins`、`installBundle`、`setPluginEnabled`、`removeBundle`、`updateBundle`、`inspect`、`waitForInstall` 等 16 个 Remote） | 无需改动 —— **这比你想的更进一步：App 可以直接在手机上给后端装插件** |
| 后端声明某面板的 UI 描述 | ❌ 无 | 写一个 DSH 插件（很小的 Remote），或在现有插件里加一个只读 Remote |
| App 向宿主注册「我装了前端插件」 | ❌ 无 | 需要新增 Remote；**必须做权限控制**，否则任何客户端都能伪造 |
| 插件分发与版本协商 | ❌ 无 | 需要约定 manifest 格式（`requires` / `provides` / `version`） |

## 8. 一句话建议

> **先收敛到「能力探测 + 动态面板」，把插件系统当作 1.0 之后的正交演进。** 这样你既拿到了「装什么就能看到什么」的体验，又不用在协议和 UI 都没定型时背上插件运行时的包袱。

> 如果坚持要做完整插件系统，**必须先决定后端是否愿意配合**——没有后端配合，App 端插件只能做「纯客户端扩展」（主题、快捷方式、布局），拿不到「后端插件 → App 界面」这条价值。
