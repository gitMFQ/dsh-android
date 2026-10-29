# 04 · Android 客户端技术栈、架构与交付流程（含先行项目调研）

约束：客户端经 HTTPS 连自托管 DSH（`https://dsh.osai.eu.org:8443/`），走"签名 Cookie 握手 + WebSocket mux + 流式事件帧"；聊天 UI 需渲染流式 Markdown、代码、数学、工具调用卡片。协议细节由另一位研究员覆盖，本文只消费其结论。
引用前缀 `DSH:` = `/usr/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/`（只读安装树，2026-10-30 实读）。全部版本号于 2026-10-29/30 由 Maven Central `maven-metadata.xml` 与 Google Maven `dl.google.com/dl/android/maven2` 实查，非记忆值。

## 0. TL;DR

| 决策点 | 结论 | 理由 |
|---|---|---|
| 路线 | **A：纯 Kotlin + Compose 原生**（B 仅作协议调试次级入口） | 协议演进 ⇒ 唯一可变面是"帧→模型"；原生把它收敛成一个纯 JVM 模块 |
| 会话获取 | **API 专用握手 `GET /connect?token=…`**，不模拟浏览器 index 重定向 | DSH 为非页面客户端专门留了这条 302 + 签名 API Cookie 的门（§3.2） |
| 传输 | Ktor 3.6.1 + OkHttp 引擎（HTTP 与 ws 共用一套 CookieJar 管线） | 一元 RPC 走 POST，流走单条 `/api/remote.mux` |
| 序列化 | kotlinx.serialization 1.12.0 + `classDiscriminator="type"` sealed 层级 | 帧本身就是 type 判别联合，但必须加 unknown 兜底 |
| Markdown | commonmark-java 0.24.0 AST → 自绘 Compose 渲染 | 流式需块级重解析；Markwon 属 View 体系，compose-richtext 停在 alpha |
| 数学 | KaTeX 跑在**单个复用**沙箱 WebView（资产内置、离线） | 原生 jlatexmath 无成熟 Compose 版；KaTeX 与流式分片契合 |
| 分发 | 侧载 + Obtainium（GitHub Releases）为主，Play 为可选项 | Play 需过生成式 AI 政策与 targetSdk 门槛，收益低于自托管用户群 |
| 先行项目 | **不存在可直接复用的非浏览器壳**；`bqv/dsh-android` 证明纯原生可行 | 上游 ACP/SDK 是 stdio，liveshare 的 viewer 是 owner 自己的 Web shell |

## 1. 路线对比

| 维度 | **A. 纯 Kotlin + Compose** | **B. Compose 壳 + WebView 载 Web GUI** | **C. Kotlin/Compose Multiplatform** | **D. Flutter / RN** |
|---|---|---|---|---|
| 流式 Markdown/代码/工具卡保真度 | 最高：自绘，可做块级增量 diff 与折叠工具卡 | 最高（就是官方 React 壳）但**不可控**：视觉改动要改宿主 | 高，复用同一套 Compose 渲染 | 中高，需自带 markdown/代码插件生态 |
| 协议演进成本（本项目核心变量） | 低：帧解析收敛到 1 个纯 JVM 模块 | **零**：协议由宿主 React 壳维护 | 低（同 A） | 中：Dart/JS 侧要重写一遍帧模型 |
| 未文档化行为（`args` 键名、`$events` ready 首帧） | 需自己踩坑并固化为契约测试 | 自动跟随官方 | 同 A | 同 A，调试链路更长 |
| Web GUI 插件/侧边栏能力 | 需逐个重实现 | 全部免费 | 需逐个重实现 | 需逐个重实现 |
| 首版工期（2–3 人） | 6–10 周（含 Markdown/数学/卡片） | 1–2 周（含登录/Cookie 注入/下载边界） | 8–12 周（多端收益才兑现） | 7–11 周（+ 一套新语言栈） |
| 二进制/内存 | APK ~8–14 MB，无浏览器内核 | 复用系统 WebView（体积小，内存峰值随页面） | 同 A + 多端产物 | Flutter ~15–25 MB；RN 需 JS 引擎 |
| 后台/保活 | 原生 FGS、WorkManager 可控 | WebView 后台易冻结，重连逻辑在 JS 里不可控 | 同 A | 中：需插件桥 |
| 离线缓存 | Room 全权可控 | 只能靠页面自身（DSH 壳基本不缓存） | 同 A | 中 |
| 团队技能 | Kotlin 一处 | Android + Web 两处 | Kotlin（多端） | Dart/TS 新栈 |
| 主要风险 | 协议漂移时 UI 要跟上（可用未知帧容忍降级） | **宿主升级即破**；302/插件/远程隔离策略随时变 | CMP 在 Android 上仍以 Android 目标为主 | 三套渲染语义对齐成本 |

