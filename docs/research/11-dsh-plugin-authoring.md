# 第三方 DSH 插件完整契约（Remote API + 浏览器半边 + 分发）

> 证据源：源码 `/tmp/dsh-src`（下称 `SRC`）+ 安装树 `/usr/lib/node_modules/@deepseek-ai/dsh`（下称 `PKG`）。`path:line` 未加前缀即 `SRC/path:line`。
> 结论：一个包可同时是「bundle（宿主插件行）」和「client（浏览器半边）」；Remote 契约（`typert.*` 产物）必须由 typert 生成器产出，**不能手写**。

## 0. 一个插件包由哪几块拼成

| 块 | 由谁读 | 必需性 | 证据 |
|---|---|---|---|
| `main` / `exports["."]` | Cordis Loader（Node ESM 解析 + `default` 解包） | 宿主半边必需 | `vendor/loader/src/index.ts:200-207`；`packages/client/modules/src/index.ts:889-916` |
| `exports["./client"]` | client-modules 扫描 | 有浏览器半边必需 | `packages/client/modules/src/index.ts:202-212,906` |
| `dsh.bundle.patch` | profile 启动器 / 管理器 | 想「装完即挂载」才必需 | `packages/util/package-manifest/src/types.ts:70-78`；`profile.ts:690-701` |
| `dsh.client` | client-modules + 客户端构建 | 有浏览器半边必需 | `types.ts:86-104`；`modules/src/client/manifest.ts:187-215` |
| `exports["./typert"]` / `exports["./remote"]` | `dsh-typert-loader` / 浏览器 `$mount` | 有 `@Remote` 才必需 | `typert/loader/src/index.ts:40,70,412`；`typert/generator/src/workspace.ts:152-175` |

## 1. package.json：DSH 到底读哪些字段

### 1.1 `INSPECT_FIELDS` 只是安装前侦察，不是 schema

| 事实 | 证据 |
|---|---|
| 安装前 `pnpm view` 只取 `name/version/description/dsh` 四字段，且只推导「是不是 bundle」（`dsh.bundle` 为对象即 true） | `boot/plugin-manager/src/index.ts:101,146-149` |
| 真正的类型是 `DshPackageManifest`（`name/version/description/icon/private/dependencies/optionalDependencies/peerDependencies/engines/dsh`），`dsh` 下只有 `manifestVersion` / `bundle` / `profile` / `client` | `types.ts:9-41` |

### 1.2 三个子 schema

| 子对象 | 字段 | 语义 | 证据 |
|---|---|---|---|
| `dsh.bundle` | `patch` | **必填**，路径或有序路径数组（相对包根） | `types.ts:70-78` |
| | `platforms` | 可选 Node 平台白名单；缺省=全平台；不匹配整层跳过 | `bundle-platform.ts:25-32` |
| | `listedByDefault` | 可选，缺省 true；false 时 GUI 列表默认隐藏 | `plugin-manager/src/index.ts:549-552` |
| `dsh.client` | `platform` | **必填** string，只接受 `'web'` | `types.ts:86-88`；`modules/src/index.ts:903` |
| | `inject` | 包名数组，仅「工厂先到」顺序，**不是 Cordis 注入** | `types.ts:90-91`；`modules/src/index.ts:910` |
| | `immediately` / `external` | bootstrap 预取开关 / 额外 module-table 请求（含 `pkg/client` 子路径） | `types.ts:92-103`；`client/tsdown.client.ts:438-458` |
| `dsh.profile` | `bundles` | 只有 **profile 目录自己的 package.json** 用 | `types.ts:80-84`；`profile.ts:599,613` |

校验失败即抛错：`platform` 非串、`inject`/`external` 非字符串数组、`immediately` 非 bool（`manifest.ts:187-215`）；`dsh.bundle.patch` 非 string|string[]（`profile.ts:54-58`）。

### 1.3 最小 package.json（宿主 + 浏览器 + 生成物 + bundle）

