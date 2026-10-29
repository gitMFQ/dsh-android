# DSH Web GUI 功能盘点 → 原生 Android 映射

> 事实基线：发行包 `0.2.2-alpha.2`，66 个 `dsh-client-*` 包。下文路径前缀 **P** = `/usr/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/`；行号为本轮实测（`grep -n`）。
> 组合真源：`P/dsh-web-app/cordis.patch.yml` 中共 **60** 行 `@deepseek-ai/dsh-client-*`（`grep -oE "name: '@deepseek-ai/(dsh-client[^']*)'"`）。前端是 **React 18.2 + Cordis 4**（`P/dsh-client-ui-renderer/package.json` devDeps: `react ^18.2.0`、`@deepseek-ai/cordis ~4.0.6-alpha.1`）。
> 注意：`dsh-client-ui-slots` / `ui-primitives` / `ui-dockkit` / `dsh-client-store` / `dsh-client-web` **不在发行包内**（`ls` 无此目录，仅以依赖与 declaration-merge 出现），故 slot 契约只能从各包的 `.d.ts` 合并声明反推。

## 1. 信息架构

### 1.1 主区域（三栏 + 三个框架座）

| 区域 | 拥有者 | 内容 | 依据 |
|---|---|---|---|
| 左栏 `sidebar`（root, single） | `ui-sidebar` → 座位 `sidebar.workspaces` / `sidebar.settings` / `sidebar.panellist` | 品牌行、New Session、折叠成 56px 图标栏、Workspace/Session 树、底部固定 Settings | `P/dsh-client-ui-layout/lib/types/client/index.d.ts`；`P/dsh-client-ui-sidebar/README.zh.md` |
| 中栏 `main`（root, **keyed**） | `ui-conversation` 占 `conversation` key；其余 key 为全局面板 | 对话（header + transcript + composer）或 Plugins 等全局面板 | 同上；`P/dsh-client-ui-layout/README.zh.md` |
| 右栏 `rightbar`（root, single） | `ui-sidebar-right` → 每 Session 一个停靠面（dockkit） | 文件预览 / 浏览器 / 终端 / 改动对比 / 子代理对话 / 计划全文，多 tab | `P/dsh-client-ui-sidebar-right/README.zh.md` |
| `shell.bottom` | 无人占用（默认组合不用） | 全宽通栏，高度挤压三栏 | `.../client/index.d.ts` |
| `shell.overlay` | `ui-chat`（配额提示）、各种 toast | 覆盖全框的点击穿透浮层，列表可叠加 | 同上；`P/dsh-client-ui-chat/README.zh.md` |
| `shell.leading` | `ui-sidebar`（重开 + New Session） | 仅 macOS 整列隐藏时的窗口 chrome 座 | 同上 |

### 1.2 视图与导航机制

```ts
// P/dsh-client-ui-layout/lib/types/client/service.d.ts
interface ILayout {
  readonly panelInfo: HostObservable<PanelInfo>   // { activePanelId: MainPanelId | null }
  selectPanel(panelId: MainPanelId | null): void  // null = 回对话；未注册 key 抛错并保留原选择
  beginNavigation(): AbortSignal                   // 异步导航，后被替代；需在提交 UI 前检查
  toggleSidebar(): void                            // 关闭 ⟷ 默认宽度
  openRightbar(track: boolean, fullscreen: boolean): void
  closeRightbar(): void
}
```

- 侧栏面板行（`sidebar.panellist`）与 `main` keyed slot **同 id 绑定**：注册 `id` → 选中时 `selectPanel(id)`；`conversation` 是保留 key（`P/dsh-client-ui-sidebar/README.zh.md`）。
- 当前 **Session 选择不属于 layout**，属于 Session Controller；选中 Session 时 `rightbar` 才渲染 `rightbar.session`（`P/dsh-client-ui-layout/README.zh.md`）。
- View 环（对话内标签页）：`conversation.view` 由 `ui-conversation` 拥有；选择规则是「持久化偏好 > 已注册 `chat` > 不渲染」，**绝不回退到第一个注册 View**（`P/dsh-client-ui-conversation/README.zh.md`）。可用 View 少于 2 个时隐藏标签栏。
- 设置是**模态面板**（800×800，挂 `#root` 之外），不是中栏页面；Plugins 是**全局面板**（`P/dsh-client-ui-settings-general/README.zh.md`）。
- 断点：侧栏 <1024px 自动折叠为 56px 轨；设置面板 <768px 铺满视口并把导航变成横向条带；右栏 <768px 打开即全屏；正文宽度 ≤900px 隐藏轮次轨道（`.../columns.d.ts`、`settings-general`、`sidebar-right`、`ui-chat` 各 README）。

