# DSH 原生安卓客户端 · 总体开发规划

> v0.2（2026-10-30）· 依据：本机发行包 `@deepseek-ai/dsh@0.2.2-alpha.2` + 上游 MIT 源码 `github.com/deepseek-ai/deepseek-harness`（克隆于 `/tmp/dsh-src`）+ 对线上实例 `https://dsh.osai.eu.org:8443/` 的实测探针
> 四份专题研究见 `docs/research/`（01 协议 / 02 API 面 / 03 Web UI 映射 / 04 安卓技术栈），本文是它们之上的汇总决策稿。

---

## 0. 一页结论

| 项 | 结论 |
|---|---|
| **最重要的事** | **已经有人做出来了**，而且不止一个：`bqv/dsh-android`（MPL-2.0，v0.6.6，纯协议、不需要装 Host 插件，140 个 Kotlin 文件 / 8.0 万行 / 342 个 JVM 测试）与 `Hakunm/dsh-android-app`（AGPL-3.0，v1.0.0，29 star，功能更全但依赖 Host 插件 `dsh-workspace`）。规划的第一决策不是"怎么写"，而是**"fork 谁 / 借鉴谁 / 还是从零写"** |
| 技术路线（若自研） | 纯 Kotlin + Jetpack Compose，自研传输层；WebView 只作"协议还没跟上的那几天"的兜底入口，不做主界面 |
| 为什么可行 | 非浏览器客户端是**官方设计内**路径：`GET /connect?token=…&interactions=answer` 签 Cookie；一元调用 `POST /api/<ns>/<method>`；流 = 单条 WebSocket `/api/remote.mux` 多路复用；人机交互（审批/提问）走 `$events` 转发事件 |
| 协议复杂度 | 39 个 namespace / 197 个 endpoint（其中 20 个是流），但**聊天客户端只需要 `session` 的 ~10 个 + `$events`**，其余按需 |
| 最大风险 | ① 协议无版本号、随 alpha 演进；② 聊天渲染（工具卡片 48 类、CoT、Markdown/公式/代码）；③ 移动网络长连接与后台保活 |
| 首版建议 | P0 = 登录 + 会话列表/搜索/新建 + 聊天（流式、工具卡片、中断/重试）+ 审批与提问 + 模型切换 + 图片/附件 + 通知 |
| 工期（自研，单人全职） | M0 协议验证 1 周 → M1 骨架 2 周 → M2 聊天闭环 3 周 → M3 交互闭环 2 周 → M4 打磨 2 周 ≈ **10 周**；若 fork 先行项目，预计 **3–5 周** 可到"自己用得上" |

---

## 1. 目标、边界与非目标

**定位**：把跑在服务器/PC 上的 DSH 变成"手机上随时能用"的客户端——随时看会话、发指令、批准危险操作、看产出。

**首版不做**：插件市场/插件管理、Cordis 插件编辑、完整 PTY 终端交互、文件树编辑、多代理团队看板、工作流 DAG、桌面级多栏拖拽布局。

**边界原则**：只做 Web GUI 已有能力集合的移动端子集，不新增服务端语义（不装插件、不改 Host）。

---

## 2. 已核实的事实基线（含实测证据）

### 2.1 三层协议

```
安卓客户端
  ├─ 认证：GET /connect?token=<launch>&interactions=answer → 302 + Set-Cookie
  │        Cookie 名 = "dsh-auth-" + base64url(sha256(authority))，值 v2.<b64url(json)>.<b64url(hmac)>
  │        HttpOnly / SameSite=Strict / Path=/ / Max-Age 30d / Secure 仅由监听器 TLS 决定
  ├─ 一元：POST /api/<namespace>/<method>   Content-Type: application/json
  │        请求 {type:"client-request", rpcId, method:"<ns>/<method>", payload:{args:{…}}}
  │        响应 {type:"server-response", rpcId, result:{ok:true,value} | {ok:false,error:{code,message,details}}}
  │        二进制结果 → multipart/form-data：metadata(JSON) + bytes-N 分片 + attachments[].path 定位
  └─ 流式：WS /api/remote.mux（唯一 socket，按 streamId 多路）
           上行 {type:"open",streamId,endpoint,payload} | item | end | cancel
           下行 {type:"item",streamId,value} | {type:"error",streamId,error} | {type:"end",streamId}
           心跳：Host 每 2s Ping，连丢 2 次即断（≈4s）；小帧不压缩；ready 超时 15s；重连退避 500ms×2 上限 10s
```

