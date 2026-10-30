# 面板块协议（Panel Block Schema）v1

> **一句话**：DSH 插件用一个**只读 Remote** 交出一份 JSON 面板描述，Android 端用**固定 14 种块**通用渲染；App 不加载任何代码，面板只能通过**声明的 op → 端点**读写，且**永远不能**接管审批/提问座位。
> **证据约定**：`$P/` = `/usr/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/`；`F/` = fork 基线 `bqv/dsh-android@v0.6.6` 本机副本 `/tmp/dsh-android-prior`（与 `docs/research/06-bqv-ui.md` 同一份代码，行号为该副本真实行号）；`R/` = 本仓 `docs/research/`。**「设计值」= 本协议自定、宿主未强制的阈值**，实现时不得当成宿主事实引用。
> 本文件把 `docs/PLUGIN-ARCHITECTURE.md:99-112`（面板 JSON 雏形）与 `:68-70`（审批必须写死）从「方向」落成「可实现的 schema」。

## 0. 结论与非目标

| 判断 | 结论 | 证据 |
|---|---|---|
| 值不值得做 | 值：省掉「每个 DSH 插件写一个安卓前端插件」；代价 = 一个渲染器 + 一份文本协议 | `docs/PLUGIN-ARCHITECTURE.md:114-119`（数据描述 vs 代码插件的对比表） |
| 覆盖目标 | 表单/列表/状态/动作/日志/图表/文件/选择器/开关/详情/富文本/表格/按钮/进度 ≈ 80% 真实面板 | 同上 `:112` |
| 硬红线 | 审批、提问、HITL 面板**必须**留在宿主，插件面板不得复刻其外观与座位 | 同上 `:68-70`；`F/app/src/main/java/uk/xa0/dsh/ui/components/ChatRows.kt:1412`（`ApprovalCard` 唯一实现）、`F/.../ui/ChatScreen.kt:1418-1430`（审批座位固定在输入框正上方） |
| 非目标 | 不做表达式/脚本/条件逻辑、不做客户端计算、不做拖拽画布、不做插件代码分发（DEX/WebView） | `docs/PLUGIN-ARCHITECTURE.md:38-43`（安卓不能 import 任意代码的三条坎） |

## 1. 传输契约：面板 Remote 面

宿主侧新增一个很小的 typert Remote 面（与 `schedule` 同形，`$P/dsh-schedule/lib/typert.remote-client.d.ts:11-16` 是模板）：

| 端点 | args（**键集必须精确**） | 返回 | 用途 |
|---|---|---|---|
| `panels/list` | `{}` | `{ panels: PanelSummary[], schemaVersion: number }` | 目录：id/title/owner/schemaVersion/requires；抽屉用它决定显不显示入口 |
| `panels/describe` | `{ panelId: string }` | `PanelDoc`（§2） | 取完整文档；一块面板一次 |
| （写入）面板声明的各 op | 见 §4 | 各端点原样 `value` | **不经新包转发**：直接打被声明端点 |

| 约束 | 事实 | 证据 |
|---|---|---|
| 参数严格 | 多键/缺键都判 `gateway/arguments-invalid`；只接受描述符的 `parameters[].wire` | `$P/dsh-api-gateway/lib/types/index.js:1248-1265`（`assertExactArguments`）、`:570` |
| 调用形态 | `POST /api/<ns>/<method>`，信封 `{type,rpcId,method,payload:{args}}`；客户端已有现成实现 | `R/01-protocol.md:94`；`F/.../net/DshClient.kt:965-1021`（`rpcRaw` 原样返回 `value`，数组也能过） |
| 错误 | 业务码原样过线，其余折叠 `gateway/internal`；码表含 `method-unavailable`/`service-unavailable` | `$P/dsh-api-gateway/lib/types/index.js:1166-1179`；`R/01-protocol.md:107` |
| 无 schema 自省 | 网关只按调用 `resolveDescriptor`；39 个 namespace 的端点清点里**没有** describe/反射式端点 → 协议版本必须自带 | `$P/dsh-api-gateway/lib/types/index.js:569`；`R/02-api-surface.md:120-131` |
| 事件 | 刷新通知走 `$events` 的 emit（如 `schedule/changed`），且 **emit 不重放**，只能「收到就重读」 | `$P/dsh-api-remotes/lib/index.js:139-141`；`R/01-protocol.md:188` |
| 事件前提 | `interactions=none` 的连接收不到 `$events`（`gateway/interactions-forbidden`） | `R/01-protocol.md:49` |