**推荐 A。** 决定性理由不是"原生更好看"，而是：协议面是**单条 WebSocket + 一族 `type` 判别帧**（唯一定义见 `DSH:dsh-api-gateway/lib/types/stream-protocol.d.ts:4` 的 `REMOTE_STREAM_MUX_PATH`，帧联合见同文件 `RemoteEventDownlinkFrame`）。映射成 Kotlin sealed class 是一次性成本，后续每加一帧只加一个分支；而 B 把这个成本换成"官方 React 壳何时改、插件是否在远程降级"的外部不确定性（`DSH:dsh-api-gateway/README.zh.md:41`、`DSH:dsh-client-connection/README.zh.md:51`）。
**低成本对冲**：A 为主，设置页放一个同进程 WebView 的"Web 兼容视图"（复用 §3.2 的 Cookie 注入），用于协议刚变、原生未跟上的那几天——比全量 B 便宜，比纯 A 稳。

## 2. 依赖清单（版本 + 理由）

### 2.1 `gradle/libs.versions.toml`

```toml
[versions]  # 2026-10-29 实查
agp="9.5.0"          # 9.6.x 仅 alpha; Gradle 本体 9.8.2
kotlin="2.4.21"      # 2.5.0 仅 Beta1/Beta2
ksp="2.3.12"         # 已与 Kotlin 版本解耦
composeBom="2026.09.00"  # 内含 material3 1.4.0 / ui 1.12.1
coreKtx="1.19.1"; activityCompose="1.13.0"
lifecycle="2.11.0"; navigation="2.10.2"
ktor="3.6.1"; serialization="1.12.0"; coroutines="1.11.0"
okhttp="5.5.1"       # Ktor OkHttp 引擎底层，显式对齐防漂移
room="2.8.5"; datastore="1.3.0"; work="2.12.0"; coil="3.6.3"
hilt="2.60.1"        # 备选 koin="4.1.0"
markwon="4.6.2"; richtext="1.0.0-alpha02"; commonmark="0.24.0"
detekt="1.23.8"; ktlint="1.6.0"; robolectric="4.15.1"; junitJupiter="6.1.3"
```

### 2.2 依赖表

| 组件 | 版本 | 理由 / 风险 |
|---|---|---|
| Kotlin | 2.4.21 | K2 稳定；协议类项目不吃 2.5 新语法红利 |
| Compose BOM | 2026.09.00 | 用 BOM 避免 material3 与 ui 错配 |
| core-ktx / activity-compose | 1.19.1 / 1.13.0 | edge-to-edge、predictive back 是聊天页必需 |
| lifecycle-runtime-compose | 2.11.0 | `collectAsStateWithLifecycle`：切后台必须停订阅 |
| navigation-compose | 2.10.2 | 类型安全 `NavKey`；会话/设置/文件三页足够 |
| Ktor core+okhttp+websockets | 3.6.1 | 同一 `HttpClient` 既发 `POST /api/…` 又开 mux，Cookie/连接池共用 |
| OkHttp | 5.5.1 | 显式钉住，保证 CookieJar/拦截器落在同一实例 |
| kotlinx-serialization-json | 1.12.0 | 够用，边界见 §2.3 |
| kotlinx-coroutines | 1.11.0 | `callbackFlow` 包 ws，结构化并发防重连泄漏 |
| Room / DataStore | 2.8.5 / 1.3.0 | 会话消息持久化 / 轻配置；**密钥不进 DataStore** |
| Android Keystore（无库） | — | AES-GCM 包 cookie 与口令，比 `security-crypto` 更可控 |
| WorkManager | 2.12.0 | 只做后台补齐未读，**不**维持 socket |
| Coil 3 | 3.6.3 | 工具卡附件缩略图 |
| Hilt 2.60.1 / Koin 4.1.0 | — | 见 §2.5 |
| Detekt 1.23.8 / ktlint 1.6.0 | — | 见 §2.6 |