| 常量 | 值 | 出处 |
|---|---|---|
| `SIDEBAR_MIN/MAX/DEFAULT/COLLAPSED` | 264 / 420 / 280 / 56 px | `P/dsh-client-ui-layout/lib/types/client/columns.d.ts` |
| `SIDEBAR_AUTO_COLLAPSE` | 1024 px | 同上 |
| `CENTER_MIN` | 400 px（先缩右栏至 300，再让其关闭，最后才压中栏） | 同上 |
| `RIGHTBAR_MIN` / 默认占比 / 上限占比 | 300 px / 45% / 70% | 同上 |

### 1.3 Android 形态结论

| Web 区域 | Android 形态 | 理由 |
|---|---|---|
| 左栏会话/工作区树 | `NavigationDrawer`（模态抽屉）+ 底部 Mini 导航 | 单手可达；树深 2 层（Workspace → Session） |
| 中栏对话 | 唯一主 Route（`MainActivity` 内 Compose） | |
| 右栏多 tab | **BottomSheet（半屏/全屏）+ 文件预览 Route** | 停靠/分栏/浮窗/拖动分辨率不可用 |
| Settings 模态 | 独立 Route（`SettingsScreen`） | 全屏页面比模态更省 |
| Plugins 全局面板 | MVP 不做（见 §6.2） | |
| `shell.overlay` toast | Compose `Snackbar` + 自有 toast host | |

## 2. 聊天渲染

### 2.1 流式增量（关键：不要把整段 markdown 每帧重排）

```ts
// P/dsh-api-session-controller/lib/types/types.d.ts#L474-L536
interface SessionAssistantStreamBaseline { revision: number; activeAttempt?: {
  attemptId; startedAfterSeq; turn; step; nextIndex: number; stream: JsonValue[] } }
type SessionAssistantStreamFrame =
  | { type: "start";  attemptId; revision; startedAfterSeq; turn; step }
  | { type: "chunk";  attemptId; revision; index; time; chunk: JsonValue }
  | { type: "end";    attemptId; revision; index;
      outcome: { kind: "committed"; eventType: "assistant/message"|"assistant/attempt"; seq }
             | { kind: "abandoned" } }
type SessionFollowFrame = { type:"snapshot"; ...; assistantStream?: baseline }
                       | SessionEventEntry | { type:"assistant-stream"; frame }
```
- 帧是**稠密、带 revision 与 index** 的；累积器折叠为一帧共享不可变 baseline，重连从 baseline 恢复，settlement 后用持久 `assistant/message` 事件替换（`P/dsh-api-session-controller/lib/types/assistant-stream.d.ts`）。
- Web 侧「实时工具 delta 与推理共用按帧合批发布节奏，持久调用/结果立即发布」（`P/dsh-client-ui-chat/README.zh.md`）。
- **Android 建议**：OkHttp/WS 收到 chunk 后 `MutableStateFlow` 合批（每 16–32ms 一帧），只在「块边界变化」时重建 markdown AST；流式中把尾部未闭合块当纯文本追加到同一个 `AnnotatedString` builder。难度 **L**。

### 2.2 逐项渲染难度