## 2. 文档根结构与硬限额

```json
{ "schemaVersion": 1, "id": "schedule.panel", "title": "定时任务", "subtitle": "dsh-schedule",
  "owner": { "plugin": "dsh-schedule-panels", "version": "0.1.0" },
  "params": [ { "name": "sessionId", "type": "string", "default": "$app.sessionId" } ],
  "endpoints": [ { "op": "tasks", "endpoint": "schedule/catalog", "args": {} } ],
  "refreshers": [ { "on": "schedule/changed", "op": "tasks" } ],
  "blocks": [ /* §3 */ ] }
```

| 字段 | 语义 | 限额（设计值） |
|---|---|---|
| `schemaVersion` | 整数，本协议 = 1；不匹配走 §7 | 仅接受整数 |
| `id` / `title` | 稳定 id；根标题是**声明**，App 外壳永远额外画自己的插件徽标条 | title ≤ 120 字符 |
| `params` | 面板输入，值只能来自 App 固定枚举：`$app.sessionId`、`$app.home`、`$app.model` | ≤ 8 个；无默认值的必填参数缺失 → 面板不可用态 |
| `endpoints` | **op 白名单**：op 名 → 端点 + 固定 args（可含 `$param`/`$item`，§4） | ≤ 40 个 op；端点 段文法 `[A-Za-z0-9_$.-]+`（`R/01-protocol.md:96`） |
| `refreshers` | 事件名 → 要失效的 op 列表 | ≤ 16 条；事件名只允许 emit 类 |
| `blocks` | 文档顺序即渲染顺序 | ≤ 200 块；整包 ≤ 256 KB；嵌套 ≤ 3 层（root=0） |
| 字符串 | 单字段上限 | 文本块 ≤ 16 KB；列表标题 ≤ 200 字符 |
| 行数据 | 单次 source 返回的行 | 渲染 ≤ 200 行（超出显示「仅显示前 200 行」）；图表点 ≤ 500；日志保留 ≤ 2000 行 |

> 为什么整包要小：下行帧 ≥1024 字符才压缩（`R/01-protocol.md:20`），上行 inbox 仅 262144 B/流（`R/01-protocol.md:19`）；文档越大越接近「把状态塞进协议」的反面。

## 3. 块类型集（14 种）

`source` 一律是 op 名（§4）；`path` 只允许 `a.b[0].c` 形态的点路径，无通配、无函数。

