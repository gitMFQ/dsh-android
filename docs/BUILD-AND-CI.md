# 构建与 CI（在 aarch64 Debian 上开发，在 GitHub Actions 上出包）

> 事实采集时间 2026-10-30；对象：本机（Debian forky/sid，**aarch64**，3 GB RAM，47 GB 空闲）+ fork 候选 `bqv/dsh-android`。

## 0. 实测更新（2026-10-30，本轮真跑过）

| 步骤 | 结果 | 证据 |
|---|---|---|
| 装 JDK 17 | ✅ | `openjdk 17.0.21.0.3`（apt） |
| 装 Gradle 8.7 | ✅ | 下载 134 MB，`gradle --version` 正常 |
| 装 Android platform-34 | ✅ | `platform-34-ext7_r03.zip`（注意：`platform-34_r03.zip` 是 404） |
| 装 build-tools 35 | ✅ 但**无用** | 解出来是 `android-15/`，`aapt2` 是 **x86_64** ELF，本机 aarch64 跑不了 |
| **Gradle 配置阶段** | ✅ **通过** | `gradle help` → `BUILD SUCCESSFUL`（Kotlin 2.4.21 + AGP 8.5.2 插件解析成功） |
| `:app:compileDebugKotlin` | ❌ 在 `processDebugResources` 挂 | `AAPT2 ... Syntax error: "(" unexpected`（x86_64 二进制被当脚本执行） |
| 找 aarch64 aapt2 | ❌ 全部失败 | 官方 SDK 无 aarch64；Termux 的 aapt2 是 **Android bionic**（`/system/bin/linker64`），glibc 下跑不了；Termux 依赖库也随之无效 |
| Debian `aapt` | ✅ 装了，但那是 **aapt1**（`Android Asset Packaging Tool v0.2-debian`），AGP 只要 aapt2 |

**结论修正**：本机**能验证 Gradle 配置/插件解析**，**不能**编译资源与打包，**也不能**跑那 1 116 个测试（它们依赖 `processDebugResources` 产出的 R 类）。**全部验证必须走 CI**

### 0.0 后续进展：qemu 路线走到哪了（2026-10-30 晚）

用户提示「缺什么工具链都可以尝试安装」后，又推进了两步，最终**仍未打通**：

| 尝试 | 结果 |
|---|---|
| 装 `qemu-user` + `libc6:amd64` | ✅ `qemu-x86_64 -L / /opt/.../aapt2 version` → **`Android Asset Packaging Tool 2.20`** 能跑 |
| 写 shell 包装脚本指向 qemu | ❌ AGP 报 `Custom AAPT2 location does not point to an AAPT2 executable` |
| 编译 **aarch64 ELF** 包装器（C，execv 转 qemu） | ❌ 同样被拒 —— AGP 的校验不只看"是不是可执行文件" |
| 注册 `binfmt_misc` 让内核直接识别 x86_64 | ❌ 容器无 `CAP_SYS_ADMIN`（`CapEff` 只有 chown/dac_override/fowner/…），`/proc/sys/fs/binfmt_misc` 不存在，`unshare` 也不行 |
| Debian 自带 `/usr/bin/aapt2` | ⚠️ 能跑，但版本 **2.19/2.20** 读不了 API 36 的资源表（`RES_TABLE_TYPE_TYPE entry offsets overlap`） |

**当前状态**：本地能编译 Kotlin、能跑 Gradle 配置，**不能**完整构建。**验证仍走 CI**（约 7 分钟一轮）。若以后换 x86_64 机器或拿到 aarch64 原生 aapt2（≥ 对应 AGP 版本），本地构建即可用。。

### 0.1 网络坑（重要，CI 不需要但本机会）

- `dl.google.com` / `repo.maven.apache.org` 单文件可达（curl 200），但 **Gradle 并行解析器会卡死**（10+ 分钟零进展、缓存不增长）。
- 解决：`settings.gradle.kts` 里把 **Aliyun 镜像放最前**（`maven.aliyun.com/repository/google|public|gradle-plugin`，实测 ~1s 响应），官方源保留为回退。这一改动**只在本地有意义**，CI 可以保留（无害）。

## 1. 本机能不能直接出 APK？—— 结论：不能（但能跑单测）

| 检查项 | 结果 | 含义 |
|---|---|---|
| `uname -m` | `aarch64` | 官方 Android SDK 的 **aapt2 / d8 / adb 只发 x86_64** 包；无 aarch64 版 |
| `java` | 无 → 已 `apt install openjdk-17-jdk-headless`（17.0.21） | **JVM 侧可行** |
| `aapt2` / `d8` / `adb` | 均无；`apt policy android-sdk` = 无候选 | 打包链路缺件 |
| `apt policy gradle` | 候选 4.8（太老，AGP 8.x 需 8.x） | 不能直接用 Debian 的 gradle |
| RAM | 3 GB（gradle.properties 要 `-Xmx2560m`） | 勉强够，并行构建会 OOM |

**结论**：本机可以**编译纯 Kotlin（协议层）并跑 JVM 单测**，但**不能**跑 `assembleDebug`（缺 aapt2 与平台包）。所以**打包全部交给 GitHub Actions**，本地只做“逻辑正确性”验证。