| 元素 | Web 事实（引用） | Compose 难度 | 方案 |
|---|---|---|---|
| 消息气泡（user/assistant/steering） | 独立来源包装层保留 key、pending/echo 标记、时间、复制操作（`P/dsh-client-ui-chat/README.zh.md`）；本地回显在权威记录到达时原子消失 | S | 原生 `Column` + `Bubble`；回显用同 key 局部替换 |
| Markdown / 表格 / 链接 | 共享 `MarkdownText` 原语 + `MarkdownDelegateProvider` 提供文件与 http(s) 导航、`fileImages`（`ui-chat`、`ui-tool` README） | M | **第三方**（Markwon / compose-richtext）+ 自研文件链接回调 |
| 代码块 / 语法高亮 | Shiki token 色（`P/dsh-client-ui-theme/README.zh.md` 中 `shiki.css`）；代码块 sticky 头栏 | M | 第三方 `Shiki` 无 Android 版 → 用 `tree-sitter` 或 highlight.js WebView 片段；**建议 MVP 用等宽 + 单色** |
| 数学公式 | 只检测 KaTeX DOM（`.katex-mathml` / `.katex-html` 用于选区过滤，`P/dsh-client-ui-chat/lib/client.js#L638-L639`），渲染在 primitives 内 | L | 第三方 `MathView`/KaTeX-in-WebView 片段；MVP 降级为纯文本 |
| CoT 折叠 | 4 档「工作步骤展示」：简洁/标准/详细/仅最终回答/完全展开；`conversation.chat.reasoning.body` single slot，官方以 priority 100 注册 | M | 原生 `Collapsible`，状态按组本地保存（`useDisclosure` 语义） |
| 工具调用卡片 | 调用**树**：根 + PTC `subCalls`；每原子调用按 wire 名分发到 keyed slot `tool.call.toolview`，未注册名走通用卡片（`P/dsh-client-ui-tool/README.zh.md`） | L | 原生 `sealed class ToolCard` + 注册表 `Map<String, Renderer>`；见 §2.3 |
| 工具卡片内容 | 卡片模型族：terminal / read / diff / search / web / image / question / todo / PTC dispatch / generic fallback | L（每类 0.5–2 天） | 先做 8 类高频卡，其余落 generic 折叠行 |
| Trajectory 视图 | 按轮次的事件账本 + 时间概览（滚轮缩放、右键拖动平移、500ms 悬停详情）+ 虚拟行（`P/dsh-client-ui-trajectory/README.zh.md`） | L | **不要 1:1**；做「按轮次分组的可折叠记录列表 + 选中行检查器」 |
| Subagent / workflow 轨迹 | `workflow-run` 独立 Chat 节点：运行→阶段→成员三层 disclosure；运行中/失败默认展开，完成默认折叠（`P/dsh-client-ui-workflow-run/README.zh.md`）；子代理目录为页头面包屑 + 惰性树（`P/dsh-client-ui-subagent/README.zh.md`） | M | 原生三层可折叠行 + 子会话 Route（`parentSessionId/childSessionId/mode`） |
| 附件与图片 | 草稿栏 64px 高横排、文件卡 240px、消息图单图长边 240px（比例钳制 [0.25,4]）、多图固定 64px 方块、灯箱（`P/dsh-client-ui-attachment/README.zh.md`） | M | 原生 `LazyRow` + 自研灯箱（`Zoomable` + pager） |
| 错误 / 重试 / 中断 | 每终止轮次一行红点行；欠费失败走中立文案 `message.failure.quota`；`QUOTA`/`ACCOUNT_QUOTA` 进 `shell.overlay`；输出上限用琥珀点；重启恢复以 `interrupted` 收尾（`P/dsh-client-ui-chat/README.zh.md`） | S | 原生状态行 + 全局 Snackbar |
| 轮次 token 用量 / 页脚 | 仅当窗口含 `turn/start` 且每次 attempt 用量精确才显示；`ui-chat.performanceUsage` = detailed/compact | S | 原生折叠行 |
| 消息反馈 Like/Dislike | 定稿消息动作条 order 10，两档都打开弹窗（7 分类 + 备注）；再点已记录评分直接撤回（`P/dsh-client-ui-message-feedback/README.zh.md`） | S | 原生 `IconButton` + `AlertDialog` |
| 引用 / quote | 选区手势经 `ctx.uiConversation.interactions` 注册；多容器各自一个悬浮卡组，quote 弹层互斥（`P/dsh-client-ui-conversation/README.zh.md`，#sent-user-text） | M | Android 无「右键手势」；用 `long-click` → 系统文本工具条 + 自定义 quote Action |
| 交付物 `present` / 改动文件 | turn tail 卡片：单文件标题或双列网格（>4 折叠）；每行开 `changes-review` tab（diff 两栏/单栏、wrap 切换、5000 行截断）（`P/dsh-client-ui-deliverables/README.zh.md`） | L | MVP 只做「文件 chip 列表 + 点击打开预览」；diff 用 `SideBySide` 精简版 |
| 操作卡片（propose_action） | 已完成回答下方一张卡；同轮多卡只取调用 id 最小者；无正文时只给「暂不需要」（`P/dsh-client-ui-action-cards/README.zh.md`） | S | 原生卡片 |
| 秘密掩码 | 已发送用户文本中识别凭据并掩码（`P/dsh-client-ui-secret-mask/README.zh.md`） | S | 复用同一正则表 |