| # | `type` | JSON 关键字段 | Compose 映射 | 硬约束 |
|---|---|---|---|---|
| 1 | `header` | `title,subtitle,badge,level(1-3)` | `SettingsContent.kt:766` `SectionHeading` | 不可隐藏 App 外壳顶栏；level 最大 3 |
| 2 | `text` | `md`(子集 markdown) | `F/.../ui/markdown/MarkdownView.kt:66-68` 原生渲染 | 无 HTML、无图片外链、无脚本；超 16 KB 截断 |
| 3 | `keyValue` | `rows:[{label,value,path?,format?}]` | `SettingsContent.kt:818` `SettingsRow` + `:841` `SettingsGroup` | `format` 仅 `text|number|datetime|duration|bytes|id`，无 printf |
| 4 | `list` | `source,item:[blocks],showCount,empty` | `LazyColumn` + `AutomationSheet.kt:434` `TaskRow` 布局基因 | item 内**禁止再嵌 `list`**；行 id 取 `row.id`，缺失则用序号 |
| 5 | `table` | `source,columns:[{key,title,align,format}]` | 横向滚动 `Row` + 表头 | 列 ≤ 8；单元格仅标量；无合并单元格 |
| 6 | `form` | `fields:[{name,kind,label,required,options?}],submit:{op,confirm?}` | `DshInput`/`SettingsSelector`（`SettingsContent.kt:926`） | `kind` ∈ `text|multiline|number|switch|choice|date|time|timezone`；值只经 `$input` 传给 op |
| 7 | `toggle` | `label,valuePath?,op,optimistic` | `Switch`（`F/.../ui/SessionsDrawer.kt:1003` 现成用法） | 只有 `optimistic:true` 才先翻转；失败必回滚并内联报错 |
| 8 | `actions` | `items:[{label,op,confirm?,danger?,disabledWhen?}]` | 按钮行/`TextButton` | `op` 必须已声明；`danger` 走宿主红卡二次确认；顺序即视觉顺序 |
| 9 | `status` | `valuePath,labelMap:{值→文案},toneMap:{值→tone},detail?` | `AutomationSheet.kt:524` `StatusChip` + `components/StateDot.kt:46` | `tone` ∈ `ok|warn|error|idle|running`；两个 map 各 ≤ 8 条，是**静态查表**不是表达式，未命中退 `idle`；语义色取 `DshTheme`（`ui/theme/DshTheme.kt:61-76`） |
| 10 | `progress` | `label,source,path,mode(determinate|indeterminate)` | `LinearProgressIndicator` | 值必须 [0,1]；越界按钳制处理，不报错 |
| 11 | `log` | `source(op 只可指向 stream 端点),follow,mono,maxLines` | 等宽 `LazyColumn`，贴尾 | 离开组合即 cancel 流（`F/.../net/RemoteMux.kt:317-324`）；只保留尾部 |
| 12 | `chart` | `kind(bar|line|spark),source,x,y,unit` | `Canvas` 自绘 | 仅这三种；点 ≤ 500；无坐标轴公式、无双轴、无 tooltip 表达式 |
| 13 | `files` | `source,pathField,nameField,sizeField,actions(open|download)` | `components/FilesPanel.kt:167` / `:782` `FileRow` | 只读列表；`open` 走宿主下载器，不接受任意 URL |
| 14 | `picker` | `label,source(选项数组),valueField,op` | `SettingsSelector` + 底部选择 Sheet | 选项 ≤ 200；选中即刻写，不做本地延迟匹配 |

**通用字段**（每块可选）：`id`（稳定，供状态/埋点）、`visibleIf`——**故意不存在**（没有表达式就没有条件显示，见 §10）、`states:{loading,empty,error}`（自定义文案，见 §5）。

## 4. 数据绑定：声明式，没有表达式

| 机制 | 形态 | 规则 |
|---|---|---|
| 读 | `{"source":"tasks"}` | `source` 只能是 `endpoints[].op` 的名字；**服务端数据一律来自端点**，不得内联 |
| 静态字面量 | `{"literal":[...]}` | **唯一例外**：静态选项/说明（如时区列表），≤ 64 行且仅标量，且该块**不得同时有写入 op**（否则「用户看到的 vs 改的」会分叉） |
| 参数 | `{"$param":"sessionId"}` | 在 args 的叶子位置被替换为 App 注入的值；只能引用 `params` 里声明过的名字 |
| 行内值 | `{"$item":"id"}` | 仅在 `list.item` 子树内合法，取当前行的同名字段；越界即整块 `schema_error` |
| 表单值 | `{"$input":"title"}` | 仅在 `form.submit` 的 args 内合法 |
| 刷新 | `refresh:{mode:"onOpen|manual|poll|event",intervalMs?,on?}` | `poll` 钳制到 [5s,300s]；`event` 只订阅 `refreshers` 里声明过的 emit；**所有** source 在 `$events` 换代后重读（逻辑流不跨代，`R/01-protocol.md:155,190`） |
| 写入 | `op` + 可选 `confirm` | 一元调用；`ok` 后失效该块 `refresh.alsoInvalidates` 里列出的 op；`error` 保留原值并就地展示 |
| 无乐观写 | `danger:true` 或 `optimistic` 缺省 | 先等宿主回执再翻转，避免「假成功」 |