```json
{
  "name": "dsh-acme-panel", "version": "0.1.0", "type": "module",
  "main": "lib/index.js", "icon": "./icon.svg",
  "exports": {
    ".":        { "types": "./lib/types/index.d.ts",         "default": "./lib/index.js" },
    "./client": { "types": "./lib/types/client/index.d.ts",  "default": "./lib/client.js" },
    "./typert": { "types": "./lib/typert.host.d.ts",         "default": "./lib/typert.host.js" },
    "./remote": { "types": "./lib/typert.remote-client.d.ts","default": "./lib/typert.remote-client.js" },
    "./cordis.patch.yml": "./cordis.patch.yml",
    "./locale/*.json": "./locale/*.json", "./package.json": "./package.json"
  },
  "files": ["lib/index.js", "lib/client.js", "lib/types/**/*.d.ts",
            "lib/typert.host.js", "lib/typert.host.d.ts",
            "lib/typert.remote-client.js", "lib/typert.remote-client.d.ts",
            "cordis.patch.yml", "locale/*.json", "icon.svg"],
  "dsh": {
    "manifestVersion": 1,
    "bundle": { "patch": "./cordis.patch.yml" },
    "client": { "platform": "web", "inject": ["@deepseek-ai/dsh-api-remotes"], "external": [] }
  },
  "engines": { "dsh": ">=0.2.2-alpha.2" },
  "peerDependencies": { "@deepseek-ai/cordis": "^4.0.1", "react": "^18.2.0" }
}
```

真实双角色第三方包见 `/root/.dsh/profiles/web/node_modules/dsh-free-search/package.json`；官方样例 `packages/experimental/liveshare/package.json`（`listedByDefault:false` + 三出口）。

## 2. Cordis 插件形状

| 形状 | 规则 | 证据 |
|---|---|---|
| 函数 | `(ctx, config) => any` | `vendor/cordis/src/registry.ts:120-122` |
| 类 | `new (ctx, config)`，**Service 类即插件** | 同上 :124-126 |
| 对象 | `{ apply(ctx, config) }`；解析顺序 `typeof plugin === 'function' ? plugin : plugin.apply` | 同上 :129-133,223-227 |
| 静态声明 | `static inject` / `static Config`（每 fiber 校验）；Loader 用 `exports.default ?? exports` 解包 | 同上 :325-329；`vendor/loader/src/index.ts:200-207` |

源码在用的三种入口：`export default GoalService`（`goal/goal/src/index.ts:652`）、`export { apply, name, inject, type Config }`（`experimental/liveshare/src/index.ts:6`）、纯 UI 的空 `export function apply(): void {}`（`client/ui-goal/src/index.ts:9`）。

## 3. 最小宿主插件：一个服务 + 一个 Remote 方法

`@deepseek-ai/dsh-permission-presets`（全文 482 行）是标准版式：

| 要素 | 代码 | 证据 |
|---|---|---|
| 声明 ctx 服务 | `declare module '@deepseek-ai/cordis' { interface Context { permissionPresets: PermissionPresetService } }` | `interaction/permission-presets/src/index.ts:39-43` |
| 服务类 | `class PermissionPresetService extends TypertRemoteService` | 同上 :174 |
| 依赖 | `static inject = ['shell','approval','sessions','agents','sessionProjections']` | 同上 :195 |
| 构造 | `super(ctx, 'permissionPresets')`：不传 namespace 时 wire namespace = 服务键 | 同上 :204；`typert/protocol/src/index.ts:242` |
| Remote 方法 | `@Remote('catalog')` + `catalog(): PermissionCatalog`；默认导出该类 | 同上 :291-292,482 |

