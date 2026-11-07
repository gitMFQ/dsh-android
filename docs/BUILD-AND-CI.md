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

### 0.0b 2026-10-31 更新：`:app:compileDebugKotlin` 本机**可以**跑了

`model/` 拆成 `:core:model`（PR #6）时需要一次把跨模块 smart cast 全找出来，为此试通了本机的 Kotlin 编译。**结论：Kotlin 编译这一步不依赖 aapt2，只要 build-tools 能解析就能跑。**

两个前提（都是本机环境问题，与仓库无关）：

| 问题 | 现象 | 本机临时绕过 |
|---|---|---|
| `platforms/android-37/source.properties` 里 `AndroidVersion.ApiLevel=37.0` 是小数 | `Failed to find target with hash string 'android-37.0'` | 改成 `37`（**仅本机**，勿提交） |
| `build-tools 37.0.0` 未安装，且无 `sdkmanager` | `Failed to find Build Tools revision 37.0.0` | **五处** `buildToolsVersion` 临时改 `36.0.0`（`app` / `core:model` / `core:config` / `core:protocol` / `design` —— 原文的「两处」是只有 2 个模块时的数字，已过时）。**验证完必须还原**：`git diff` 里冒出 `buildToolsVersion` 就是忘了 |

改完后：

```sh
bash build.sh :app:compileDebugKotlin    # ✅ 约 13 分钟（3 GB 内存，无并行余地）
bash build.sh :core:model:testDebugUnitTest
# ❌ processDebugUnitTestResources 仍挂在 aapt2：
#    AAPT2 ... 1: Syntax error: word unexpected (expecting ")")
```

**意义**：抽模块 / 改 UI 时，**编译错误可以在本机一轮内收干净**，不必用 CI 的 7 分钟去试。测试与资源打包仍只能走 CI。

> **2026-11-01 补充（阶段 2 实测）**：除了 `compileDebugKotlin`，本机还能编译 **测试源码**吗？**不能**——`:app:compileDebugUnitTestKotlin` 依赖 `bundleDebugClassesToCompileJar`，而它要 `processDebugResources` 产出的 `R.jar`，于是又撞上 aapt2。`:design` 的测试源码可以单独编译（`:design:compileDebugUnitTestKotlin`），`:app` 的不行。
>
> 更要紧的是：**本机能跑 `:design` 的单元测试**（`bash build.sh :design:testDebugUnitTest`，实测 2 分钟、8/8 绿）—— `:design` 是 Android library 但它的测试不碰资源打包时就能跑通，这一点与 `:core:model` 不同（后者挂在 `processDebugUnitTestResources`）。**新写的纯函数逻辑尽量放进 `:design` 或 `:core:term`，本机就能验。**