> 关键：**没有** `if`/`map`/`length`/`format("%s")` 这类东西。要算数就让宿主算好再返回字段——协议只搬运，不计算。

## 5. 状态模型

| 问题 | 决定 |
|---|---|
| 文档状态在哪 | 进程内 `PanelsRepository`（内存 + 按 `panelId` 缓存），跟随连接代；断线不重放，重连后重取目录与当前文档（`R/01-protocol.md:188`） |
| 块状态在哪 | `PanelViewModel` 持 `Map<blockId, BlockState>`；`BlockState` = `Idle|Loading|Data(JsonElement)|Empty|Error(code,message,retryable)|Unavailable` |
| 草稿在哪 | 页面态 `rememberSaveable`（与 fork 现有 sheet 开关同一机制，`F/.../ChatScreen.kt:466-479`）；切面板/旋转保留，退出面板即弃 |
| 空态 | `Data` 且数组长度为 0 → `Empty`；**不用空数组冒充加载中** |
| 失败隔离 | 每块起独立 `CoroutineScope`（父级 `supervisorScope`）；一块失败只渲染占位错误卡 + 「重试」，其余块照常 |
| 整页失败 | 仅两种：文档解析失败，或 `blocks` 全为 `Error/Unavailable` → 整页错误页（含「重新获取面板」） |
| 错误码 | `method-unavailable`/`service-unavailable` → `Unavailable`（永久，不重试）；`401/403` → 交宿主重登录流程（`F/.../net/DshClient.kt:983-985`）；其余 `retryable` |
| 一致性 | 写入成功后**以宿主回读为准**（失效再读），不把本地草稿当权威 |

## 6. 安全模型

| 面 | 规则 | 依据 |
|---|---|---|
| 代码 | 拒绝任何可执行载荷：文档只允许 §3 的块与字面量；未知字段丢弃，未知类型占位 | `docs/PLUGIN-ARCHITECTURE.md:38-43` |
| 端点白名单 | 面板只能调 `endpoints[].op` 声明的端点；App 侧另维护**固定黑名单**，任何声明都无效 | 本轮新增约束；黑名单见下 |
| 固定黑名单（App 硬编码，永不开放） | `$events/result`（审批/提问回执）、`session/cancel`、`session/create`、`credentials/*`、`settings/mutate`、`pluginManager/*` | 审批为 waterfall（`$P/dsh-api-remotes/lib/index.js:27,144`）；这些端点连 Web 端也是特权面 |
| 能力告知 | 首次打开面板，显示「本面板将调用」清单（op + 端点），需一次确认 | `docs/PLUGIN-ARCHITECTURE.md:64-68` 的风险表 |
| 尺寸 | §2 限额；解析前后各校验一次（防压缩炸弹式超大数组） | 设计值 |
| 深度 | 块嵌套 ≤ 3；`list.item` 内禁 `list` | 设计值（防递归放大） |
| 外链 | 文档**不能**带 `url`/`href`；唯一入口是 §10 的 `webview` 块，需逐宿主确认；一律 `Intent.ACTION_VIEW`/CustomTabs，禁 `file://`/`javascript:`/`intent:` | `F/.../ui/AboutScreen.kt:586` 是 fork 唯一的外链先例（`ACTION_VIEW`） |
| 冒充审批 | ①面板只在**自己的全屏路由**渲染（不与其他 sheet 同屏）；②外壳永远画插件徽标条 + 「插件面板」前缀；③渲染器**不得**引用 `ApprovalCard`/`AskQuestionRow`/`QuestionCard`；④面板内的确认框由**宿主**画，且强制带「来自面板 `<title>`」一行，文档不可关掉 | `F/.../ChatRows.kt:1412`、`F/.../components/AskQuestionRow.kt`、`components/QuestionCard.kt`；座位证据 `ChatScreen.kt:1418-1430` |
| 日志/隐私 | 面板数据不进 logcat 正文；错误只记 code + panelId | 与 `F/.../DshViewModel.kt:2535` 的「每 episode 一行」策略一致 |

