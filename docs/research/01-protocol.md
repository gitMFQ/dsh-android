# DSH 线协议逆向（面向 Kotlin/Android 原生客户端）

> 证据约定：`路径:行号` 均相对 `/usr/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/`（下文 `$P/`）。树中只有 `lib/*.js` 打包产物与 `lib/types/*.d.ts`，**无 TS 源码**；行号 = bundle 真实行号。
>
> **一句话**：启动令牌（URL query）→ 兑换成 **authority 绑定的 HMAC 签名 cookie** → 一元 = `POST /api/<ns>/<method>`（JSON 信封）→ 流式 = 单条 WS `/api/remote.mux`（JSON 文本帧）→ 转发事件 = 同一 WS 里的 `$events` 逻辑流 + HTTP `$events/result` 回执。**不存在 Bearer/API-Key 通道。**

## 0. 常量速查

| 项 | 值 | 证据 |
|---|---|---|
| 共享 HTTP 前缀 / API 兑换路由 | `/api` / `/connect` | `$P/dsh-client-connection/lib/index.js:14,19` |
| WS 路径 | `/api/remote.mux` | `$P/dsh-api-gateway/lib/types/stream-protocol.js:4` |
| 启动令牌 | 32B 随机 → base64url（每进程一次，WeakMap 缓存） | `$P/dsh-client-connection/lib/index.js:275,294-299` |
| Cookie 名 | `dsh-auth-` + base64url(sha256(authority)) | 同上 `277,335-336` |
| Cookie 值 | `v2.<b64url(json)>.<b64url(HMAC-SHA256)>` | 同上 `354-357` |
| Cookie 寿命 | `cookieMaxAgeDays=30`（默认） | 同上 `1017` |
| 请求体上限 | 300 MiB（缓冲式路由） | 同上 `30,1018` |
| 心跳 | 周期 2000ms，连丢 2 次 terminate | `$P/dsh-api-gateway/lib/types/index.js:17`；`stream-server.js:6,81-102` |
| 上行 inbox | 262144 B/流 | 同上 `18` |
| 压缩 | 默认 `'deflate'`，仅 ≥1024 字符下行帧压缩 | 同上 `59`；`stream-server.js:33,273-275` |
| 生成契约规模 | 35 包 / 39 namespace / **197 endpoint** / 20 个 `mode:'stream'` | 本树 `$P/*/lib/typert.remote-client.js` 穷举计数 |

## 1. 认证握手

### 1.1 启动令牌来源
每进程一次性随机 32B → base64url，按 root Context 缓存（`$P/dsh-client-connection/lib/index.js:275,294-299`）。签名密钥另存：`ctx.credentials` 的 `client-connection/browser-session` `grant` 记录（32B，持久于 `$DSH_HOME/.credentials.yaml`），激活时载入内存（`273,391-408`；README.zh.md:43）。**重启换令牌；删凭据记录吊销全部会话；无 logout 端点**（`dsh-client-connection/README.zh.md:82-83`）。令牌只以 query 出现，永不放 header（README.zh.md:41）；打印于 `dsh web: <url>`（`$P/dsh-web-app/lib/index.js:160-162`、`index.js:440-444`）。

### 1.2 浏览器路径 `GET /`
`authorizeIndex`（`index.js:458-497`）：`GET /` + 恰好 1 个 `?token=` 且 timingSafeEqual 命中 → **303**，`Location: ./`，`Set-Cookie`，`cache-control: no-store`、`referrer-policy: no-referrer`（`473-478`）；已有合法 cookie → **303** `./` 不发 cookie（`482-490`）；其余带 token 的请求 → **401** text/plain（`491`）；无 token + 合法 cookie → 放行 index（`494`）。**非 index 静态资源完全公开**（`$P/dsh-host-frontend-static/lib/index.js:79-93`）。

### 1.3 API 路径 `GET /connect?token=...&interactions=answer|none`
`authorizeConnect`（`index.js:505-539`）：

```http
GET /connect?token=<launch>&interactions=none HTTP/1.1
Host: app.example:8443
---
HTTP/1.1 302 Found
cache-control: no-store
referrer-policy: no-referrer
location: ./api/remote.mux
set-cookie: dsh-auth-<b64url(sha256(authority))>=v2.<b64url(json)>.<b64url(sig)>; Max-Age=2592000; Path=/; Expires=<UTC>; HttpOnly; SameSite=Strict[; Secure]
```