> **2026-11-01 补充（2.4 实测）：`:design` 的测试现在跑在 JDK 21 上，而且要临时改 build-tools。**
>
> 2.4 把 miuix 接进 `:design`（`MiuixPalette.kt` 要返回 miuix 的 `Colors`），于是 `:design` 的测试类路径上也出现了 **Java 21 字节码**。两点实测：
>
> 1. **`JAVA_HOME` 必须是 21**（`/usr/lib/jvm/java-21-openjdk-arm64`），17 会 `UnsupportedClassVersionError`。`build.sh` 里写死的是 17，所以不能直接用它跑 `:design` 测试。
> 2. **`buildToolsVersion` 要临时改成 36.0.0**（本机只有 35/36），且**五处都要改**；验证完必须还原，`git diff` 里冒出 `buildToolsVersion` 就是忘了。
>
> 这两步已经脚本化成 **`tools/localbuild.sh`**（**已提交进仓库**，不是本地私货）：
>
> ```sh
> tools/localbuild.sh <worktree 目录> <gradle 任务…>
> tools/localbuild.sh .work/fb :design:testDebugUnitTest
> tools/localbuild.sh . :app:compileDebugKotlin
> ```
>
> 它设好 21 与 SDK 变量、临时改五处 build-tools 并用 `trap` 还原、再抢全局 `.build.lock`。两处值得知道的实现细节：
>
> - **`ROOT` 用 `git rev-parse --git-common-dir` 求主工作区**，不是脚本自己的目录。`.toolchain/`（Gradle、android-home、xdg-data）和 `.build.lock` 都被 gitignore，只存在于主工作区；git worktree 里没有它们，所以脚本要从任意 worktree 借到同一份。写错这一行会让脚本在 worktree 里报 `no gradle at …` —— 实测踩过。
> - **还原靠 `trap`**，正常退出、Ctrl-C、`set -e` 触发都还原。跑完 `git status` 应当干净。
>
> 一轮 `:design:testDebugUnitTest` 实测 **2.5–3 分钟**（含配置），比 CI 一轮 7 分钟省一半，且能直接看到断言消息。**`:app` 的测试源码仍然编不了**（aapt2），所以新增的 `:app` 测试（`SystemBarsTest`、基线测试）只能由 CI 验证 —— 这一点没变。
>
> `build.sh` 里是 `--no-daemon`，13 分钟是冷启动。同样的验证在 CI 上是 7 分钟全流程，所以**只在本机能省下往返时才用**（例如要迭代编译器报错）。

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
- debug APK 用仓库内固定的 `app/debug.keystore` 签名（§4.1），所以**同一台手机上装新 build 直接覆盖**，不必卸载 —— 这也是用户要的「不用每次卸载重装」

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
| 签名（release） | **首发前生成并固化 keystore**（RSA 4096 / 有效期 25 年以上），私钥进 CI secrets + 加密备份；丢失 = 已发布用户无法升级 |
| 签名（debug） | **仓库内固定**：`app/debug.keystore`（`androiddebugkey` / `android`，RSA 2048，10000 天）**刻意提交**，`app/build.gradle.kts` 里 `signingConfigs.debug` 恒指向它 |
| 产物命名 | `dsh-android-<versionName>.apk` + 同名 `.sha256` |
| 覆盖安装 | 同包名不同签名无法覆盖；因此 debug 与 release **包名后缀不同**（`.debug`），且 debug 每次构建签名必须相同 |

### 4.1 为什么 debug 的 key 要提交进仓库

用户报的问题原话是「每次安装都要卸载重装」。根因不是包名，是**签名每次都变**：

1. `debug` buildType 原先没有 `signingConfig`，AGP 于是用 `$ANDROID_USER_HOME/debug.keystore`，缺了就**当场生成一把随机新 key**；
2. `build.sh` / CI 把 `ANDROID_USER_HOME` 指到 `.toolchain/android-home`，而 `.toolchain/` 被 gitignore；
3. 于是每个 CI job（以及每次清理过工具链的本地构建）都是一把全新的 key → 签名不同 → `adb install -r` 报
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package uk.xa0.dsh.debug signatures do not match previously installed version`。

现在 `app/debug.keystore` 随仓库走，`signingConfigs.debug` 无条件指向它，所以无论本机还是 CI、无论 `.toolchain/` 是否新建，debug 包的签名恒定，覆盖安装成立（`.github/workflows/build.yml` 的 Summary 那句话从此才真的成立）。

**边界，必须守住**：

- 这把 key 是 Android 公开的 debug 身份，密码就是 `android`，进了 git 历史就等于全世界都有。**绝不可用于正式发布**，也不要用它签任何要分发的东西。
- release 仍走 `SIGNING_STORE_FILE` 等 4 个环境变量（`release.yml` 的 `RELEASE_KEYSTORE_BASE64` 一族）。**没有**这些变量时，`buildTypes.release` 现在**显式**回退到这把 debug key（以前是 AGP 隐式回退它自己的默认 debug key）——语义没变：仍产出可安装物，但**debug 签名的 release 包无法被正式包覆盖安装**，只适合自测。
- 因此 `.gitignore` 里 `*.keystore` 之后有一条 `!app/debug.keystore` 例外，带注释说明原因。新增任何**别的** keystore 都不需要开例外——它们必须留在仓库外。

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