### 2.3 多态帧：kotlinx.serialization 够不够

**够，但要三道保险**：

```kotlin
@Serializable @JsonClassDiscriminator("type")
sealed interface Downlink {
  @Serializable @SerialName("ready")     data class Ready(val clientId: String, val host: HostInfo) : Downlink
  @Serializable @SerialName("emit")      data class Emit(val event: String, val args: List<JsonElement>) : Downlink
  @Serializable @SerialName("waterfall") data class Waterfall(val event: String, val eventId: String, val agentId: String) : Downlink
  @Serializable @SerialName("cancel")    data class Cancel(val eventId: String) : Downlink
}
val Json = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; isLenient = true }
// ① ignoreUnknownKeys：新字段不让旧客户端崩
// ② 未知 type：先 parseToJsonElement 读 type 再按白名单 decode，未知者丢弃并计数
// ③ 单帧 decode 失败只丢该帧，绝不断开 mux
```
依据：`DSH:dsh-api-gateway/lib/types/stream-protocol.d.ts` 的 `RemoteEventDownlinkFrame` 就是 `ready|emit|waterfall|cancel` 四型联合，且 `emit` 的 `args` 类型是 `readonly unknown[]`（结构不固定）⇒ `args` 必须落 `JsonElement`，"序列化层够用、DTO 层要克制"。同类判断适用于 `DSH:dsh-api-gateway/lib/types/rpc.d.ts` 的 `ConnectionRpcResult`（`{ok:true,value}` / `{ok:false,error:{code,message,details}}`）。

### 2.4 Markdown / 代码高亮 / 数学

| 方案 | 版本 | 流式增量 | 工具卡/自定义块 | 数学 | 状态 | 结论 |
|---|---|---|---|---|---|---|
| **commonmark-java AST + 自绘 Compose** | 0.24.0 | **好**：只重解析未闭合末块 | 完全自由 | 交 KaTeX/原生 | 低速但稳定 | **推荐** |
| Markwon | 4.6.2 | 差：TextView 长文重排抖 | 需 `AndroidView` 互操作 | jlatexmath 扩展 | 事实停更 | 仅 B 兜底 |
| compose-richtext | 1.0.0-alpha02 | 中 | 中 | 弱 | 停在 alpha02 | 不用 |
| WebView 整页注入 | — | 中 | 无法与原生卡片共存 | 最好 | 需 JS 资产 | 仅装数学单块 |

**语法高亮**：优先自写轻量 tokenizer（覆盖 20 种常用语言即可，工具卡代码通常 <200 行）；确需完整高亮再用 sora-editor——⚠ 该 groupId 在 Maven Central/Google Maven metadata 中**未查到**，落地前须核实真实坐标。
**数学**：**KaTeX 放 WebView**——可离线（`katex.min.js`+字体 ≈0.6–1.2 MB 打进 assets）、与流式 `$…$` 分片契合、失败可降级为源码。关键约束：**全页只复用一个 WebView 实例**（`AndroidView`+`remember`，`WebViewClient` 拦截一切导航），逐公式 `evaluateJavascript` 注入；一屏十来个公式各自建实例会 OOM。原生 `jlatexmath-android` 无 Compose 封装且字形/换行质量落后。

### 2.5 DI 与并发