## 2. 本地能做什么（开发内循环）

```sh
# 1) 纯 JVM 单测（共 1 116 个 @Test，其中 47 个跑 Robolectric；全部无需真机）
./gradlew :core:protocol:test          # 拆分后

# 2) 静态检查
./gradlew detekt ktlintCheck

# 3) 协议 spike（无 Android 依赖的独立 JVM 程序，见 SPIKE.md）
#    JDK 17 已就绪：用 JDK 自带的 java.net.http.WebSocket + HttpClient 即可，不引入额外依赖
```

> 没有 wrapper 时 `./gradlew` 需联网下载 Gradle 发行版（约 130 MB）；受限网络用 `ALL_PROXY=http://127.0.0.1:7890`。

## 3. GitHub Actions 出包（推荐路径）

fork 已有两条工作流，直接沿用：

| 工作流 | 触发 | 作用 | 要改的 |
|---|---|---|---|
| `.github/workflows/build.yml` | push main / PR | 出 **debug APK** 产物（`dsh-android-debug-<sha>`），30 分钟超时 | artifact 名与应用名；确认 `paths-ignore` 仍排除 `docs/**` |
| `.github/workflows/release.yml` | push tag `v*` | 出 **release APK** 挂到 GitHub Release，正文取 `docs/releases/<tag>.md` | 包名相关文案；**配好签名 secrets** |

关键设计（值得保留）：
- 用 runner 的 JDK/Gradle 复现 `.toolchain/` 布局，再调用项目自己的 `build.sh` —— 本地与 CI 不是两套构建定义
- `concurrency` 按 ref 去重，新 push 取消旧构建
- release 走 `RELEASE_KEYSTORE_BASE64` / `RELEASE_KEYSTORE_PASSWORD` / `RELEASE_KEY_ALIAS` / `RELEASE_KEY_PASSWORD`；缺失时回退 debug key（**但 debug 签名的包无法被正式包覆盖安装**）

### 3.1 建议新增第三条工作流 `verify.yml`

```yaml
name: Verify
on: [push, pull_request]
jobs:
  jvm-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - run: ./gradlew :core:protocol:test :core:model:test --no-daemon
      - uses: actions/upload-artifact@v4
        if: failure()
        with: { name: test-reports, path: '**/build/reports/tests/**' }
```
> 目的：把逻辑层门禁从 30 分钟的 Android 构建里摘出来，PR 反馈从十几分钟降到几分钟。

## 4. 版本与签名纪律

| 项 | 规则 |
|---|---|
| `versionCode` | 从 1 起单调递增、**永不重用**；CI 可用 `git rev-list --count HEAD` 或语义版本映射 |
| `versionName` | `1.0.0` 起步；预发 `1.0.0-beta.1`；debug 包追加 `-debug` |
| 签名 | **首发前生成并固化 keystore**（RSA 4096 / 有效期 25 年以上），私钥进 CI secrets + 加密备份；丢失 = 已发布用户无法升级 |
| 产物命名 | `dsh-android-<versionName>.apk` + 同名 `.sha256` |
| 覆盖安装 | 同包名不同签名无法覆盖；因此 debug 与 release **包名后缀不同**（`.debug`） |

## 5. 分发（自托管场景）

| 方式 | 要求 | 备注 |
|---|---|---|
| GitHub Releases | tag + APK + `docs/releases/<tag>.md` 变更日志 | fork 已内建；Obtainium 可直接订阅 |
| Obtainium | 仓库地址 + release APK **稳定命名** | 建议 APK 名不含随机 sha |
| F-Droid | 可复现构建、源码与许可证齐全 | MPL/GPL 都可；注意 Miuix 依赖与体积 |
| Play 商店 | 生成式 AI 政策 + targetSdk 门槛（2026 年起 ≥35） | 建议 1.0 之后再考虑；当前 targetSdk=34 会被拦 |

## 6. 冒烟验证（拿到 APK 后 5 分钟）

```sh
adb install -r dsh-android-<ver>.apk
adb shell am start -n <pkg>/.MainActivity
adb logcat -s DSH:* AndroidRuntime:E | head -40
# 应用内：填地址 → 登录 → 列会话 → 发一句 → 看流式 → 触发一次审批
```

另保留**无头 JVM 冒烟**（不依赖手机）：
```sh
java -jar tools/protocol-smoke.jar --url https://dsh.osai.eu.org:8443 --session <id> "你好"
```

## 7. 已知坑

| 坑 | 现象 | 处理 |
|---|---|---|
| aarch64 无 aapt2 | `:app:processDebugResources` 找不到 aapt2 | 不在本机打包，走 CI |
| Gradle daemon 连不上 | “Could not connect to Kotlin compile daemon” | 把 `XDG_DATA_HOME` 指到项目内（fork 的 `build.sh` 已处理） |
| Robolectric 要写 home | 单测因锁文件失败 | 单独 `user.home`（fork 已处理：`.toolchain/robolectric-home`） |
| 3 GB RAM | 并行构建 OOM | `org.gradle.parallel=false`、`-Xmx1536m` |
| 代理 | 依赖下载超时 | `ALL_PROXY=http://127.0.0.1:7890` |