**转发事件（人机交互通道）**
```
open endpoint="$events" payload={"args":{}}
首帧必须 {type:"ready", clientId, host:{home}}   ← 收到才算就绪
其后 {type:"emit",event,args} | {type:"waterfall",event,eventId,agentId,request} | {type:"cancel",eventId}
应答 = 一元 POST /api/$events/result {clientId, eventId, outcome:{kind:"next"|"result"|"rejected"}}
全树仅 2 条 waterfall 事件：approval/request、user-questions/request（共 30 条事件）
```

**实测（本机对线上实例，2026-10-30）**
| 探针 | 结果 |
|---|---|
| `POST /api/session/list` 无 Cookie | **200 且直接返回数据** —— 该实例装了 `dsh-linkless-access` 插件（接管 `browserAuth.isAuthenticated` 与 `authorizeIndex`，鉴权下放给反代）。**App 必须同时支持"Cookie 登录"与"反代免登录"两种模式** |
| `WS /api/remote.mux`（无 Cookie） | 101 升级成功，`{"type":"open","endpoint":"$events"}` 立刻收到 `ready` 帧 |
| payload 缺 `args` | `gateway/internal: Remote payload must contain exactly one plain-object args field` |
| 字段名不符 | `gateway/arguments-invalid`，details 指明缺哪个字段（如 `missing "_request"`） |
| `method` 与 URL 不符 | `gateway/bad-request` |
| 未知端点 / GET /api/** | 404；非 JSON → 415 |

### 2.2 会话数据模型（聊天页唯一数据源 = `session/follow` 流）

```
{type:"snapshot", header, cursor, records[], hasMore, projections, assistantStream?}   // 基线（含 cursor）
{type:"event", event:SessionWireEvent{type,seq,time,…}}                                // 持久增量
{type:"assistant-stream", frame:{type:"start"|"chunk"|"end"}}                          // 实时逐 token
  start: {attemptId,revision,startedAfterSeq,turn,step}
  chunk: {attemptId,revision,index,time,chunk}
  end:   {attemptId,revision,index,outcome:{kind:"committed",eventType,seq}|{kind:"abandoned"}}
```
配套：`session/state`+ `session/observeState`（`pendingInput:'approval'|'question'|'other'` 是通知的最佳钩子）、`session/control`（全局 projection 基线）、`session/wait`（`reason:'idle'|'input-required'|'timeout'`，默认 30s，适合"等任务结束"）、`session/events`（历史分页，`session/page` 是它的弃用别名）。

### 2.3 关键请求体（照抄即可）

| 端点 | args |
|---|---|
| `session/list` | `{"_request":{}}`（cursor 可选） |
| `session/create` | `{"request":{"workspaceId"?,"cwd"?,"sessionId"?,"agentPreset"?}}` |
| `session/follow` | `{"request":{"address":{"kind":"session","sessionId":…},"activate":true,"assistantStream":true,"maxMessages":50,"turnWindow":{"minMessages":50,"minTurns":2}}}` |
| `session/prompt` | `{"request":{"requestId":"<uuid>","sessionId":…,"mode":"queue"|"steer","content":[{"type":"text","text":"…"}],"clientTimeZone":"Asia/Shanghai"}}` |
| `session/cancel` | `{"request":{"sessionId":…}}` |
| `session/modelCatalog` | `{}`（无参端点传空 args） |

> 参数名一律按描述符（多数端点是 `request` 具名参数；`session/list` 是 `_request`）。**M0 阶段务必对每个 P0 端点实测一次并固化成 fixture**，不要凭类型猜。

---

## 3. 路线选择：fork 先行项目 vs 自研

### 3.1 三条路（新增，优先考虑）

| 路线 | 做法 | 工期 | 风险 |
|---|---|---|---|
| **① Fork `bqv/dsh-android`**（推荐先评估） | MPL-2.0，**纯协议、不装 Host 插件**；140 个 Kotlin 文件 / 8.0 万行 / 342 个 JVM 测试，含聊天、轨迹、审批、终端、文件、证书信任、断线重启。改包名/品牌后补差距（多实例、通知、平板） | **3–5 周**到可用 | 上游单人维护、v0.6.6 面向较早 dsh 版本；MPL 要求修改过的文件开源 |
| ①' 参考 `Hakunm/dsh-android-app` | AGPL-3.0，29 star，功能更全（聊天/审批/工作区/文件编辑/模型管理，中英双语），但**依赖 Host 插件 `dsh-workspace`** 并需在插件里配置授权根 | 仅作功能参照 | **AGPL 传染，代码不可并入闭源/私有项目**（连 SAAS 也要开源） |
| ② 借鉴协议层 + 自写 UI | 复用 bqv 的 `net/`（`DshClient` 1155 行、`EndpointResolver` 673、`RemoteMux` 575、`CertTrust` 273、`Multipart` 152、`PersistentCookieJar` 122 等 ~4000 行），UI 与领域层自写 | 6–8 周 | 协议层与其数据模型耦合，剥离成本中等 |
| ③ 从零自研 | 按本文档从骨架开始 | 10 周 | 协议踩坑全自己买单 |

> 关键事实：bqv 项目**证明了"不用 WebView、不装 Host 插件也能完整实现"**这条路（它的 `docs/research/mobile-remote.md` 已确认 DSH 本身**没有**移动端/配对/token 签发能力，`dsh` CLI 无 remote/mobile/pair 子命令）。Hakunm 项目则证明"加一个 Host 插件能换来实现速度与更全的功能"，但把用户绑定到插件。
> **因此推荐：先花 1–2 天做 ① 的可行性评估（跑起来 + 对照本实例接口逐项验证），再决定 ①/②/③；功能清单以 ①' 为准做对照。**

### 3.2 如果自研（或 fork 后重构）的分层

```
:app                 组装、DI、导航、通知、前台服务
:feature:chat        会话页（渲染/输入/流式/审批卡片）
:feature:sessions    会话列表/搜索/新建/工作区
:feature:files       产出文件浏览、图片预览、下载
:feature:settings    实例/登录/模型/通知/外观
:core:chat-render    Markdown / 代码高亮 / 公式 / 工具卡片
:core:dsdata         SessionReducer（snapshot/event/chunk → ChatState，纯函数）、投影、队列
:core:dsc            Auth / Unary RPC / WS Mux / $events
:core:network        OkHttp、CookieJar、重试、证书信任
:core:common         日志、时间、测试工具
```

### 3.3 关键设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 序列化 | kotlinx.serialization + sealed 层级 + **未知帧/字段容忍** | 协议会演进，宽松解析比强 schema 抗升级 |
| 帧节流 | 16–32ms 合批；只在块边界重建 Markdown AST | 逐 token 重排 markdown 会卡死主线程 |
| 会话状态 | 纯函数 reducer + Room 只存快照与分页缓存 | 可测试、可回放、可重连重建 |
| 长连接 | 前台可见时保活；后台靠 `session/wait` + WorkManager 轮询 + 通知，**不假设常驻** | Doze/厂商省电会掐 |
| 凭据 | Keystore + EncryptedSharedPreferences 存 Cookie；支持"粘贴 Cookie"与"反代免登录" | 30 天 Cookie ≈ 长期凭据；本实例正是免登录 |
| 证书 | 系统信任库 + 自签证书信任/指纹（NSConfig + 运行时探针） | 自建实例常见自签 |
| 多实例 | 数据模型预留 `instanceId`，首版单实例 | 避免过早复杂化 |

---

## 4. API 面（39 namespace / 197 endpoint，客户端只需要一小撮）

**P0（聊天闭环）**：`session/list`、`create`、`follow`(流)、`prompt`、`cancel`、`state`/`observeState`(流)、`control`(流)、`events`(分页)、`rename`、`selectModel`、`modelCatalog`、`attachment`、`updateQueue`、`wait`；`skills/list`、`commands/list+execute`、`fileReferences/list`；`fileUploads/upload`；`$events`（审批/提问）+ `$events/result`。

**P1**：`workspace/*`（工作区分组/排序/归档）、`workspaceFiles/{list,read,readBytes,stat}`、`job/{list,follow}`、`directoryPicker/*`、`permissionPresets/catalog`、`messageFeedback/*`。

**P2/不做**：`terminal/*`（PTY 双向）、`pluginManager/*`、`pluginMarket/*`、`cuaAccess/*`、`liveshareShares/*`、`dynamicCordisRunner/*`、`forgePanel/*`、`officeToPdf/*`、`speech/*`。

> **注意**：`deliverables` 与 `agent-team` **没有 Remote 面**（交付物走会话事件 + `/api/present.open` 路由；多代理只在会话事件里体现）。别去翻这两处。

---

## 5. 里程碑（以"自研/借鉴"为基准；fork 路线可直接跳过 M1）

### M0 · 协议验证（1 周，纯 JVM，无 Android）
- 命令行 JVM 程序：登录（Cookie 与免登录两模式）→ `session/list` → `session/follow` 打开会话 → `session/prompt` → 打印流式帧 → 收到审批 waterfall → `$events/result` 回传 → `session/cancel`
- 产出 `protocol-fixtures/*.jsonl`（真实帧样本）+ `docs/PROTOCOL.md` 定稿
- **验收**：真机网络环境下完成一次"发消息 → 流式 → 工具调用 → 审批 → 结束"
- **风险出口**：卡住则退回 WebView 壳方案

### M1 · 工程骨架 + 会话列表（2 周）
Gradle 多模块、CI 出 APK、`DshTransport`（认证/一元/WS/$events/重连）、登录页（地址 + token/Cookie/免登录 + **分步诊断** DNS→TLS→connect→cookie→probe）、会话列表（分组/搜索/新建/归档）

### M2 · 聊天闭环（3 周）
`session/follow` 管线（snapshot 基线 + 增量 + 断线重取）、流式渲染（合批）、Markdown/代码/公式/图片、工具卡片（**先做"8 类高频 + 通用兜底卡片"**，不要逐个复刻 48 类）、输入框（多行/停止/@ 文件// 命令/附件）

### M3 · 交互闭环（2 周）
审批（批准/拒绝/规则）与 `ask_user_question`、`$events/result` 回执、超时与冲突；通知（任务完成 / 等待审批 → 本地通知，点击直达）；产出文件预览；会话 rename/fork/归档/工作区切换

### M4 · 打磨与内测（2 周）
弱网/切后台/旋转/深色/大字体/无障碍；万条消息滚动与内存；崩溃日志与"导出诊断包"；签名、版本号、内测分发（APK 直发 / Obtainium）

---

## 6. 技术选型（版本见 `docs/research/04-android-stack.md`）

Kotlin 2.4.x + Compose BOM 2026.09；Ktor 3.6.1（OkHttp 引擎，HTTP 与 WS 共用 CookieJar）；kotlinx.serialization 1.12；Room 2.8 + DataStore 1.3 + Keystore；Coil 3.6；commonmark-java 0.24 + 自绘 Compose 渲染；KaTeX 置于单个复用沙箱 WebView；WorkManager 2.12 + 前台服务；Detekt/ktlint；GitHub Actions 出签名 APK；分发以侧载 + Obtainium 为主。

（路线 ① 则直接沿用先行项目的 Gradle 结构：无 wrapper、`build.sh` 统一入口、JDK 17 + Gradle 8.7 + AGP 8.x、minSdk 26 / targetSdk 34。）

---

## 7. 风险与对策

| 风险 | 对策 |
|---|---|
| 协议无版本号（alpha 演进） | ① 宽松解析 ② 启动能力探测（`$events` ready + P0 端点探活 + 记录 host 版本）③ fixtures 回归测试 ④ 设置页展示"服务端版本/兼容状态" |
| 鉴权模式分裂（Cookie vs 反代免登录） | 登录页三选一（token 链接 / 粘贴 Cookie / 免登录），并给出**分步诊断**（403 = Host 未受信 → 提示 `--trusted-host`；401 = Cookie 失效；409 = 同一 Cookie 被另一 socket 占用） |
| 移动网络长连接不稳 | 可断可续：心跳看门狗、重连后重取 snapshot、`rpcId` 幂等、`session/wait` 兜底 |
| 渲染复杂度失控 | 工具卡片"高频精修 + 通用兜底"；Markdown 流式只重建尾部块 |
| 上游先行项目停更 | 只复用其协议层（可测试、可替换），UI 自持 |
| 安全 | Keystore 存凭据、证书校验、日志脱敏、服务端可轮换 token |

---

## 8. 需要你拍板（按重要性）

1. **路线**：先花 1–2 天评估 fork `bqv/dsh-android`（推荐），还是直接自研？
2. **首版范围**：是否按 §5 的 M2+M3（含审批、附件、通知）？
3. **面向谁**：只自己用 / 小团队多实例侧载 / 公开发布（商店合规）？
4. **设备**：只手机竖屏，还是手机+平板自适应？最低版本（先行项目是 API 26）
5. **是否要 iOS/桌面**：决定是否 KMP。
6. **服务端配合**：是否允许为 App 增加推送/长连配合；以及实例是否长期保持免登录（当前是 `dsh-linkless-access`）。

---

## 9. 文档索引

| 文件 | 内容 |
|---|---|
| [research/01-protocol.md](research/01-protocol.md) | 线协议逆向：认证、Cookie 推导、一元信封、WS 帧、$events、恢复、Kotlin 实现清单 |
| [research/02-api-surface.md](research/02-api-surface.md) | 197 端点清单、P0 端点的请求/响应、端到端时序图、HITL 通道、破约检测 |
| [research/03-ui-feature-map.md](research/03-ui-feature-map.md) | Web GUI 信息架构、流式渲染、48 类工具卡片、主题 token、Android 页面映射与取舍 |
| [research/04-android-stack.md](research/04-android-stack.md) | 技术路线对比、依赖版本、TLS/保活、模块结构、先行项目调研、分发 |
| [SPIKE.md](SPIKE.md) | 1–2 天开工验证清单（协议最小闭环 + 先行项目跑通 + 决策会），含常见坑与 v0.1.0 验收定义 |