Hilt 2.60.1（编译期校验，构建 +15~25%）vs Koin 4.1.0（运行时装配，改图不重编）vs 手写 `object Graph`。**推荐先手写，超 5 个模块再换 Koin**：协议演进意味着装配图频繁增删 handler，注解处理器每次都要全量重编。
并发：`Mutex` 保护 socket 写（多条逻辑流共享一条 mux，`DSH:dsh-api-gateway/README.zh.md:41`）；`SharedFlow(replay=0, extraBufferCapacity=256, onBufferOverflow=DROP_OLDEST)`；UI 用 `StateFlow` + `collectAsStateWithLifecycle`。

### 2.6 质量门

```kotlin
// detekt 1.23.8：仅纯 JVM 模块开 Complexity/长函数，UI 模块关 MagicNumber
// ktlint 1.6.0：接 spotless；CI 里 ktlintCheck/detekt 失败即拒
```

## 3. 网络与安全

### 3.1 自签/私有 CA 的 TLS

| 方案 | 配置 | 代价 | 建议 |
|---|---|---|---|
| `network_security_config` 加用户 CA | `<certificates src="user"/>` | 任何已装 CA 均可 MITM | 不作默认 |
| 内置固定 CA（`res/raw` PEM） | `<certificates src="@raw/dsh_ca"/>` | 换证书要发版 | 私有部署首选 |
| TOFU（首信后钉锚点） | 自写 `X509TrustManager` + Keystore 存锚点 | 换证书需用户确认一次 | **推荐** |
| 严格 pinning | OkHttp `CertificatePinner` | 证书轮换即全量断连 | 不建议自托管域 |

要点：`dsh-host-webserver` **不管理证书生命周期**，`tls.certFile/keyFile` 激活期只读一次、**替换需重新加载**（`DSH:dsh-host-webserver/README.zh.md:131`，配置见 `:52-54`）；"客户端仍会校验你提供的证书：本载体不附带信任库、不做 HTTP→HTTPS 重定向"（同文件 `:57`）⇒ 信任决策只能在客户端做。

```xml
<!-- res/xml/network_security_config.xml：禁明文，只加私有 CA -->
<network-security-config><base-config cleartextTrafficPermitted="false">
  <trust-anchors><certificates src="system"/><certificates src="@raw/dsh_ca"/></trust-anchors>
</base-config></network-security-config>
```
⚠ `src="user"` 只在调试/企业构建开；主构建开启等于让任何装过第三方 CA 的手机读走 cookie。TOFU 实现见先行项目 `bqv/dsh-android` 的 `app/src/main/java/uk/xa0/dsh/net/CertTrust.kt`：存**信任锚**而非指纹，让平台继续校验有效期/用途/路径长度——过期证书仍是过期证书。

### 3.2 令牌与 Cookie 持久化（最容易踩空的一步）

| 层 | DSH 机制 | 引用 |
|---|---|---|
| 进程令牌 | 每次启动随机，只在 `GET /` 接受 `?token=`，换成绑定 authority 的签名 cookie 后 302 到干净 `./` | `DSH:dsh-client-connection/README.zh.md:41` |
| Cookie 形态 | host-only、`Path=/`、`HttpOnly`、`SameSite=Strict`；密钥在 `ctx.credentials`，默认 `cookieMaxAgeDays=30` | 同上 `:43` |
| scheme 绑定 | HTTPS cookie 名称/audience 含 `https:`，同 host/port 的 HTTP cookie 在 TLS 监听器上无效 | 同上 `:45` |
| **API 门** | `GET /connect?token=…&interactions=answer|none` → 302 + **mux 目标地址** + 签名 **API Cookie** | 同上 `:51` |
| 信任栅栏 | 每请求校验 `Host`（回环/绑定 IP/`trustedHosts`）与 `Origin`，401/403 在分发前返回 | 同上 `:47` |
| 心跳 | Host 每 `websocketHeartbeatIntervalMs`（默认 2s）Ping，连续两间隔无活动即杀 socket | `DSH:dsh-api-gateway/README.zh.md:95` |