### 2.3 工具视图键（实测 48 个来自 `ui-tool`，另有 6 个由业务包注册）

```text
// P/dsh-client-ui-tool/lib/client.js: key: "<wire name>" 于 tool.call.toolview
ask_user_question bash cordis_inspect_list cordis_inspect_query cordis_inspect_self
create_goal edit get_goal glob grep interrupt_agent job_kill job_list job_output
list_agents list_subagent_models lsp ralph read read_image schedule_create
schedule_delete schedule_list schedule_update send_message session_event_read
session_event_search session_event_trace session_search session_trace spawn_teammate
subagent team_task_create team_task_get team_task_list team_task_update terminal_close
terminal_list terminal_open terminal_read terminal_signal todo_write update_goal
wait_agent web_fetch web_search workflow write
// 业务包额外注册：skill (ui-skill) | cordis_define/run/stop/undefine (ui-cordis) | propose_action
```

- 派生规则：「Auto 拒绝优先于按工具名选择的专门视图」；`read/write/edit` 在 `file_path` 闭合且解码无误后显示可打开路径与 `NKB`（UTF-16 长度/1024 向上取整，**不是文件字节**）（`P/dsh-client-ui-tool/README.zh.md`）。
- 交互：每调用独立展开态，外层轮次收起时重置；Inspect 打开 Trajectory，`openFile` 打开右栏文本预览（`ui-tool` README）。
- **Android**：`ToolCall` 数据模型需保留 `parentCallId`、`phase(preparing/start/result)`、懒 `args` 读器；卡片渲染器做成 `sealed interface`，未知名回落 `GenericRow`。难度 **L**（工时集中处）。

## 3. 交互状态

