# DSH 插件编写契约（写我们自己的插件需要知道的一切）

> 来源：`/tmp/dsh-src` 全量 TS 源码 + 安装树。所有 `path:line` 相对 `/tmp/dsh-src`。
> 目的：我们要写一个「向 App 声明 UI 面板」的 DSH 插件，本文是它的施工图。

## 1. 一个插件包的最小结构

```
my-panel-plugin/
  package.json          # name / type:module / main / exports / dsh 字段 / files
  src/index.ts          # 宿主半边：Cordis 插件入口 + TypertRemoteService
  src/types.ts          # 请求/响应类型
  src/client.ts         # 浏览器半边（可选，本项目不需要）
  cordis.patch.yml      # 仅「bundle」类包需要
  locale/*.json         # 可选，8 语言
  tsdown.config.ts      # 构建
```

### 1.1 package.json 的 `dsh` 字段

DSH 读三个东西（`packages/boot/plugin-manager/src/index.ts:101` 的 `INSPECT_FIELDS` 含 `dsh`）：

| 字段 | 用途 | 例子 |
|---|---|---|
| `dsh.bundle.patch` | 该包是一组插件组合（bundle），指向 patch 文件 | `{"bundle":{"patch":"./cordis.patch.yml"}}`（`packages/experimental/badge-skill-bundle/package.json`） |
| `dsh.client.inject` / `dsh.client.platform` | 该包有浏览器半边，声明依赖的客户端服务 | `packages/client/ui-schedule/package.json` 的 `dsh.client` |
| （本插件新增）`dsh.appPanel` | **我们加的**：声明此插件向 App 提供面板 | `{"appPanel":{"provider":"@me/dsh-plugin-foo","method":"get"}}` |

**普通功能插件（非 bundle）只需要 `main` + `exports`，`dsh` 字段可省略。**

### 1.2 exports 约定（照抄 goal 插件）

```json
"exports": {
  ".": { "types": "./lib/types/index.d.ts", "default": "./lib/index.js" },
  "./types": { "types": "./lib/types/types.d.ts", "default": "./lib/types/types.js" },
  "./client": { "types": "./lib/types/client.d.ts", "default": "./lib/types/client.js" },
  "./typert": { "types": "./lib/typert.host.d.ts", "default": "./lib/typert.host.js" },
  "./remote": { "types": "./lib/typert.remote-client.d.ts", "default": "./lib/typert.remote-client.js" },
  "./locale/*.json": "./locale/*.json",
  "./package.json": "./package.json"
}
```

> `./typert` 与 `./remote` 是**生成产物**（见 §4），不手写。

## 2. 宿主插件入口（Cordis）

一个插件模块导出 `apply(ctx)`（或默认导出函数），Cordis 调用它注入服务。真实例子：`packages/client/ui-schedule/src/client/index.ts:79-90`

```ts
export const inject = [ /* 依赖的 ctx 服务名 */ ]
export function apply(ctx: ClientContext): void { /* 注册贡献 */ }
```

宿主半边同理，但注入的是 Node 侧服务（`ctx.sessions`、`ctx.agents`、`ctx.typertGateway` 等）。

## 3. 注册一个 Remote 方法

### 3.1 服务类

```ts
export class MyPanelService extends TypertRemoteService {
  constructor(ctx: Context) {
    super(ctx, 'myPanel', { namespace: 'myPanel' })   // 第二参=ctx key，第三参=wire namespace
  }

  @Remote('get')
  async get(request: PanelRequest): Promise<PanelDescriptor> { ... }
}
```

依据：
- 基类 `TypertRemoteService` 在 `packages/typert/protocol/src/index.ts:250-264`，构造签名 `(ctx, serviceKey, options)`，内部 `bindTypertRemote(this, this.name, options)`
- `@Remote(name?)` 装饰器在同一文件 `:282+`；省略名字时用方法名
- 真实用法见 `packages/goal/goal/src/index.ts:236,249,278`（`super(ctx,'goals')` + `@Remote('get')`）

### 3.2 端点字符串

`namespace + '/' + method`（`packages/typert/registry/src/service.ts:72`、`packages/typert/generator/src/analyzer.ts:2105`）。
于是 `session/list`、`myPanel/get`。App 侧就是我们已知的 `POST /api/<ns>/<method>`。

### 3.3 参数形状（重要，容易踩）