```ts
// src/index.ts
import type { Context } from '@deepseek-ai/cordis'
import { Remote, TypertRemoteService } from '@deepseek-ai/dsh-typert-protocol'
export interface PingValue { readonly pong: true; readonly at: number }
declare module '@deepseek-ai/cordis' { interface Context { acme: AcmeService } }
export class AcmeService extends TypertRemoteService {
  static inject = ['typert']
  constructor(ctx: Context) { super(ctx, 'acme', { namespace: 'acme' }) }
  @Remote('ping') ping(): PingValue { return { pong: true, at: Date.now() } }
}
export default AcmeService
```

## 4. Remote 注册与端点映射

| 问题 | 答案 | 证据 |
|---|---|---|
| namespace 怎么来 | `bindTypertRemote(this, serviceKey, { namespace })`：`namespace = options.namespace ?? serviceKey`，两者过 `validateName` 后 `Object.freeze` | `typert/protocol/src/index.ts:236-246` |
| 基类 / 不继承 | 构造里 `super(ctx, serviceKey)` 并写 `this.typertRemote`；替代写法 `readonly typertRemote = bindTypertRemote(this, serviceKey)` | 同上 :227-233,250-263；`docs/api-gateway.md:17` |
| `@Remote` 三重重载 | 裸装饰器（方法名即端点名）/ `@Remote(name)` / `@Remote({name?, mode?:'stream', resources?})` | 同上 :282-310 |
| 端点字符串 | `namespace + '/' + method`（`typertEndpoint`） | `typert/registry/src/service.ts:71-73` |
| 装饰器限制 | 必须 public 实例方法、键为 string，否则 TypeError | 同上 :433-435 |
| 运行期可见 | initializer 把 marker 写 prototype；网关先取 strict `ctx.typert.local.get(endpoint)`，无则回落 SRC 推断 | 同上 :428-444；`api/gateway/src/index.ts:879-885` |
| 传输 | `POST /api/<namespace>/<method>`，body `{args:{...}}`，逐字段严格匹配 | `docs/api-gateway.md:126,130` |

### 4.1 生成的 `typert.remote-client.d.ts`

`PKG/dsh-permission-presets/lib/typert.remote-client.d.ts` 固定三段：

```ts
/* Generated by @deepseek-ai/dsh-typert-generator from the Host FaceModel — do not edit. */
declare module '@deepseek-ai/dsh-typert-protocol' {
  interface TypertRemoteNamespace$<hex(namespace)> { catalog: () => Promise<RemoteResult<PermissionCatalog>> }
  interface TypertRemoteMap { 'permissionPresets/catalog': () => Promise<RemoteResult<PermissionCatalog>> }
  interface TypertRemoteNamespaceMap { 'permissionPresets': TypertRemoteNamespace$<hex> }
}
export declare const TYPERT_REMOTE:
  TypertRemoteInfo<{ readonly 'permissionPresets/catalog': TypertRemoteMethod<{}, PermissionCatalog> }>
export default TYPERT_REMOTE
```

| 事实 | 证据 |
|---|---|
| 接口名 = namespace 转 hex（`goals`→`TypertRemoteNamespace$676f616c73`） | `PKG/dsh-goal/lib/typert.remote-client.d.ts:11`；`typert/generator/src/emitter.ts:545` |
| 消费侧 `createTypertRemoteInfo(spec, zod)` 惰性造 descriptor；Host 侧 `typert.host.d.ts` 只导出不透明 `TYPERT`，数据在 `.js` | `typert/protocol/src/remote-info.ts:28-30`；`emitter.ts:243`；`PKG/dsh-permission-presets/lib/typert.remote-client.js:20-45` |
| Loader 在 entry 挂载时 import `./typert` 注册进 `ctx.typert`，卸载撤回 | `typert/loader/src/index.ts:1-23,401-412` |

### 4.2 生成是**构建期**、由 tsdown 插件完成