| 状态面 | Web 行为（引用） | Android 建议 |
|---|---|---|
| 审批 takeover | `conversation.composer` chain，`select(owner)` 返回非 null 即接管；Enter 批准 / Esc 拒绝，`始终允许` 为高亮主按钮，普通允许只授单次（`P/dsh-client-ui-approval/README.zh.md`） | Composer 换成 `ApprovalSheet`（**必须**覆盖输入框焦点）；按钮顺序反向：DENY 次要 / ALLOW 主 / ALWAYS 三次确认 |
| ask_user_question | 附着式卡片临时占用编辑器位；单选/多选、自定义答案可并存、跳过得 `{selected:[]}`、翻页器、**倒计时卡片**（聚焦暂停、`慢慢回答` 转无限、到期 `ASK_TIMED_OUT` 仍可答）（`P/dsh-client-ui-user-questions/README.zh.md`） | 全屏 Dialog（页宽小）+ 计时条；答案走同一 `answer` Remote |
| plan mode | `/plan` 进入，composer 蓝色徽章 `conversation.input.plan` 退出；提交计划自动开右栏；审阅卡「要求修改」（`ASK_CANCELLED`）/「同意执行」（`P/dsh-client-ui-plan/README.zh.md` + `user-questions`） | 状态 chip + 计划全文 Route；审阅 = 底部双按钮 bar |
| 模型选择 | `/model` 弹窗与 composer 模型位共享**会话级目录**，按 provider 分组（DeepSeek 账号优先）；>4 个模型才显示搜索框；fuzzy-label 匹配；Effort 行来自目录推理元数据（`P/dsh-client-ui-model-selection/README.zh.md`） | 底部 Sheet，两段式：模型列表 → Effort |
| `/` 与 `@` 触发 | 光标处检测 → 分组候选菜单（`conversation.input.overlay`，session scope）；Tab 下钻(`drill`) / 选中(`pick`)，Esc/Shift+Tab 离开；空格与回车经 `matchSpace`/`matchEnter` 仲裁（`P/dsh-client-ui-input-trigger/README.zh.md`） | 无光标概念 → 用「输入框左侧 ⌘/＠ 按钮 + 前缀 token 检测」；Tab→「展开子项」图标 |
| 命令派发 | 三类：`popupSelect`（`/model`、`/permission`）、`action`（`/feedback`、`/plan`）、宿主 `execute`/`leadingInput`；composer `+` 菜单与 `/` 同源（`P/dsh-client-ui-commands/README.zh.md`） | 同一 Source 接口，Android 用 BottomSheet 选择器 |
| 权限预设 | 新建会话默认值在设置；当前会话 `/permission`；`full access` 与 `auto` 需二次风险确认（`P/dsh-client-ui-permission-presets/README.zh.md`） | 同样二次确认 Dialog |
| 排队 / 插话 | `QueueDock` 读 `inbox.next-turn`；繁忙时 Enter 行为由 `ui-conversation` 设置决定（Queue vs Steer）；`steer-all` 组合键 | 排队行列表 + 「插话」按钮；键盘语义改成长按发送 |
| 编辑器与草稿 | Lexical 0.49 编辑器，每 Session 一个；引用胶囊为原子 decorator 节点；（粗指针）草稿字号 ≥16px 防缩放（`P/dsh-client-ui-conversation/README.zh.md`） | `BasicTextField` + `AnnotatedString` chip 完全重做；16sp 下限同理 |
| 快捷键 | `Mod+B` 侧栏、`Mod+/` 速查、`Mod+,`/`Mod+Alt+,` 设置、`Esc Esc`（`stopSequenceMs` 默认 500ms）停止；固定序列不可改（`P/dsh-client-shortcuts/README.zh.md`） | 不作 1:1；映射为返回手势/长按/硬件键盘可选 |

## 4. 主题 / i18n / 设计 token

| 项 | 事实 | 依据 |
|---|---|---|
| 配色 | `light` / `dark` / `system`，`system` 走 `prefers-color-scheme` | `P/dsh-client-ui-theme/README.zh.md` |
| token | `--dsw-alias-*` 命名空间，实测 **414** 个唯一 `--dsw-*` 变量名编译进 `client.js`；8 张样式表（base / corner-shape / design-platform / focus / onboarding / scrollbar / gradient-shadow-text / shiki）在 bundle 里注入 | `P/dsh-client-ui-theme/lib/client.js`；README「样式表」节 |
| 字号 | 正文 10–22（默认 14）、代码 10–16（默认 11，行内 +1）、终端 10–20（默认 13）；`--dsh-content-font-size` 等投影到 body | 同上 |
| 字体 | 三个独立字体栈（正文/代码/终端），用户列表排在 `--dsh-font-family-*-default` 之前；列表规范化后写 `textFontFamily/codeFontFamily/terminalFontFamily` | 同上 |
| 首帧 | 宿主把 `ui-theme` 设置嵌进 index 响应 + head CSS + body 脚本，插件加载前即生效 | 同上，「插件前调色板」 |
| 图标替换 | `ThemeDefinition.icons` 把 `ICON_SYMBOLS` id 映射到完整 `<symbol>` SVG，禁止 `<style>/<script>` | 同上 |
| i18n | 8 个内置语言（en zh ja es fr de ru pt）；`ctx.locale.register(ns, {zh, en, ...})`，zh/en 必填；`t` 座位随语言热切换；`?locale.preference` 存 Host | `P/dsh-client-locale/README.zh.md` |