- `interactions` **最多一次**，仅 `answer`|`none`，缺省 `none`；重复/非法 → **400** text/plain `interactions must be declared once as answer or none`（`507-516`）。
- 非 `GET`、路径非 `/connect`、`token` 非恰好 1、authority 不可解析、令牌不匹配 → **401**（`519-522`）；栅栏失败 → **403**、服务已 dispose → **401**（`797-803`）。
- 成功时**同步**建 principal=`{kind:'owner'}` 的 Peer，其 `id` 写入 cookie payload `{version:2,authority,id,issuedAt,expiresAt}`（`781-791,525-531`）。
- HTTP 可先于 WS；**一条 API socket 独占清理**：同 cookie 二次升级 → **409**（`756-776`）；socket 关闭移除 Peer，之后该 cookie 的 HTTP 一律 **401**（`742-750`；README.zh.md:51）。
- `interactions=none` 仍可一元/流，但 `$events` 与 `$events/result` 会 `gateway/interactions-forbidden`（`$P/dsh-api-gateway/lib/types/index.js:908-911`）。**要收事件必须 `answer`。**

### 1.4 Cookie 名与属性（须逐字节一致）
```js
audience = secure ? new URL("https://"+host).origin : new URL("http://"+host).host   // index.js:307-315
name     = "dsh-auth-" + base64url(sha256(audience))                                  // 335-336
attrs    = name=value; Max-Age=<maxAge/1000>; Path=/; Expires=<UTC>; HttpOnly; SameSite=Strict
           [+ "; Secure" 仅当该监听器自身为 TLS]                                       // 347-350
```
host-only（无 `Domain`）、`Path=/`、`HttpOnly`、`SameSite=Strict`（README.zh.md:43）。`Secure` **只由接收请求的监听器协议决定**，`--public-url` 与 `X-Forwarded-*` 均不参与（README.zh.md:43,81）；HTTPS 监听器把 scheme 纳入 audience，故同 host:port 的 HTTP cookie 无法过 TLS 校验（:45）。服务端校验（`559-570`）：名须由**本次 Host** 算出 + 签名有效 + `authority` 全等 + `issuedAt<=now<expiresAt` + 区间 ≤ maxAge。**换端口/换 scheme/换 Host 拼写 = 不同 cookie 名 = 401。**

### 1.5 Host / Origin 栅栏（`/api` 与 WS 升级同用）
`isTrustedApiRequest`（`index.js:240-250`）：① `Host` 存在且可解析（按监听器 scheme 归一键，`158-194`）；② Host ∈ loopback（`localhost`/`[::1]`/`127/8`，`127-131`）∪ 监听器 bind IP 字面量（端口不限，`225-231`）∪ `trustedHosts`（带端口=精确 `host:port`，不带=任意端口，`201-207`）；③ `sec-fetch-site: cross-site` 拒（`246`）；④ 有 `Origin` 时必须同 authority（Host 按 **Origin 的 scheme** 重解析，兼容 HTTPS 代理→HTTP 上游，`258-269`）。失败 **403**；Host 可信但未认证 **401**（README.zh.md:47）。`trustedHosts` 条目按 WHATWG 归一化严格校验，非规范写法（`0x7f.0.0.1`、零填充端口、未加括号 IPv6、带 path/userinfo）使插件**加载失败**（`179-183`）；`--host 0.0.0.0` 被拒（`$P/dsh-web-app/lib/startup.js:48`）。

### 1.6 裸 OkHttp 为什么 401/403
| 症状 | 原因 | 证据 |
|---|---|---|
| 401 `dsh web authentication required` | 无 cookie / cookie 名与当前 Host+端口不符 / 已过期 | `571-577,559-570` |
| 403 `forbidden` | Host 非 loopback、非 bind IP、不在 `trustedHosts` | `240-245,797-803` |
| 409（仅 WS） | 该 cookie 已有活动 socket | `756-776` |
| 400（`/connect`） | `interactions` 重复或非法 | `507-516` |
| 415 / 404（`/api/...`） | `content-type` 非 `application/json` / endpoint 段非法或未认领 | `888-916,926-931` |