| 事实 | 证据 |
|---|---|
| 根 `tsdown.config.ts` 只在 host 面挂 `decoratorPlugin()`；`typertPlugin()` = 装饰器降级 + `buildStart` 起 Worker 全量生成 + `writeBundle` 写盘 | `tsdown.config.ts:36`；`typert/generator/src/tsdown-plugin.ts:97-155` |
| 写 `<pkg>/lib/typert.<face>.js|.d.ts`；有 Remote 才写 `typert.remote-client.{js,d.ts,d.ts.map}`，host 无 Remote 会**删除**旧 remote 文件 | `typert/generator/src/artifact-files.ts:12-46` |
| 无 bundler 等价入口 `scripts/generate-typert.ts`（`generateContributors` + `writeWorkspaceArtifacts`） | `scripts/generate-typert.ts:14-18` |
| 硬门槛：`exports["./typert"]` 必须为 `{types:'./lib/typert.host.d.ts',default:'./lib/typert.host.js'}`，有 Remote 时 `./remote` 同理，且两对文件都进 `files`；生成器要求根有 `tsconfig.host.json` 与 `packages/` | `typert/generator/src/workspace.ts:116-176,186-190` |

> `typertPlugin` 是公开导出（`@deepseek-ai/dsh-typert-generator/tsdown`），但绑定 workspace 布局；脱离该布局只能自建「装饰器降级 + 产物写盘」。源码启动只走 SRC 兜底，浏览器侧**拒绝**无 strict codec 的 descriptor（`docs/api-gateway.md:136-140,144`）。

## 5. 浏览器半边：`/plugins/<id>/client.js` 从哪来

| 环节 | 机制 | 证据 |
|---|---|---|
| 发现 | 遍历当前 Loader entries，解析 entry 名所在包 package.json，读 `dsh.client`；`platform !== 'web'` 跳过 | `modules/src/index.ts:889-903` |
| 定位产物 | `exports["./client"]` 必须是 string 或 `{default:string}`，相对包根 | 同上 :202-212,905-909 |
| 只认裸包名行 | 子路径 entry、`cordis:*` 内置都不算 client 行 | 同上 :891-899；`docs/cookbook/adding-a-settings-card.md:58` |
| 路由 | `webServer.register({ kind:'prefix', path:'/plugins' })` | 同上 :232,701-704 |
| combo URL | `/plugins/??<id>/client.js[,<id2>/client.js]&rev=<rev>`，页面里用去前导斜杠形式 | 同上 :274-290,528-537 |
| 分块 | 动态 import 编译成 `require.async('./client.<name>.js')`，服务端按 `/plugins/<id>/client.<name>.js?rev=` 供；`client.node.js` 永不作浏览器脚本 | 同上 :199,1147-1156；`client/tsdown.client.ts:471-491` |
| 缺产物 / 包资产 | 缺产物抛 `client bundle not found; run pnpm run build before launch`；资产走 `/plugins/<pkg>/assets/<path>` | 同上 :103-116,239,1196-1230 |

### 5.1 client 模块必须导出什么

| 契约 | 内容 | 证据 |
|---|---|---|
| 注册调用 | 产物**自己**调 `window.__ModuleLoader__.load({ id, factory })`，`id` 必须是包名 | `modules/src/client/manifest.ts:362-374` |
| 产物外壳与格式 | banner `window.__ModuleLoader__.load({ id, factory: (require) => {` / intro `var module = { exports: {} }` / footer `return module.exports; } });`；`format:'cjs'`、`platform:'browser'`、输出 `lib/client.js` | `client/tsdown.client.ts:786-793,916-937` |
| 工厂返回 | 返回 Cordis 插件模块的 exports 即 `{ apply, inject, name }`，浏览器 Loader 当普通插件激活 | `modules/src/client/index.ts:56-66`；真实例 `/root/.dsh/profiles/web/node_modules/dsh-pet/lib/client.js:5820` |
| 可 require 基线 | react、react/jsx-runtime、react-dom、react-dom/client、`@deepseek-ai/cordis`、zod、`@tanstack/react-virtual`、dsh-client-store、dsh-client-ui-slots、dsh-client-ui-primitives、dsh-client-ui-dockkit | `client/web/src/platform.ts:8-15` |
| 其它依赖 | 必须进 `dsh.client.external`，否则**打包期**即报 `client bundle purity` | `tsdown.client.ts:829-853` |