**可移植性判断**：token 是纯命名字符串 → 可用脚本从 `client.js` 抽出 `--dsw-alias-*` 表，生成 `ComposeTheme` 的 `ColorScheme` + 自定义 `LightDark` 扩展对象（S 工作量，但 414 个 token 需要语义筛选，先取 ~40 个核心：bg-layer-1/2, label-primary/secondary/tertiary, brand-primary, border-l1/l2, button-primary-fill, state-*）。**字体设置不可移植**（CSS 字体栈语义与 Android `Typeface` 不同）；**Shiki 语法色**需另找方案。i18n 字典可直接导出 JSON 复用。难度 **M**。

## 5. 通知需求

| 场景 | Web 现状（引用） | Android 应做 |
|---|---|---|
| 长任务完成 | **无系统通知**：`DocumentTitle` 只把当前 Session 标题投到浏览器标题（`P/dsh-client-ui-layout/lib/types/client/DocumentTitle.d.ts`）；运行态靠鲸鱼动画 + TextShimmer；侧栏行有「未读完成提醒」水位线标记（`P/dsh-client-ui-workspace/README.zh.md`） | FGS（`dataSync`/`specialUse`）+ 通知渠道「回合完成」；点击深链到会话与轮次 |
| 待审批 / 待回答 | 仅侧栏行黄色警告点（`ui-workspace`）+ composer takeover；`SessionState.pendingInput: "approval"|"question"|"other"`（`P/dsh-api-session-controller/lib/types/types.d.ts` L563-573） | 高优先级通知 + 通知内直接 ALLOW/DENY（`PendingIntent` action）或打开审批 Sheet |
| 错误 | 行内红点行 + 警告 toast；`QUOTA` 走 `shell.overlay` 全局条目并可被 `keepOpen()` 保持 | 通知渠道「错误」，失败重试按钮 |
| 后台任务 | 头部 `jobs` 列表 + 可展开输出面板 + 两击停止（3s 内确认）（`P/dsh-client-ui-jobs/README.zh.md`） | 进度通知（`setProgress`）+ 完成通知；停止走 `ctx.jobs.kill` 等价 RPC |
| 实测缺口 | 全树 grep `new Notification` / `requestPermission` **零命中**，也无声音提示 → Web 端确实只靠页面内 UI | 这是 **Android 相对 Web 的净增量价值**，应列入 P0 |

**实现要点**：`SessionState` 是「被动状态读 + `wait`」模型（`SessionWaitValue.reason: idle|input-required|timeout`，`types.d.ts` L565-...)；Android 后台不必常开 WS，可用 `wait` 轮询/长轮询来驱动通知，避免 FGS 长连接耗电。

## 6. 两张表

### 6.1 模块清单（Web ↔ Android）