全树无 `authorization` 头解析（`grep -rn authorization` 于 `dsh-client-connection/lib`、`dsh-host-webserver/lib` 为空）；README.zh.md:41 明示不接受 header token。

### 1.7 TLS 反代后的实例配置
| 需求 | 配置 | 证据 |
|---|---|---|
| 放行 `Host: app.example` | `--trusted-host app.example`（可重复） | `$P/dsh-web-app/lib/startup.js:25,67`；`index.js:1016,1032` |
| 仅改打印/打开 URL，**不授权** | `--public-url https://app.example/ui/` | `startup.js:25`（原文 "grants no trust"）、`dsh-web-app/lib/index.js:140-146` |
| 让 cookie 带 `Secure` | 必须 dsh 自身 TLS：`--tls-cert`+`--tls-key`（必须成对） | `startup.js:25,50-55`；`$P/dsh-host-webserver/README.zh.md:57` |
| 代理须保留 Host | 否则 authority 变成上游 host:port → 浏览器侧 cookie 名不符 → 401 | `index.js:307-315,559-570` |
| 代理终止 TLS、上游 HTTP | cookie 不带 `Secure`，audience 不含 scheme | README.zh.md:43,45,81 |

WS 升级路由**仅在 `ctx.appReady` 完成后注册**（`$P/dsh-api-gateway/lib/types/index.js:141-153`），启动中的 Host 表现为"载体失败"，靠客户端重试覆盖。

## 2. 一元 RPC（HTTP POST）

### 2.1 请求
```http
POST /api/session/list HTTP/1.1
Host: app.example:8443
content-type: application/json
cookie: dsh-auth-<hash>=v2....

{"type":"client-request","rpcId":"<uuidv4>","method":"session/list","payload":{"args":{"agentId":"..."}}}
```
- URL = `/api/` + endpoint；段文法 `[A-Za-z0-9_$.-]+`，禁空段/`.`/`..`（`$P/dsh-client-connection/lib/index.js:690,926-931`；客户端同判 `client.node.js:309-310,425-428`）。
- **`method` = `<namespace>/<method>`**，由 `endpointOf(namespace, method)` 直接拼接（Host `$P/dsh-api-gateway/lib/types/index.js:1183-1185`；Client `lib/types/client/index.js:567-569`；注册表键同形 `$P/dsh-typert-registry/lib/types/service.js:32-34`）。本树 39 个 namespace（`session`/`terminal`/`goals`/`account`/`workspace`…）。
- `method` 必须等于路径 endpoint，否则 `gateway/bad-request`（`888-916`）。
- `payload` 必须**恰好一个 `args` 普通对象**（`index.js:922-936`）；键集 = 描述符 `parameters[].wire`（lookup 参数也在此传），多/缺键 → `gateway/arguments-invalid`（`1248-1273`）。
- 取消 = 断开连接（bridge `res.on('close')`→`abort`，`$P/dsh-client-connection/lib/index.js:40-44`）；`AbortSignal` 是描述符元数据，非 wire 字段（README.zh.md:39）。

### 2.2 响应
```json
{"type":"server-response","rpcId":"<回显>","result":{"ok":true,"value":<JSON>}}
{"type":"server-response","rpcId":"<回显>","result":{"ok":false,"error":{"code":"gateway/xxx","message":"...","details":{}}}}
```
信封 schema `$P/dsh-client-connection/lib/index.js:631-644`；`error` 固定 `code/message/details`（`611-615`）。`rpcId` 由客户端生成，回显不一致 → 客户端抛 `rpcId mismatch`（`client.node.js:322,338`）。业务 `RemoteError` 原码过线，其余折叠 `gateway/internal`（details 空）（`$P/dsh-api-gateway/lib/types/index.js:1166-1179`）。void 结果**无 `value` 字段**（`encodeRpcResult`，`789-799`）。码表（grep `gateway/[a-z-]*`）：`bad-request`、`arguments-invalid`、`input-invalid`、`cancelled`、`internal`、`peer-disposed`、`invocation-unavailable`、`interactions-forbidden`、`service-unavailable`、`method-unavailable`、`ambiguous-endpoint`、`definition-unavailable`、`lookup-unavailable`/`lookup-failed`/`lookup-not-found`、`context-unavailable`/`context-failed`/`context-not-found`、`provider-mismatch`、`signature-invalid`、`binding-invalid`、`result-invalid`、`protocol`、`uplink-overflow`。