**实现清单**：① 用户粘贴一次性 token（或扫码），`GET /connect` **不跟随重定向**，直读 `Location` 与 `Set-Cookie`；② 自实现 OkHttp `CookieJar`（落 DataStore、值 Keystore 加密），不用 WebView 的 `CookieManager`；③ `Host` 头须等于用户填的 authority（含端口），否则 403；④ 聊天客户端必须 `interactions=answer` 才收 `$events`（审批/提问）；⑤ Cookie 30 天到期要有"重新粘贴 token"降级路径，禁止静默 401；⑥ 非回环且无 TLS 仍可跑但只警告（`DSH:dsh-web-app/README.zh.md:65`），客户端应拒绝非 https。

### 3.3 Doze / 后台限制 / WebSocket 真实存活

| 事实 | 依据 |
|---|---|
| Doze 期间系统**暂停网络访问**并推迟 jobs/syncs/alarms，只在 maintenance window 放行网络 | Android 官方《Optimize for Doze and App Standby》实读 |
| App Standby 与厂商省电策略进一步冻结后台进程 | 同上 |
| Android 15+ 禁止 `BOOT_COMPLETED` 启动 `dataSync` 型 FGS | Android 官方 FGS types 实读 |
| `dataSync` 面向网络传输；短任务用 `shortService` | 同上 |

**不要承诺"常驻连接"**：亮屏前台时 socket 稳定（2s Ping 压住 NAT 超时）；息屏/切后台数分钟内被 Doze 掐断是**正常预期**；Wi-Fi↔蜂窝切换断得更频繁，而 OkHttp 要等写失败或读超时才暴露死链 ⇒ 客户端需"Ping×3=6s 判死链"。
**重连直接对齐 DSH 自家实现，别自创**：`ConnectionController` 按 500ms/1s/2s/4s/8s/10s 上限档重试、50%–100% 抖动、到顶后继续；每次重试**重建物理 socket 并重开 `$events`**；只有 `ready` 首帧才发布 `connected`；`offline` 暂停、`online` 重置档位（`DSH:dsh-client-connection/README.zh.md:62`）；握手 3s 告警、15s 就绪超时（同 `:60`）。

```kotlin
fun run(scope: CoroutineScope) = scope.launch {
  var attempt = 0
  while (isActive) {
    try { client.webSocket("$base/api/remote.mux") {
      awaitReadyFrame(15_000); attempt = 0; pumpIncoming()   // 只有 ready 才算连上
    } } catch (t: Throwable) { /* 记日志，不弹窗 */ }
    delay(backoff(attempt++))                                // 500..10000 + jitter
  }
}
```
① `ready` 前**不清空**已有消息；② 只有 401/403 才提示重新登录，超时/拒连算瞬时故障；③ 断线期间输入进 Room 草稿队列，重连后按 `rpcId` 去重发送。

### 3.4 前台服务与通知

| 场景 | 机制 | 注意 |
|---|---|---|
| 前台聊天 | 无需服务 | 用 `lifecycle-runtime-compose` 停订阅 |
| 长任务执行中切后台 | `dataSync` FGS + 常驻通知 | Android 15+ 有配额；`BOOT_COMPLETED` 不可启动 |
| 审批/提问需回答 | 高优先级通知 | 仅 `answer` 型 interactions 收得到 |
| 周期性补齐未读 | WorkManager（≥15min） | **不**用它维持 socket |

## 4. 工程结构与测试 / CI

### 4.1 模块划分

**推荐 5 模块**——把"会变的协议"关进一个编译期拦得住、且能纯 JVM 测的模块。

```text
dsh-android/
├─ gradle/libs.versions.toml
├─ app/              # 组装、DI、Activity、导航（ui/{chat,session,settings,files}）
├─ core-protocol/    # ★ 纯 JVM：帧模型 + 编解码 + 契约测试（零 Android 依赖）
├─ core-network/     # Ktor client、CookieJar、TLS/TOFU、mux 状态机
├─ core-data/        # Room、DataStore、Keystore、仓库层
├─ core-render/      # Markdown AST→Compose、代码高亮、KaTeX 桥
└─ feature-chat/     # 会话 UI（依赖 core-render，不依赖 app）
```
依赖单向 `app → feature → core-*`；`core-protocol` 零 Android 依赖是能跑纯 JVM 契约测试的前提。