### 5.2 最小 client 插件（加一个面板）

`ctx.slots` 由 `SlotRegistry` 提供（`ui-renderer/src/client/registry.ts:120,158-160`）；`ctx.uiRenderer` 只暴露 `mount(container)`（`ui-renderer/src/client/index.ts:23-31,43-48`）——**注册 UI 用 `ctx.slots`，不是 `ctx.uiRenderer`**。

```ts
// src/client/index.ts
import type { Context as ClientContext } from '@deepseek-ai/cordis'
import type {} from '@deepseek-ai/dsh-client-ui-renderer/client'   // ctx.slots 类型合并
import type {} from '@deepseek-ai/dsh-client-ui-settings/client'   // 'settings.section' 的 SlotMap 声明
export const inject = ['slots']
export function apply(ctx: ClientContext): void {
  ctx.slots.inject('settings.section', () => ctx.slots.register({
    name: 'settings.section', id: 'acme', order: 40, label: () => 'Acme',
  }, () => null))   // 换成真正的 React 组件
}
```

| API | 语义 | 证据 |
|---|---|---|
| `ctx.slots.inject(key, cb)` | 等该 slot 被**父级声明**后才执行；声明消失自动回收；支持 generator 产出多个 disposer | `ui-renderer/src/client/registry.ts:209-269` |
| `ctx.slots.register(opts, Cmp)` | 注册到已声明 slot；`single/keyed/list/chain` 四种基数，同 key 同 priority 冲突抛错 | `ui-slots/src/index.ts:1161-1300` |
| 常用 slot key | `settings.section`、`settings.plugins.tab`、`conversation.input.dock`、`conversation.chat.node`、`sidebar.right.tab.document`、`shell.overlay`、`plugins.detail.section` | `ui-settings/src/client/contract/slots.ts:57`；`ui-goal/src/client/index.ts:75-106` |
| 带 inject 的真实注册 | `ui-goal` 的 `conversation.input.dock`（id/order/locale/inject 全套） | `ui-goal/src/client/index.ts:102-154` |

### 5.3 浏览器侧调自己的 Remote

| 步骤 | 做法 | 证据 |
|---|---|---|
| 1 | 把 `TYPERT_REMOTE` 交给 `ctx.remote.$mount(contribution)`（`{package, descriptors}`） | `api/gateway/src/client/index.ts:214-220,254-280` |
| 2 | `inject` 同时声明 `'remote'` 与 `'remote.<namespace>'` | `docs/cookbook/adding-a-remote-api.md:112`；`ui-goal/src/client/index.ts:64` |
| 3 | 直接 `await ctx.remote.acme.ping()`，返回 `RemoteResult<T>`（`{ok:true,value}` / `{ok:false,error}`），**不 reject** | `docs/cookbook/adding-a-remote-api.md:112-120` |

> 官方把全部 contribution 集中在一个 assembly 里 mount（`api/remotes/src/client/index.ts:186-205`）。自行 mount 需确保该 assembly 已激活（`inject:['remote']`）；`$mount` 走调用方 fiber，fiber 卸载即撤命名空间。

## 6. 构建工具链与发布布局

| 事实 | 证据 |
|---|---|
| 官方：tsc 先出 `lib/types/`，再用 **tsdown**（rolldown）打 `lib/index.js` 与 `lib/client.js`；单包配置一行 preset `clientBundle(import.meta.dirname, ['lib/types/index.js'])` | `package.json` 的 `build:lib:*` 脚本；`client/ui-goal/tsdown.config.ts:1-3` |
| preset **不发布**，仓库外必须自行复刻；纯宿主包可无 tsdown 配置，只要 `main` 指向可用 ESM | `docs/cookbook/adding-a-settings-card.md:60`；`goal/goal/package.json` |
| 构建顺序 | ① 装饰器降级 + typert 生成 → ② tsc 出 `lib/types` → ③ tsdown 出两个入口（`tsdown.config.ts:36`；`docs/api-gateway.md:154-162`） |