### 2.3 二进制结果 = multipart/form-data
`fullResponse`（`index.js:938-968`）：part `bytes-0..n` 为原始字节，`metadata` 为完整 `server-response` 信封**外加** `attachments` 表：
```json
{"type":"server-response","rpcId":"...","result":{"ok":true,"value":{...}},
 "attachments":[{"path":["value","field",0],"codec":"bytes","part":"bytes-0"}]}
```
`value` 对应位置放 `null` 占位；客户端按 `path`（字符串键/数字下标，不含业务字段名）逐项替换为字节，字段集须闭合（`client.node.js:348-395`）。上行不支持二进制（README.zh.md:32）。

### 2.4 精确 Fetch 路由（非 RPC）
`ctx.connection.fetch.register({path,methods,requestBody})` 的精确路由优先于 RPC interceptor，且仅 owner principal（`index.js:820-839`）；用于会话日志下载、原始文件上传等非 JSON 响应。未认领 → 404。

## 3. WebSocket `/api/remote.mux`

### 3.1 升级
- 路径精确匹配（`stream-protocol.js:4`；注册 `$P/dsh-api-gateway/lib/types/index.js:99-137`）。
- 与 `/api` 同一 `admitConnection`（栅栏+cookie）；失败以**裸 HTTP** 收尾而非握手（`rejectRemoteStreamUpgrade`，`stream-server.js:416-427`）：`401 Unauthorized`/`403 Forbidden`/`409 Conflict`，body 为小写原因词 + `Connection: close`。
- 首升成功即独占该 cookie 的 socket；socket 绑定 Peer scope，Peer 释放 → `close(1001,"peer left")`（`395-403`）。
- **仅文本帧**：二进制 → `close(1003)`（`stream-server.js:136-139`）；非法 JSON → `close(1008)`（`144`）；客户端自决码：`4002` 非法帧（`client.node.js:436`）、`4000` 重连（`185`）、`1000` dispose（`332`）。

### 3.2 帧联合（键集必须精确，多余键 = 非法）
Client→Host（`parseRemoteStreamClientMessage`，`stream-protocol.js:148-170`）：
```
{"type":"open","streamId":"<非空>","endpoint":"<ns/method>","payload":{"args":{...}}}
{"type":"item","streamId":"...","value":<无损 JSON>}   // 省略 value 表示 undefined 项
{"type":"end","streamId":"..."}                         // 半关闭上行
{"type":"cancel","streamId":"..."}                      // 取消该逻辑流，不关 socket
```
Host→Client（`176-198`）：
```
{"type":"item","streamId":"...","value":<JSON>}
{"type":"end","streamId":"..."}
{"type":"error","streamId":"...","error":{"code":"...","message":"...","details":{...}}}
```
- `streamId` 由客户端生成（uuid），非空且同连接唯一；**重复 `open` 是唯一会关 socket 的违规**（`stream-server.js:186-189`）；发往已结束流的 item/end/cancel 静默丢弃（`160-185`）。
- 上行逐项 Host 校验：被拒 → 整流 `gateway/input-invalid`(field=uplink)；`end` 后再发 → `gateway/protocol`；超 262144B → `gateway/uplink-overflow`；**只失败该流不关 socket**（`stream-server.js:301-388`；README.zh.md:45）。
- `invocation.uplinkWindow` 向方法暴露 inbox 容量与 JSON 帧开销（`stream-server.js:212-215`）。

### 3.3 心跳 / 压缩
- Host 每 2s 发 **Ping**；Pong、任意收到帧、或"未一次性完成的下行写入"均算活动；连续 2 周期无活动 → `terminate()`（`stream-server.js:6,81-153`）。客户端必须自动回 Pong（OkHttp 默认），否则 4s 内被杀。
- `permessage-deflate`：默认协商，Host 只压 `text.length>=1024` 的下行帧（`stream-server.js:33,273-275`）；`'none'` 时不带扩展。客户端可协商可不协商（README.zh.md:41,96）。