| 模块 | Web 对应包 | Android 形态 | 优先级 | 成本 |
|---|---|---|---|---|
| 登录 / 连接 / 断线重连 | `dsh-client-connection` | 启动屏 + `SessionManager`；OkHttp Cookie 罐 | P0 | M |
| 会话列表 / 工作区树 | `ui-workspace`、`ui-sidebar` | `NavigationDrawer`：Workspace 折叠组 + Session 行（置顶/最近/搜索） | P0 | M |
| 对话与流式渲染 | `ui-conversation`、`ui-chat`、`session-controller` | 主 Route：`LazyColumn` + 合批 StateFlow | P0 | L |
| 输入框 / 草稿 | `ui-conversation`（Lexical 0.49） | 自研 `BasicTextField` + chip `AnnotatedString`；16sp 下限 | P0 | L |
| 附件 / 图片 / 灯箱 | `ui-attachment`、`dsh-client-file-upload`、`ui-file-drag` | `PickVisualMedia` + `LazyRow` 64dp 栏 + 自研 Zoomable 灯箱 | P0 | M |
| 工具调用卡片 | `ui-tool`（48 个 key） | `sealed interface ToolCard` + 渲染注册表 + GenericRow 兜底 | P0 | L |
| 审批 takeover | `ui-approval` | 底部 `ApprovalSheet`（覆盖输入；ALLOW/ALWAYS/DENY） | P0 | S |
| ask_user_question | `ui-user-questions` | 全屏 Dialog + 页宽 + 计时条 | P0 | M |
| 模型 / 推理强度选择 | `ui-model-selection` | BottomSheet 两段（模型 → Effort），>4 才带搜索 | P0 | S |
| 中断 / 停止 / 重试 | `ui-conversation`（`Esc Esc`、Stop 按钮） | 浮动 Stop 按钮 + 通知栏 Stop Action | P0 | S |
| 主题 / 字号 | `ui-theme`（414 个 `--dsw-*`） | 脚本导出 token → `DshTheme`（先取 ~40 个语义色） | P0 | M |
| i18n | `dsh-client-locale`（8 语言） | 导出 JSON → `strings.xml` 或运行时字典 | P0 | S |
| 通知（完成/审批/错误） | 无（Web 零命中） | FGS + 3 个渠道 + `PendingIntent` 直达动作 | P0 | M |
| 设置外壳 + 通用分区 | `ui-settings-general`、`ui-settings` | `SettingsScreen` Route（分区列表→页） | P0 | S |
| 权限预设 | `ui-permission-presets` | 设置行 + `/permission` 等价 Sheet + 风险二次确认 | P1 | S |
| plan 模式 | `ui-plan` | 状态 chip + 计划全文 Route + 双按钮审阅条 | P1 | M |
| 交付物 / 改动文件 | `ui-deliverables` | turn tail 文件 chip 列表 → 预览 Route（diff 精简版） | P1 | L |
| Trajectory 轨迹 | `ui-trajectory` | 「按轮次分组的折叠记录 + 检查器」，**不做**时间轴/缩放 | P1 | L |
| 子代理目录 / 续接 | `ui-subagent` | 页头面包屑 + 子会话 Route（不可续接则只读） | P1 | M |
| 后台任务 | `ui-jobs` | 头部图标 + Sheet 列表 + 可展开流式输出 + 停止 | P1 | M |
| Goal 条带 | `ui-goal` | composer 上方卡片（Todo→Goal→Queue 顺序） | P1 | S |
| `/` 命令 + `@` 引用 | `ui-commands`、`ui-input-trigger`、`ui-reference` | 输入框左侧按钮 → BottomSheet 选择器 | P1 | M |
| 消息反馈 Like/Dislike | `ui-message-feedback` | 消息长按菜单 → 反馈 Dialog | P1 | S |
| 右栏文件预览 / 浏览器 / 终端 | `ui-sidebar-right`、`-files`、`-documentpreview`、`-browser`、`-terminal` | 半屏 BottomSheet；文件预览 = Route；终端/browser **P2** | P1 | M |
| 定时任务 | `ui-schedule` | 列表页（只读 + 启停） | P2 | M |
| 快捷键速查 / 重绑 | `ui-shortcuts`、`dsh-client-shortcuts` | 设置页只读列表（硬件键盘用户） | P2 | S |
| 秘密掩码 | `ui-secret-mask` | 同一正则表复用 | P2 | S |
| 操作卡片 | `ui-action-cards` | turn tail 单卡 | P2 | S |
| 工作流运行节点 | `ui-workflow-run` | 三层折叠行 | P2 | M |
| Cordis 动态插件卡 | `ui-cordis` | 不做 | P2 | — |
| 插件管理 / 市场 / 推荐 | `ui-plugin-manager`、`-market`、`-recommendations` | 不做（Web 专属） | P2 | — |
| Agent preset 编辑 | `ui-agent-preset` | 只读展示 preset 名 | P2 | S |
| 入口定制 / 品牌位 | `ui-entry-customization`、`ui-brand-official` | 不做 | P2 | — |
| 目录选择器 | `ui-directory-picker-*` | 用系统 SAF 选目录 | P2 | S |
| 个性化页面 / 推荐卡 | `ui-creation-recommendations` | 不做 | P2 | — |
| Open In... | `ui-open-in-app` | Intent `ACTION_VIEW` 替代 | P2 | S |
| 产品埋点 | `dsh-client-product-analytics` | 可选 Firebase/自建 | P2 | S |
| HMR / 模块系统 | `dsh-client-hmr`、`dsh-client-modules` | **不可移植**（React/Cordis 专属） | — | — |

### 6.2 明确「MVP 不建」清单