```
dsh-acme-panel/
  package.json  cordis.patch.yml  icon.svg  locale/{en,zh,...}.json
  lib/index.js                  # 宿主半边 ESM
  lib/client.js                 # 浏览器半边 IIFE + __ModuleLoader__.load
  lib/typert.host.{js,d.ts}
  lib/typert.remote-client.{js,d.ts,d.ts.map}
  lib/types/**/*.{js,d.ts}
```

locale 的 `meta.title`/`meta.description` 被读作显示名（`package-meta.ts:124-145`），文件名须为语言 id 且与 `en.json` 同目录（:128-133）；图标 SVG/PNG/JPEG/WebP ≤256 KiB（:16-21,41）。

## 7. 分发：安装、落盘、市场

| 环节 | 事实 | 证据 |
|---|---|---|
| CLI 入口 | `dsh plugin` 把剩余参数**原样转发给 profile 目录里的 pnpm** | `apps/cli/src/args.ts:187-201` |
| 首次初始化 profile | 写 package.json + 空 cordis.patch.yml + pnpm-workspace.yaml（`nodeLinker: hoisted`） | `plugin-manager/src/operations.ts:563-576`；`app-boot/src/profile.ts:180-212` |
| 装到哪 | `$DSH_HOME/profiles/<name>/node_modules/`，依赖写入该 profile 的 `dependencies`；`DSH_HOME` 缺省 `~/.dsh` | `app-boot/src/profile.ts:39,165-172`；`util/home-paths/src/index.ts:135-137` |
| 装后自动挂载 | `reconcile()` 把「新装且有 `dsh.bundle`」的包 append 进 `dsh.profile.bundles`；**无 `dsh.bundle` 只给一行 warning** | `operations.ts:90-120`（关键 :103-105） |
| 兼容预检 / 装后校验 | 装前 `pnpm view` 不通过则**不安装**；装后非 bundle → `not-bundle`、兼容不通过 → `incompatible-version`，两者都回滚 | `operations.ts:344-363`；`plugin-manager/src/index.ts:968-976` |
| git 源 / 启停 | prepare 脚本需在 profile 的 `pnpm-workspace.yaml` 的 `allowBuilds` 放行；启停写用户层 `cordis.patch.yml` 的 `- id: <entry>` + `disabled: true`（按 YAML 文档改写） | `apps/cli/src/plugin.ts:104-106`；`build-approval.ts:1-50`；`plugin-manager/src/patch.ts:25-77` |

市场索引（**格式 1**）：`{format:1, title, description?, metadata:{categories,labels,sortKeys}, plugins:[...]}`（`util/plugin-market-index/src/types.ts:94-102`；校验 `index.ts:303-315`）；plugin 含 `name/title?/description?/icon?/author?/homepage?/repository?/metadata(keywords,labels,sortKeys)/versions[]`（:76-92）；version 含 `{version,publishTimestamp,source:{type:'npm',registry?}|{type:'git',url,commit,path?},engines:{dsh?}}`（:43-74）。安装参数推导：npm → `name@version`，git → `git+<url>.git#<commit>[&path:/<dir>]`（:325-331）。

| 收录/配置规则 | 证据 |
|---|---|
| **能被市场收录的硬条件**：有 `dsh.bundle.patch` 且非 private | `apps/plugin-market-generator/README.md:46` |
| 索引源须 http(s)、无凭据、无 fragment；索引源是插件 volatile 配置项 | `plugin-market/src/index-url.ts:11-26`；`plugin-market/src/index.ts:45,111-112` |