### 3.4 重连 / generation
- 物理 socket 由 **Connection** 重试：`backoffBaseMs=500, factor=2, max=10000`，每档 50%–100% 抖动，达上限后持续尝试（`$P/dsh-client-connection/lib/index.js:983-989`；`client.node.js:108-115`）。每次 retry 先取消候选/活动 socket，再建一次全新连接（README.zh.md:60,62）。
- `reconnect()`：`revision++`、`failAll`、旧 socket `close(4000)`、`maintain()`（`client.node.js:174-191`）。
- 就绪时限：`generationReadyWarnMs=3000`（告警）、`generationReadyTimeoutMs=15000`（中止该代）（`983-989`；`client.node.js:234-263`）。
- 浏览器 `offline` → `disconnected` 暂停；`online` → 重置从 500ms 起（`client.node.js:97-107,467-483`）。
- **逻辑流不跨代重放**：socket 丢失时在途 stream 全以 carrier error 失败，须重开；仅 `$events` 由 Connection 自动重开。

## 4. 转发事件流

### 4.1 打开与 ready
mux 上 `open`：`endpoint="$events"`，`payload={"args":{}}`（必须**恰好**空 args，否则 `gateway/arguments-invalid`）（`$P/dsh-api-gateway/lib/types/index.js:343-356`；常量 `stream-protocol.js:6,10`）。前置：Peer 存活 + `interactions==='answer'`（`908-911`）。首项必为 ready（严格键集 `{type,clientId,host}`，host 仅 `{home}`）：
```json
{"type":"ready","clientId":"<uuid>","host":{"home":"/root"}}
```
（`index.js:377`；`host.home` 来自 `homedir()`，见 `$P/dsh-api-remotes/lib/index.js:155`；Client 校验 `client.node.js:741-751`）。**收到 ready 才发布 generation**；非 ready 首项/畸形项 → 该代失效并重试（README.zh.md:58,60）。

### 4.2 帧
| 帧 | 形状 | 语义 | 证据 |
|---|---|---|---|
| emit | `{"type":"emit","event":"<name>","args":[...]}` | 单向广播，**不重放**；非 owner 仅当 resources 允许时整条送达 | `index.js:400-423`；`client.node.js:759-767` |
| waterfall | `{"type":"waterfall","event":"...","eventId":"<uuid>","agentId":"<非空>","request":{...}}` | Agent 作用域，**必须应答**；`request` 不含 `agent`/`signal` | `index.js:453-469`；`client.node.js:768-779` |
| cancel | `{"type":"cancel","eventId":"<uuid>"}` | 该 waterfall 已结算/取消，abort 本地 handler | `index.js:538-544`；`client.node.js:755-758` |

**事件全集共 30 条（`$P/dsh-api-remotes/lib/index.js:21-144`）**：waterfall 仅 2 条 —— `approval/request`、`user-questions/request`；其余 28 条 emit。声明 `resources` 的 5 条：`api-session/activity`、`api-session/added`、`api-session/error`、`api-session/removed`、`api-session/status`（均按 session）。其余 emit：`agent-preset/selected`、`commands/change`、`deepseek-account/session-expired`、`deepseek-account/model-sign-in-required`、`credentials/record-updated`、`credentials/reference-updated`、`goal/activation-changed`、`cordis/request-run`、`cordis/request-run-resolved`、`cordis/dynamic-package`、`cordis/dynamic-retract`、`cordis/inspect-query`、`cordis/inspect-query-resolved`、`llm/adapters-updated`、`llm/models-updated`、`permission-presets/catalog-changed`、`plugin-manager/changed`、`plugin-manager/install-log`、`plugin-manager/install-state`、`plugin-manager/update-state`、`plugin-market/changed`、`settings/document-updated`、`schedule/changed`。Client 端 listener key 前缀 `internal/api-gateway/remote-event/<uuid>/`（`client.node.js:591,733-735`）。

### 4.3 应答 `$events/result`（回到一元 HTTP）
```http
POST /api/$events/result
{"type":"client-request","rpcId":"...","method":"$events/result","payload":{"args":{
  "clientId":"<ready 的>","eventId":"<waterfall 的>","outcome":{"kind":"result","value":<JSON>}}}}
```
- `outcome.kind` ∈ `next`（放行给后续 Host listener）/ `result`（可省 `value`）/ `rejected`（`{kind,error:{name,message,code?,details?}}`）（`stream-protocol.js:18-54,229-245`）。
- 仅 owner Peer，且须与持有该 `$events` 流的 Peer 相同，否则 `gateway/invocation-unavailable`/`gateway/interactions-forbidden`（`index.js:307-330`）；成功返回 `ok:true` 无 value；事件已结算的应答**幂等无操作**（`491-510`）。