## 7. 版本与向前兼容

| 规则 | 内容 |
|---|---|
| 广告能力 | `panels/list` 的 args 带 `{"supports":[1],"client":"dsh-android/0.1"}`（新增端点，无历史包袱）；宿主按**最大公共版本**返回，返回版本 > 支持版本时 App 显示「需升级 App」而不解析 |
| 文档自述 | 每份 `PanelDoc.schemaVersion` 必须 ≤ 请求里的最大值，否则整份拒收（`UnsupportedSchema` 占位页） |
| 未知块 | 未知 `type` → `Unknown(type, rawJson)` → 占位卡「此版本不支持 `<type>`，App 升级后可用」，**绝不抛异常**（解析器用 `JsonElement` 兜底，见 §9） |
| 未知字段 | 忽略（kotlinx.serialization `ignoreUnknownKeys = true`），不因新增可选字段而整页失败 |
| 未知枚举 | 一律退化为最保守值：`refresh.mode` 未知 → `manual`；`tone` 未知 → `idle` |
| 未知 op 端点 | 运行到才发现，映射为 `Unavailable`（宿主没装对应服务时正是这个码） |

## 8. 具体示例：schedule 面板

```json
{
  "schemaVersion": 1, "id": "schedule.panel", "title": "定时任务",
  "subtitle": "跨会话的提醒，按会话投递",
  "owner": { "plugin": "dsh-schedule-panels", "version": "0.1.0" },
  "params": [],
  "endpoints": [
    { "op": "tasks",   "endpoint": "schedule/catalog", "args": {} },
    { "op": "history", "endpoint": "schedule/history", "args": {
        "request": { "sessionId": { "$item": "sessionId" }, "id": { "$item": "id" }, "limit": 20 } } },
    { "op": "delete",  "endpoint": "schedule/delete",  "args": {
        "sessionId": { "$item": "sessionId" }, "id": { "$item": "id" } } },
    { "op": "setEnabled", "endpoint": "schedule/setEnabled", "args": {
        "sessionId": { "$item": "sessionId" }, "id": { "$item": "id" }, "enabled": { "$input": "on" } } }
  ],
  "refreshers": [ { "on": "schedule/changed", "op": "tasks" } ],
  "blocks": [
    { "type": "header", "id": "h", "title": "全部任务", "level": 2,
      "subtitle": "开启中的任务会向原会话投递" },
    { "type": "list", "id": "tasks", "source": "tasks", "showCount": true,
      "refresh": { "mode": "event", "on": "schedule/changed" },
      "states": { "empty": "还没有定时任务" },
      "item": [
        { "type": "keyValue", "id": "kv", "rows": [
            { "label": "名称",   "path": "title" },
            { "label": "下次运行", "path": "scheduledAt", "format": "datetime" },
            { "label": "规则",   "path": "kind", "format": "text" } ] },
        { "type": "status", "id": "st", "valuePath": "active",
          "labelMap": { "true": "运行中", "false": "已结束" },
          "toneMap":  { "true": "running", "false": "idle" } },
        { "type": "toggle", "id": "tg", "label": "启用", "valuePath": "active",
          "op": "setEnabled", "optimistic": false },
        { "type": "actions", "id": "act", "items": [
            { "label": "运行记录", "op": "history" },
            { "label": "删除", "op": "delete", "danger": true,
              "confirm": { "title": "删除任务？", "body": "会连同已保存的运行记录一起移除。",
                           "confirmLabel": "删除" } } ] }
      ] }
  ]
}
```