### 4.2 测试策略

| 层 | 内容 | 工具 | 门槛 |
|---|---|---|---|
| 契约（最重要） | 真实实例录制的帧序列 → 解码 | JUnit 6.1.3 + `fixtures/*.ndjson` | 夹具全部解码、零丢帧；新帧类型先加夹具再写码 |
| 传输 | mux 重连/心跳/401 | OkHttp `MockWebServer` 5.5.1 | 连续 3 次断线仍恢复；`ready` 前不误报 connected |
| UI | 流式 Markdown 增量、长列表滚动 | Robolectric 4.15.1 | 只测"用户会抱怨"的行为 |

**真机实例要打，但只放 nightly**：PR 用录制夹具——真机契约不可复现（模型输出每次不同），夹具可复现且能当"协议变更探测器"。

### 4.3 GitHub Actions：签名 APK

```yaml
on: { pull_request: {}, push: { tags: ['v*'] } }
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew :core-protocol:test :core-network:test ktlintCheck detekt
      - name: Assemble signed release
        env:
          KEY_B64: ${{ secrets.SIGNING_STORE_BASE64 }}
          STORE_PASS: ${{ secrets.SIGNING_STORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.SIGNING_KEY_ALIAS }}
          KEY_PASS: ${{ secrets.SIGNING_KEY_PASSWORD }}
        run: |
          echo "$KEY_B64" | base64 -d > release.jks
          ./gradlew :app:assembleRelease -PstoreFile=release.jks
          apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
      - uses: softprops/action-gh-release@v2
        if: startsWith(github.ref, 'refs/tags/')
        with: { files: app/build/outputs/apk/release/app-release.apk }
```
密钥从环境变量读、**绝不入库**：先行项目 `bqv/dsh-android` 的 `app/build.gradle.kts` 即此形状（无密钥时回落 debug 签名，保证 `assembleRelease` 仍产出可安装物而非失败）。

## 5. 先行项目调研（2026-10-29/30 实查）

**上游核实**：`github.com/deepseek-ai/deepseek-harness` **公开可读、MIT、265,668 stars、最后推送 2026-10-28**（`gh repo view`）。上游**没有**可复用的非浏览器客户端壳：

| 上游件 | 形态 | 能否当 Android 底座 |
|---|---|---|
| `dsh-acp` | 标准 ACP over **stdio** JSON-RPC（`DSH:dsh-acp/README.zh.md:2`、`:55`） | ✗ 手机无法喂 stdin；且刻意不提供 DSH 专用呈现数据与交互式 UI（`:12`） |
| `dsh-experimental-liveshare` | viewer 页**就是 owner 自己的 Web shell**（`DSH:dsh-experimental-liveshare/README.zh.md:105`） | ✗ 只在浏览器里跑 |
| `dsh-host-webserver` | Electron 走 `file://` + IPC 桥（`DSH:dsh-host-webserver/README.zh.md:12`） | ✗ 进程内载体，非网络壳 |
| SDK 协议族 | 换行分帧 JSON-RPC（`DSH:dsh-sdk-protocol/README.zh.md:2`） | ✗ 同为 stdio |

**社区项目**（GitHub 检索 `dsh android` / `deepseek harness android` / `dsh-client`）：