### 4.4 断线后必须重基的内容
| 类别 | 断线后 | Kotlin 动作 |
|---|---|---|
| `$events` 流 | 自动重开，得**新 `clientId`** | 丢弃旧 clientId；未决 waterfall 由 Host 以同 `eventId` **重放**（README.zh.md:92） |
| emit 通知 | **不重放**，best-effort | 自行重拉基线/快照（`api-session/*`、`schedule/changed` 等均按"通知后重读"设计，`$P/dsh-api-remotes/README.zh.md:49-51`） |
| 在途 waterfall | pending 保留，重连后重发同 `eventId` | 以 `eventId` 去重 |
| 普通流式调用 | 全部以 carrier error 失败 | 重 `open` 并自带游标（journal/snapshot 语义，`$P/dsh-api-gateway/lib/types/client/journal-stream.js`） |
| `host.home` | 每代重发 | 每代重读，勿当常量（`$P/dsh-api-gateway/README.zh.md:64`） |

## 5. Kotlin 实现清单
```kotlin
// 引导一次：OkHttp 的 CookieJar 接管即可
val req = Request.Builder()
    .url("$base/connect?token=$launch&interactions=answer").get().build()
// 之后所有 HTTP / WS 均只靠 CookieJar；不要手写 Authorization
// WS: client.newWebSocket("$wsBase/api/remote.mux", listener)  // OkHttp 自动回 Pong
```
| 事项 | 要求 | 依据 |
|---|---|---|
| Cookie 存储 | 用 `CookieJar` 或原样保存 `Set-Cookie`；**不要**自算 cookie 名 | `index.js:335-336` |
| 令牌存储 | 仅引导期使用，302 后立即丢弃；长期凭据 = cookie | `index.js:440-444`；README.zh.md:83 |
| 401 | 无法静默刷新，只能重获启动令牌（重跑 `dsh web` 打印的 URL） | README.zh.md:82-83 |
| 409（WS） | 旧 socket 未释放；先关本地再重连，或重新 `/connect` | `index.js:756-776` |
| 一元重试 | 只重试幂等方法 + carrier 失败（`gateway/internal` 可能已执行） | `lib/types/client/index.js:618-640` |
| WS 退避 | 500ms 起 ×2 上限 10s，50%–100% 抖动，成功归零 | `index.js:983-989`；`client.node.js:108-115` |
| 就绪超时 | 15s 无 ready ⇒ 该代失败并重连 | `client.node.js:234-263` |
| 重连后 | 重开全部 `mode:'stream'`（带游标）+ 重挂 `$events` + 重读快照；**不**期望 emit 重放 | §4.4 |
| 帧严格性 | 键集精确，多余键触发 `1008`/客户端 `4002` | `stream-protocol.js:148-198` |
| 限额 | 请求体 ≤300 MiB；上行 ≤262144 B/流 | `index.js:30`；`index.js:18` |

## 6. 代码生成可行性

### 6.1 `*.remote-client.d.ts` 是稳定契约吗
是**生成物**（首行固定 `/* Generated by @deepseek-ai/dsh-typert-generator from the Host FaceModel — do not edit. */`，`$P/dsh-goal/lib/typert.remote-client.d.ts:1`），且与同目录 `typert.remote-client.js` 同源配对；本树共 **35 个**。形状规整：`TypertRemoteMap`/`TypertRemoteScopeMap` + `export declare const TYPERT_REMOTE: TypertRemoteInfo<{...}>`（`:22-50`）。但**直接解析 d.ts 对 Kotlin 是下策**：类型是 TS 别名（`@deepseek-ai/dsh-session/types#SessionId`），需跨包解析 `lib/types/*.d.ts`，且 **d.ts 不含 jsonSchema**。