| 诚实说明 | 内容 |
|---|---|
| 真实可用度 | `schedule/catalog` 无参、返回被 `sessionId/status` 增强的记录（`$P/dsh-schedule/lib/types/types.d.ts:184-191`）；`schedule/delete` 存在（`:285-292`）；`schedule/history` 存在（`:390-393`）——列表/删除/历史**今天就能渲染** |
| ⚠️ `setEnabled` 不存在 | 现存端点只有 `catalog/delete/deleteInactive/history/list/update`（`$P/dsh-schedule/lib/typert.remote-client.d.ts:11-16`）；`schedule/update` 是「带着 `expected` 整条记录做比较更新」（`types.d.ts:368-374`），**无法**表达启用/停用；fork 自己也在注释里写了「不启用/不创建」（`F/.../ui/AutomationSheet.kt:79-86`）。所以要么面板插件补一个 `schedule/setEnabled`，要么该 `toggle` 按 §7 渲染为**永久禁用 + 原因**，绝不假成功 |
| 同理 | `schedule/create` 不存在，面板不做「新建」；新建交回会话里的 `schedule_create` 工具（同上注释） |

## 9. Compose 实现骨架

```kotlin
// 1) 解析：未知类型永远落到 Unknown，不抛
@Serializable data class PanelDoc(val schemaVersion: Int, val id: String, val title: String,
    val endpoints: List<Endpoint>, val blocks: List<BlockDoc>)

@Serializable(with = BlockSerializer::class)
sealed interface BlockDoc { val id: String
    @Serializable @SerialName("list")  data class ListBlock(override val id: String,
        val source: String, val item: List<BlockDoc>, val showCount: Boolean = false) : BlockDoc
    // …其余 13 种同形
    data class Unknown(override val id: String, val rawType: String, val raw: JsonElement) : BlockDoc
}

object BlockSerializer : KSerializer<BlockDoc> { /* 先读 "type"，命中枚举就委派，
    否则 JsonElement 原样收进 Unknown；ignoreUnknownKeys = true */ }

// 2) 注册表：类型 → 渲染器；加块不改 when
class BlockRegistry(private val renderers: Map<KClass<out BlockDoc>, BlockRenderer>) {
    @Composable fun Render(block: BlockDoc, ctx: BlockContext) =
        renderers[block::class]?.Render(block, ctx)
            ?: UnknownBlockCard((block as BlockDoc.Unknown).rawType)   // 兜底，不是崩溃
}

fun interface BlockRenderer { @Composable fun Render(block: BlockDoc, ctx: BlockContext) }

data class BlockContext(
    val state: BlockState,              // §5，已按 path/source 解析好
    val call: (op: String, args: JsonObject) -> Unit,
    val confirm: (ConfirmSpec, () -> Unit) -> Unit,   // 宿主画确认框，带面板署名
    val colors: DshColors,              // ui/theme/DshTheme.kt:61-76
)

// 3) 页面：只此一处依赖宿主
@Composable fun PanelScreen(vm: PanelViewModel, onBack: () -> Unit) =
    PanelScaffold(chrome = PanelChrome(vm.owner, vm.title), onBack = onBack) {  // 外壳不可被文档去掉
        LazyColumn { items(vm.doc.blocks, key = { it.id }) { registry.Render(it, vm.context(it)) } }
    }
```

| 测试方式（不依赖宿主） | 做法 |
|---|---|
| 解析 | JVM 单测读 `app/src/test/resources/panels/*.json`（fork 已有 `app/src/test/resources/`，现只放 `multipart-real.bin`）→ 断言块树；含「未知 type / 超限 / 坏路径」三组负例 |
| 渲染 | `PanelScreen` 只吃 `PanelViewModel` 接口；用 fake `call/confirm` 注入固定 `BlockState`，无需网络、无需 DSH |
| 截图 | Roborazzi（`docs/PLAN.md:168` 已排期）对每类块的 fixture 出图；换皮时它就是安全网 |
| 回归 | fork 现有 127 个测试文件 / 1116 个 `@Test`（`docs/PLAN.md:162,166`）不得因新模块变红 |