| 项目 | 路线 | 连接方式 | 可复用什么 | 许可/状态 |
|---|---|---|---|---|
| **bqv/dsh-android** | 纯 Kotlin+Compose，**无 WebView、宿主零插件** | 直连 `POST /api/…` + `ws /api/remote.mux`；自写 `PersistentCookieJar`、TOFU、`RemoteMux.kt` | **最有价值**：`docs/research/protocol.md`（端点/信封/认证/头要求全景）、342 个 JVM 测试组织法、签名即环境变量的 CI | MPL-2.0；min26/target34；活跃 |
| Hakunm/dsh-android-app | 原生 Compose+Material3 | 需宿主插件 `dsh-workspace`：配对码（10 分钟一次性）+ 设备 token，Keystore 存储、WebUI 可吊销 | 配对/吊销产品流程、文件浏览与编辑器 | **AGPL-3.0** ⚠ 传染，代码不可并入 |
| SparkWeiyang/DeepseekHarness-AndroidClient | 原生三页 | 局域网扫描 + 本地 adb | 局域网发现 UX | MIT；小 |
| hongshuxifan321/dsh-mobile-app | WebView 壳 + 服务端插件 `dsh-plugin-mobile-remote`（`/mobile` + Basic Auth 代理） | 代理注入 Basic Auth，页面内 WebSocket 原生可用 | 壳的最小实现、PWA 兜底、Actions 分发 | MIT；103★ |
| ChanceFlow/deepseek-harness-app | **Flutter** | 接未修改的 dsh web backend | 反面样本：Dart 侧要重写渲染与帧模型 | MIT；早期 |
| pht88888888/DSHAPP、itiswdwa/dsh-android | 自带 Node/Termux/proot，**把宿主装进手机** | 本机跑 DSH 再 WebView 载官方 GUI | 与"手机当客户端"是两条产品线 | MIT |
| ZSeven-W/dsh-android | **不是客户端**：给 DSH 驱动 adb/模拟器的插件 | — | 命名易混，调研时注意 | MIT；209★ |
| dataelement/dsh-desktop、dsh-tauri/…-desktop | 桌面壳（Electron / Tauri 8MB） | 打包宿主 + 内嵌 Web 资源 | 仅参考打包与分发 | MIT |

**结论**：可复用的是**知识与协议参考**（尤其 `bqv` 的 `docs/research/protocol.md` 与 `net/` 包结构），**不存在**可直接 fork 的非浏览器壳。

## 6. 分发：侧载、商店与自更新

| 渠道 | 适用 | 代价 | 建议 |
|---|---|---|---|
| GitHub Releases 直发 APK | 自托管用户、内测 | 需开"未知来源"；无自动更新 | 首发 |
| **Obtainium** | 同上 + 自动更新 | 用户需装 Obtainium；按版本号比较 | **推荐主渠道**（跟踪 Releases 即可，无需自建 JSON） |
| 自建 `version.json` | 想内建更新检查 | 维护 CDN + 签名校验 | 可选 |
| F-Droid | 开源、可复现构建 | 审核慢、需构建配方、不收私有依赖 | 二期 |
| Google Play | 最大触达 | 生成式 AI 政策 + targetSdk 门槛 + 自托管场景审核不确定 | 三期，别在首发赌 |

**Obtainium 事实**（wiki 实读）：按"源"提取版本与 APK；源可给版本号，也可只有 HTML 时用 URL 哈希当**伪版本**（*"HTML source so a URL hash is used instead as a 'pseudo-version'"*）⇒ Releases 资产名稳定 + `versionCode` 单调递增即可自动更新。

```json
{ "versionCode": 42, "versionName": "0.9.2", "apkUrl": "https://…/dsh-android-0.9.2.apk",
  "sha256": "…", "minSdk": 26, "notes": "修复 mux 重连抖动" }
```
App 拉取后**校验 sha256 与签名证书指纹**再交给 `PackageInstaller`；不做静默安装（需 `REQUEST_INSTALL_PACKAGES` 且用户确认）。

**包名与签名硬规则**：① `applicationId` 反域名且稳定（例 `eu.osai.dsh`），debug 加 `.debug`（先行项目为 `uk.xa0.dsh` / `.debug`）；② **同包名不同签名无法覆盖安装** ⇒ 首个公开版前定死 keystore（RSA 4096、25 年），存 CI secrets，丢失等于强制全员卸载重装并丢本地 cookie/草稿；③ `versionCode` 单调递增、由 tag 推导；`minSdk 26`（Android 8.0，与两个主流先行项目一致），侧载 `targetSdk` 取 35/36、上 Play 取当年要求；④ 若上 Play 用 Play App Signing，本地只留上传密钥，避免换机丢签名。