### 6.2 更好的源：运行时描述符（自带 JSON Schema）
每包 `lib/typert.remote-client.js` 导出 `TYPERT_REMOTE = createTypertRemoteInfo({package, methods:{'<ns>/<method>':{...}}})`，每个方法内嵌完整 `jsonSchema` 字面量（`$P/dsh-goal/lib/typert.remote-client.js:197-253`）：
```js
'goals/clear': { service:'goals', description:'...', invocation:{kind:'direct'},
  scope:{context:'agent', wire:'agentId'},
  parameters:[ {name:'agent', wire:'agentId', source:'lookup', lookup:'agent',
                codec:{mode:'strict',typeSymbol:'...',create:<zod factory>,jsonSchema:{"type":"string"}},
                strictCodec:{...}},
               {name:'ref', source:'json', codec:{...}, strictCodec:{...}} ],
  result:{mode:'strict', typeSymbol:'...', jsonSchema:{...}},
  sourceLocation:{"file":"packages/goal/goal/src/index.ts","line":435,"column":3} }
```
流式方法另有 `mode:'stream'` + `uplink:{codec:{...}}` + `cancellation:{parameter:'signal'}`（`$P/dsh-api-terminal-controller/lib/typert.remote-client.js` 的 `'terminal/attach'`）。`jsonSchema` 有两档：`codec.jsonSchema`（`additionalProperties:true`，宽松）与 `strictCodec.jsonSchema`（`false`，严格）；Host 按严格档校验（`$P/dsh-typert-protocol/README.zh.md:51`）。
**实测规模**：35 包 / 39 namespace / **197 endpoint** / 20 个 stream 方法。

### 6.3 有没有运行时 dump API
**有 Node 端 registry API，但没有跨网络的 dump RPC。**
| 能力 | 签名 | 证据 |
|---|---|---|
| 包反射 | `ctx.typert.getPackage(pkg, face='host')` / `listPackages(filter?)` | `service.js:531`；`$P/dsh-tool-cordis/lib/types/api-catalog.js:4029-4039` |
| schema | `get(key)` / `resolve(key)` / `list(filter?)` | catalog `4010-4027` |
| 投影 JSON Schema | `toJSONSchema(key, params?)`（不缓存） | `service.js:540-542`；catalog `4041-4045` |
| 调用描述符 | `ctx.typert.local.get/list`、`ctx.typert.remotes.get/list` | `$P/dsh-typert-registry/README.zh.md:42` |
| 端点键 | `typertEndpoint(d) = namespaced/method` 拼接 | `service.js:32-34` |

registry README 明示 **"不存在独立的元数据存储或 RPC API"**（`:42`）。Gateway 只认 `$events` 与业务 endpoint；在 `dsh-api-gateway/lib`、`dsh-typert-registry/lib`、`dsh-typert-protocol/lib` grep `\$schema|\$describe|/schema` **无命中**。→ **客户端无法从服务端动态拉契约**。

### 6.4 推荐路线
1. **构建期**：Node 侧离线脚本 `import TYPERT_REMOTE from '<pkg>/lib/typert.remote-client.js'`，逐个序列化 `{package, methods:{endpoint:{mode,scope,parameters[{wire,source,lookup,strictCodec.jsonSchema}],uplink,result,description}}}` → `contract.json`（剔除 `create` zod factory，不可序列化）。
2. **codegen**：由 `contract.json` 生成 Kotlin endpoint 常量、`@Serializable` 数据类、一元/流分流、`args` 键名；契约版本记各包 `package.json` version 指纹。
3. **运行期兜底**：未知 namespace 视为"未实现"而非崩溃；新增能力需重发版（`ClientRemote.$mount` 也是编译期固定集合，`$P/dsh-api-remotes/README.zh.md:77-78`）。

### 6.5 空白 / 未解
- 本树**无 TS 源码**，`sourceLocation` 指向的仓库路径不可读，无法交叉验证生成器语义。
- `dsh-typert-generator` **未随发行物提供**（`ls $P | grep generator` 空），只能逆向产物、无法逆向规则。
- `$events` 里 `approval/request`、`user-questions/request` 两条 waterfall 的 `request` 字段形状在 `$P/dsh-user-approval`/`$P/dsh-user-questions` 的类型声明中（本树仅 `lib/types/*.d.ts`），需逐条从 d.ts 反推后写入契约。