App 侧调用时 payload 必须是 `{"args":{...}}`，且**字段名与描述符严格一致**：
- 单对象参数 → `args: {"request": {...}}`（如 `session/prompt`）
- 下划线开头的参数名 → `args: {"_request": {...}}`（如 `session/list`）
- 无参 → `args: {}`（如 `session/modelCatalog`）

> 这是实测过的：字段不匹配会返回 `gateway/arguments-invalid` 并指出缺哪个字段。

## 4. 生成产物 typert

`lib/typert.host.js` / `lib/typert.remote-client.js` 由 **typert 生成器**在构建时产出，里面是 `InvocationDescriptor`（含 `strictCodec.jsonSchema`）。

- 生成器：`packages/typert/generator/`（`analyzer.ts` 解析装饰器，`emitter.ts` 产出）
- 仓库根脚本：`scripts/generate-typert.ts`
- 运行时消费：`ctx.typert.local`（严格模式读描述符）+ `ctx.typertGateway.invoke()`

**对我们最重要的结论**：客户端不该去解析 `.d.ts`，而应**在构建期生成一份静态契约**（JSON 或 Kotlin 数据类）。这也是我们做 Android 客户端时的既定方案。

## 5. 构建

官方包用 **tsdown**（`packages/goal/goal/tsdown.config.ts`）：

```ts
export default defineConfig([{
  entry: ['lib/types/index.js'], outDir: 'lib', format: ['esm'],
  platform: 'node', target: 'es2024', fixedExtension: false, dts: false,
}])
```

> 注意 `entry` 指向 `lib/types/index.js`——**源码先由 tsc 编译到 `lib/types/`，再由 tsdown 打成单文件 `lib/index.js`**。第三方插件可以简化为「tsc 直出 ESM」或者干脆发布纯 JS，只要 `main` 指向可用文件即可。

## 6. 安装与分发

| 动作 | 命令 | 装到哪 |
|---|---|---|
| 装插件 | `dsh plugin --profile web add <pkg>` | `$DSH_HOME/profiles/web/node_modules/` + 写入 `package.json` 的 `dependencies` |
| 启用/停用 | 写 `cordis.patch.yml` 的 `disabled: true/false` | 同上 |
| 组合成 bundle | `package.json` 的 `dsh.bundle.patch` 指向 patch 文件，patch 里 `- insert: [{id, name}]` | — |

patch 文件格式（`packages/experimental/badge-skill-bundle/cordis.patch.yml`）：

```yaml
- insert:
    - id: skill-badge
      name: '@deepseek-ai/dsh-skill-badge'
```

插件市场：`packages/boot/plugin-market/src/types.ts:35-85` 定义了索引结构（`MarketIndexSummary` / `MarketListingEntry` / `MarketInstallation`），支持多索引 URL（本实例配了 4 个）。

## 7. 约束（第三方插件不能做什么）

| 约束 | 说明 |
|---|---|
| 不能绕过 Host/Origin 栅栏 | 所有调用都走认证后的 `/api` |
| 不能自建 HTTP 服务端 | 只能用 `ctx.webServer.register` 注册路由（受同一栅栏保护） |
| 不能读取进程外凭据 | `ctx.credentials` 有 scope 限制 |
| 版本兼容 | 插件依赖 `@deepseek-ai/cordis` 与 `dsh-typert-protocol` 的 API，alpha 期间可能变 |
| bundle 类需 `dsh.bundle` | 否则不会被当作组合包 |

## 8. 我们自己的插件骨架（可直接开工）

```ts
// src/index.ts
import { Context, Service } from '@deepseek-ai/cordis'
import { TypertRemoteService, Remote } from '@deepseek-ai/dsh-typert-protocol'
import type { PanelDescriptor } from './types.ts'

export const inject = ['typertGateway', 'webServer']

export class AppPanelService extends TypertRemoteService {
  constructor(ctx: Context) {
    super(ctx, 'appPanel', { namespace: 'appPanel' })
  }

  @Remote('get')
  async get(request: { sessionId?: string }): Promise<PanelDescriptor> {
    return {
      schemaVersion: 1,
      title: '定时任务',
      blocks: [
        { type: 'list', id: 'tasks', source: 'schedule/list', args: { sessionId: request.sessionId } },
        { type: 'switch', id: 'enable', label: '启用', action: 'schedule/update' },
      ],
    }
  }
}

export function apply(ctx: Context) {
  ctx.plugin(AppPanelService)   // Cordis 注册
}
```

> 详细区块 schema 见 [BLOCK-SCHEMA.md](../BLOCK-SCHEMA.md)。