## 8. 约束清单：第三方插件不能做什么

| # | 约束 | 依据 |
|---|---|---|
| 1 | **没有沙箱**：同进程同权限，可读整个文件系统与环境变量；`allowBuilds` 只拦安装期脚本，不拦运行期代码 | `build-approval.ts:1-15`；`apps/cli/src/plugin.ts:104-106` |
| 2 | **不能自带 HTTP 服务**：浏览器侧只经 `/plugins` 或自用 `ctx.webServer.register`；`/api` 由网关独占 | `modules/src/index.ts:701-704`；`docs/api-gateway.md:126-130` |
| 3 | **不能重复占端点**：同 namespace+method 二次挂载抛 `contribution repeats ... method` | `api/gateway/src/client/index.ts:282-293` |
| 4 | **不能手写 strict 契约**：浏览器侧拒绝无 strict codec 的 descriptor | `docs/api-gateway.md:136-140` |
| 5 | **不能跨插件值导入**：`@deepseek-ai/*` 值导入必须进 `dsh.client.external`，否则打包期报错 | `tsdown.client.ts:829-853` |
| 6 | **不能把浏览器半边挂子路径行**：只有裸包名的 Loader 行携带 client 半边；无 `dsh.bundle` 的包能装但不自动进 `dsh.profile.bundles` | `docs/cookbook/adding-a-settings-card.md:58`；`operations.ts:103-105` |
| 7 | **不能改受保护插件行**：`PROTECTED_PLUGINS`（loader/gateway/webserver/client-modules/plugin-manager…）不可禁用或卸载 | `util/official-bundles/src/plugin-manager.ts:8-18` |
| 8 | **平台限制**：`dsh.bundle.platforms` 不匹配当前 OS 时整层跳过 | `app-boot/src/bundle-platform.ts:25-32` |

版本兼容：

| 规则 | 内容 | 证据 |
|---|---|---|
| 判据优先级 | 有 `engines.dsh` 就**只看它**、忽略 peer；没有才逐个检查 `@deepseek-ai/dsh` 与 `@deepseek-ai/dsh-*` 的 peer range | `app-boot/src/plugin-compatibility.ts:55-79` |
| 预发布 / `workspace:*` | 比较带 `includePrerelease: true`；`workspace:*` 仅 peer 场景等价「当前版本」，写进 `engines.dsh` 视为不满足 | 同上 :51-53,70；`apps/plugin-market-generator/README.md:56` |
| 不满足后果 | 安装被拒（`incompatible-version`）或启动被 deny | `plugin-manager/src/index.ts:973-974`；`app-boot/src/compatibility-preflight.ts:64-100` |
| 豁免 | `dsh plugin --profile web allow-version <pkg>@<ver> --dsh-version <exact> --accept-risk`，写 `<profile>/compatibility.json`，**只对精确版本对生效** | `apps/cli/src/plugin.ts:18-57`；`app-boot/src/profile-compatibility.ts:10,34-40` |

> 真实第三方包里有 `dsh.compatibility.dshReleases` 字段，但**当前源码树没有任何运行期读者**（`grep -rn dshReleases packages/` 无命中）。真正判据只有 `engines.dsh` 与 peer；Cordis 与 `dsh-typert-protocol` 在 alpha 期仍可能变，应放 peer 并宽松声明。

## 9. 未解 / 需实测项

| 项 | 状态 |
|---|---|
| 脱离 `tsconfig.host.json + packages/` 布局后 `typertPlugin` 能否复用 | 源码未提供独立 CLI（`typert/generator/package.json` 无 `bin`），需自建生成入口 |
| 第三方自行 `$mount` 的时序风险；`dsh.client.external` 对跨插件 slot 契约包（如 `dsh-client-ui-settings/client`）的解析 | 官方仅见集中 assembly 用法，真实第三方包（dsh-pet、dshmarket）**未使用 $mount**；external 需在目标版本上跑一次打包验证 |
