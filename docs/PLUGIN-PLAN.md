# 后端可改：插件化路线 v2（从「不做」改为「分三期做」）

> 前提变更：你确认**可以改 DSH 服务端**。这条前提把 `PLUGIN-ARCHITECTURE.md` 里「拿不到核心价值」的判断推翻了——现在「后端插件 → App 界面」这条路是通的。
> 本文替代该文档 §6 的路线，给出可执行的架构、协议与分期。

## 1. 架构定型：三段式，App 永不经手后端的代码

```
┌─────────────────┐   ① 声明 UI    ┌──────────────────┐   ② 数据     ┌─────────────┐
│ DSH 插件（改）   │ ─────────────▶ │ App 区块渲染器    │ ◀────────── │ DSH Remote  │
│ panel.json      │  panel descriptor│ (固定 12 种区块)  │  unary/WS   │  既有 API   │
└─────────────────┘                 └──────────────────┘             └─────────────┘
         │                                    │
         └──────── ③ 能力探测 ────────────────┘
              pluginInventory/list
```

| 环节 | 谁提供 | 内容 |
|---|---|---|
| ① 面板声明 | **DSH 插件新增一个 Remote** | 返回 JSON：面板标题 + 区块数组 + 每个区块绑定的端点名 |
| ② 数据 | **既有 Remote**（无需新写） | App 用 `ns/method` 直接调用，结果喂给对应区块 |
| ③ 能力发现 | **既有 `pluginInventory/list`** | 宿主装了哪些插件 → 决定 App 显示哪些面板 |

**核心约束**：App **只渲染数据，绝不执行后端下发的代码**。面板声明是 JSON，不是 JS。

## 2. 为什么选「区块声明」而不是「App 端插件」

| 维度 | App 端插件（每插件写一份 Android 代码） | 区块声明（本文方案） |
|---|---|---|
| 新增一个插件的工作量 | 改 DSH + **写 Android 插件 + 发版 App** | 改 DSH + **写一段 JSON** |
| 用户要做什么 | 等 App 更新才能用新插件 | **装完后端插件立即能在 App 里用** |
| 安全 | 要沙箱、要审核 | 纯数据，无执行面 |
| 表现力 | 无上限 | 覆盖表单/列表/状态类 ≈80% |
| 对 App 的攻击面 | 大 | 小（受白名单约束） |

> **这是关键取舍**：牺牲 20% 的极致表现力，换来「后端插件装完即用」的产品体验。剩下 20%（拖拽、Canvas 图表）走**逃生舱**：`webview` 区块，URL 必须经用户手动确认。

## 3. 后端要新增什么（很小）

### 3.1 一个 Remote：`appPanel`

```kotlin
// 伪代码：插件作者实现这一个方法
@Remote('get')
async get(app: AppId): Promise<PanelDescriptor> {
  return {
    schemaVersion: 1,
    title: '定时任务',
    blocks: [
      { type: 'list', source: 'schedule/list', item: { title: '${name}', subtitle: '${cron}' } },
      { type: 'switch', label: '启用', source: 'schedule/update', action: 'toggle' },
      { type: 'button', label: '新建', action: 'schedule/create' },
    ],
  }
}
```

### 3.2 可选：一个 manifest 字段

插件 `package.json` 里加：

```json
{ "dsh": { "appPanel": { "provider": "@scope/dsh-plugin-foo", "method": "get" } } }
```

App 不必读这个字段（可以直接探测端点），但它让「哪些插件支持 App 面板」变得可枚举。

### 3.3 不需要改的

- 认证、RPC、事件流：**完全复用**
- `pluginInventory/list`：已有
- `pluginManager/*`（16 个）：已有 —— App 甚至能在手机上给后端装插件

## 4. App 端要新增什么

| # | 模块 | 工作量 |
|---|---|---|
| 0 | **宿主补端点**：`panels/list` + `panels/describe`（≈200 行，照 `dsh-schedule` 的 typert 模板） | 3 天 |
| 1 | `PanelDescriptor` 数据模型 + 解析（容忍未知字段/未知区块） | 2 天 |
| 2 | `BlockRenderer`：12 种区块的 Compose 实现 | 1.5 周 |
| 3 | `PanelHost`：路由 + 白名单 + 错误隔离 | 3 天 |
| 4 | 能力探测与面板发现 | 3 天 |
| 5 | 测试：fixture JSON 快照测试 | 3 天 |
| | **合计** | **约 4.5 周** |

> ⚠️ **样例面板必须用现成端点**：`schedule` 域只有 `catalog/delete/deleteInactive/history/list/update`，**没有** `setEnabled`、**没有** `create`；`update` 是带 `expected` 的整记录比较更新，表达不了启停。示例面板的「启用开关」要么由新插件补端点，要么渲染为**禁用态 + 原因**，绝不假成功（fork 的 `AutomationSheet.kt:79-86` 已得出同一结论）。

## 5. 分期（与主路线并行，不阻塞 1.0）

| 期 | 时机 | 内容 | 产出 |
|---|---|---|---|
| **P1** | 1.0 之后 | 后端加 `appPanel` Remote + 写一个示例插件（如 schedule 面板）；App 实现 5 种基础区块（text/list/switch/button/status） | 端到端跑通一个真实面板 |
| **P2** | +2 周 | 补齐到 12 种区块（table/progress/logs/file-list/key-value/markdown/form/chart-lite） | 覆盖绝大多数插件 |
| **P3** | 按需 | 逃生舱 `webview` 区块 + 用户确认；插件市场里标注「支持 App」 | 生态闭环 |

## 6. 安全模型（硬约束）

| 约束 | 理由 |
|---|---|
| **白名单端点**：面板只能调用 manifest 里声明的 `ns/method` | 防止恶意插件读全部会话 |
| **禁止渲染审批/提问 UI**：`approval` 与 `user-questions` 端点对面板**永久封禁** | 冒领审批 = 绕过人机确认 |
| **区块数量与深度上限**（≤64 区块、≤3 层嵌套） | 防 DoS |
| **不加载外部 URL**，除非用户点击确认 | 防钓鱼 |
| **面板必须走独立路由**，视觉上与聊天区隔开 | 防伪装官方界面 |

## 7. 与现有路线的关系

| 现有阶段 | 冲突吗 |
|---|---|
| 阶段 0.5 工具链升级 | 不冲突 |
| 阶段 1–3 外壳与聊天 | 不冲突（区块渲染器是新增模块） |
| 阶段 3.5 多语言 | **有交集**：区块里的 label 需要 i18n → 面板声明的文案由**后端按 App 语言下发**，App 不再翻译 |
| 阶段 4 发布 | P1 可作为 1.1 版本特性 |

> 一句话：**插件化不改变 1.0 的范围，而是 1.1 的自然增量。**
