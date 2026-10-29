# DSH 原生安卓客户端 · 三份先行项目评估备忘

> 本文只回答一个问题：**开工第一天该做什么。** 结论：先做一个 1–2 天的"协议 + 先行项目"验证 spike，再决定 fork 谁或自研。

## 1. 候选对比（均已实测仓库元数据，2026-10-30）

| 项目 | 许可 | 规模 | 架构 | 依赖 Host 插件 | 适配本实例（免登录 + 0.2.2） | 结论 |
|---|---|---|---|---|---|---|
| `bqv/dsh-android` | MPL-2.0 | 140 个 main .kt / 5.8 万行 / 1 116 个 @Test / v0.6.6 | 纯原生 Compose，自研 `net/`（DshClient 1155 行、EndpointResolver 673、RemoteMux 575、CertTrust 273、Multipart 152、PersistentCookieJar 122） | **否**（只用官方 HTTP+WS 面） | 需验证：v0.6.6 面向较早 dsh；本实例加了 `dsh-linkless-access`（免登录），需确认其登录页支持"无 Cookie 直连" | **首选评估对象**（架构正、许可宽松、代码可读） |
| `Hakunm/dsh-android-app` | **AGPL-3.0** | 12 个 .kt，模块化程度低，v1.0.0，29 star | Compose，`data/DshClient.kt` 单文件协议层 | **是**（`dsh-workspace` 插件 + 授权根） | 需要客户端装插件，本实例未装 | **只做功能参照，代码不可并入**（AGPL 传染） |
| 自研 | — | — | 按 PLAN.md §3.2 | 否 | 完全可控 | 前两者都不满足时采用 |

**许可要点**：
- MPL-2.0（bqv）：可以 license 不同的更大作品里使用；但**修改过的 MPL 源文件必须继续以 MPL 提供**（新建文件可以是别的许可）。适合闭源分发，只要把改动过的 `net/*.kt` 等文件公开。
- AGPL-3.0（Hakunm）：网络服务也算分发，**几乎不可能闭源**。不要复制其代码。

## 2. 1–2 天 spike 清单（可验收）

目标：用最小成本得到"能连上、能流式、能审批"的铁证，并把协议固化成 fixture。

- [ ] **S1 协议最小闭环**（4–6h，纯 JVM/Kotlin 脚本，不建 Android 工程）
  1. `GET /connect?token=…&interactions=answer`（有 token 的环境）与"直接裸调"（本实例免登录）两条路径都跑通
  2. `POST /api/session/list` → 拿到会话
  3. WS 连 `/api/remote.mux` → `open session/follow` → 收到 `snapshot`
  4. `session/prompt` 发一句 → 看到 `assistant-stream/chunk` 帧
  5. 触发一次审批（让 agent 跑 bash/write 等敏感工具）→ 收到 `waterfall: approval/request` → `POST /api/$events/result` 回 `next`
  6. 把全过程帧写入 `protocol-fixtures/*.jsonl`
- [ ] **S2 先行项目跑起来**（2–4h）
  - clone `bqv/dsh-android`，按其 README 的 `build.sh` 构建（JDK 17 + Gradle 8.7 + SDK 34），装到真机，指向 `https://dsh.osai.eu.org:8443/` 看能否登录/聊天
  - 记录失败点（登录方式、版本不兼容、字段变化），形成"改造清单"
- [ ] **S3 决策会**（1h）：依据 S1/S2 结论选 ①/②/③，并确认 §8 的产品问题

## 3. spike 常见坑（提前写明，省一天）

| 坑 | 现象 | 处理 |
|---|---|---|
| payload 缺 `args` | `gateway/internal` | 一律 `payload:{"args":{…}}` |
| 参数名猜错 | `gateway/arguments-invalid` + `missing "_request"` | 按端点描述符，逐端点 fixture |
| 端点写成点号 | 404 | 用斜杠 `session/list` |
| Host 头不对（反代） | 403 | 服务端加 `--trusted-host <authority>`；客户端不要自造 Host |
| 没带 Cookie 且实例未装免登录插件 | 401 | 走 `/connect` 换 Cookie，或提示用户装 `dsh-linkless-access` |
| 同一 API Cookie 被第二 socket 占用 | WS 409 | 客户端全局单例 socket，先释放再重连 |
| 逐 token 重排 Markdown | UI 卡顿 | 16–32ms 合批 + 只在块边界重解析 |
| 后台假设 WS 常驻 | 收不到消息 | `session/wait` + WorkManager + 通知 |

## 4. 首个可交付版本（v0.1.0-internal）的定义

能装到自己的手机上，完成下列一条真实任务即可发版：
看会话列表 → 进一个会话 → 发一句需求 → 看流式回复与工具调用 → 手机上批准一次 → 看到结果 → 切后台再回来能续上。