| 不建 | 理由（事实依据） |
|---|---|
| 三栏拖拽 / 宽度手柄 / 右栏分屏 / 浮窗 | 布局是 **瞬时状态**，刷新即回默认；拖动只在普通展开态显示；手机宽度下 `CENTER_MIN=400` 必然触发收栏（`P/dsh-client-ui-layout/README.zh.md`、`columns.d.ts`） |
| 插件管理 / 市场 / 安装 / 启用停用 | 纯浏览器生态：装包要扫描 `dsh.client` 行 + 重建 bundle + HMR（`P/dsh-client-modules`、`dsh-client-hmr` README）；且改的是**服务端**插件图，Android 端无法落地 |
| Cordis 插件定义卡 / 入口定制 | 同上，且属「写代码的代码」类界面 |
| Trajectory 时间概览（滚轮缩放 / 右键框选 / 500ms 悬停） | 精确指针交互，无触摸等价物（`P/dsh-client-ui-trajectory/README.zh.md`） |
| 终端模拟器（PTY 交互） | 需求要键盘布局 + Ctrl 序列 + 重连保留 shell 与输出网格重排（`P/dsh-client-ui-sidebar-terminal/README.zh.md`） |
| 内嵌浏览器 tab（sandboxed iframe） | Web 用 iframe 沙箱；Android 需自建 WebView 沙箱与 Cookie 隔离，收益低于成本 |
| Side-by-side diff review（两列同步横向滚动） | 窄屏不可读；且 diff 上限 5000 行、Shiki 逐 hunk 双色（`ui-deliverables` README） |
| 键盘快捷键体系（`Mod+B`/`Mod+/`/`Esc Esc`/双键重叠录制） | 桌面语义；录制规则含「按住时间重叠、顺序不限」（`P/dsh-client-shortcuts/README.zh.md`），手机无此输入模型 |
| Markdown 选区 → quote 右键手势 | Web 依赖 `ctx.uiConversation.interactions` 的右键+DOM 选区；Android 用系统文本工具条，MVP 只做「复制」 |
| 目录选择器（应用内浏览） | 系统 SAF 已覆盖；`ui-directory-picker-native` 本就是「无渲染、调 OS chooser」的占位（README） |
| 产品埋点 | 与首版目标无关；`desktop` profile 才启用 |
| 多语言全量 8 种 | 先 zh/en；字典结构可直接搬，翻其它语言是纯数据工作（`dsh-client-locale`） |
| 自定义主题 / 图标替换 | `ThemeDefinition.icons` 用 SVG `<symbol>` sprite + `currentColor`，Android 需重做矢量系统 |
| 代码块 Shiki 高亮 | 无 Android 移植；MVP 等宽单色，P1 再接 tree-sitter |
| 数学公式渲染 | 只依赖 KaTeX DOM 选择器过滤（`ui-chat/lib/client.js#L638`），渲染实现不在发行包内（`ui-primitives` 缺失）；MVP 降级纯文本 |
| 附件拖放（跨应用拖拽 / 文件夹拖入） | Web 用 `entry API` + `__DSH_HOST_PATHS__` 桌面桥；Android 无对应桥，改用系统分享 `Intent` |
| 会话 fork / 分支 | 分支操作在 Web 也是「不可用时点击看原因」（`ui-chat` 完成轮次页脚），非高频 |

## 7. 与 PRD 的差异提示

1. **通知是净增量**：`PLAN.md` 未列通知，但 Web 端实测无任何系统通知入口（§5），Android 做 FGS+通知即直接超越 Web。
2. **工具卡是工时黑洞**：Web 有 48+ 个 keyed 视图，逐个复刻不可行；建议「8 类高频 + generic 兜底」，与 `ui-tool` 自身的 fallback 策略同构。
3. **右栏不是一块 UI 而是 5 个 tab 类型**（files/browser/terminal/documentpreview/changes-review，`P/dsh-client-ui-sidebar-right/README.zh.md` 的 `ctx.sidebarRightTabs`），MVP 只保留 documentpreview 的只读子集。
4. **`dsh-client-ui-slots` 不在发行包内**：任何「Android 复刻 slot 组合」的设想都缺契约源，应先确认是否可从上游仓库取得。