## 10. 诚实边界：这套协议表达不了什么

| 表达不了 | 为什么 | 替代 |
|---|---|---|
| 任意自定义图表（热力图/桑基/交互 tooltip） | `chart` 只有 bar/line/spark | 宿主端把数据算成这三种之一；或走 §10 逃生舱 |
| 拖拽、滑动删除、手势、画布 | 协议无手势/坐标原语 | 用 `actions` 显式按钮；真需要再议 |
| 条件显示 / 客户端计算 / 本地过滤 | 无表达式（这是安全边界，不是遗漏） | 宿主给 `source` 加参数、返回已筛好的结果 |
| 实时流式富文本（逐 token） | 只有 `log` 支持追加 | 文本块整段刷新；聊天流式仍由聊天页负责 |
| 上传/拍照/扫码 | 无 input 文件原语 | 交回聊天页的附件入口 |
| 多语言 | 面板文案由宿主给，App 不翻译 | 宿主按 `Accept-Language` 自己本地化 |
| 二级/三级导航 | 只有「面板 → 行内子列表」一层 | 拆成多个面板，用 `panels/list` 的目录互跳 |

**逃生舱 `webview` 块**：`{"type":"webview","url":"https://host/panel","height":"fill"}` —— 逐 URL 需用户确认（显示 host + 证书摘要），确认前不加载；无 JS bridge、不共享 Cookie、不在审批座位附近；不支持时该块渲染为「本版本已禁用外部视图」。**代价要讲清**：fork 目前**全无 WebView**（`F/.../ui/AboutScreen.kt:250`「there is no WebView」、`ui/markdown/MarkdownView.kt:66-68`、`ui/TerminalScreen.kt:85`），引入它是一次真实的架构变更，应放到 v1 之后单独评估（`docs/PLUGIN-ARCHITECTURE.md:135` 的 P3）。

## 11. 落地顺序与遗留风险

| 步骤 | 内容 | 验收 |
|---|---|---|
| S1 | `PanelDoc` + 解析器 + 14 块注册表 + fixtures（纯客户端，可用静态 JSON 演示） | 无宿主也能出 14 种块的截图 |
| S2 | `panels/list`/`panels/describe` 宿主插件（≈200 行，照 `$P/dsh-schedule` 的 typert 模板） | 目录能出入口，文档能取回 |
| S3 | 读绑定（`source`/`$param`/`$item`）+ 事件刷新 + 错误隔离 | schedule 面板列表真实可用 |
| S4 | 写绑定（`actions`/`form`/`toggle`）+ 宿主确认框 + 黑名单 | 删除有二次确认，失败可见 |
| S5 | 能力确认页、版本协商、`webview`（可延后） | 未知块不崩、旧 App 显式拒绝新文档 |

| 风险 | 对策 |
|---|---|
| 面板与 App 原生页面重复（schedule 面板 vs `AutomationSheet`，`F/.../ui/AutomationSheet.kt:152,434`） | 先只做**原生没有的**面板；schedule 面板作为样例而非替代 |
| 宿主字符串不本地化 | 文档明确「App 只翻自己的外壳」；面板作者自报语言 |
| 声明式表达力不足引发「加个表达式吧」的滑坡 | §10 已把边界写死；新增能力必须同时给解析与安全测试 |
| 冒领审批 | §6 四条 + 注册表层面禁止引用审批组件；代码评审硬门槛 |

> 与既有文档的关系：本文细化了 `docs/PLUGIN-ARCHITECTURE.md:99-112`；不改动 `docs/DESIGN.md` 的视觉语言，只要求面板壳复用 `DshTheme` 语义色与 `KsuTokens` 形状（`docs/DESIGN-TOKENS.md:21`）。
