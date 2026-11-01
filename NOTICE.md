# 许可与来源（License and provenance）

本仓库整体以 **GNU General Public License v3.0**（下称 GPL-3.0）发布，全文见 [LICENSE](LICENSE)。

## 为什么从 MPL-2.0 改成 GPL-3.0

这个 fork 的基线 `bqv/dsh-android` 是 MPL-2.0。阶段 2 的外壳换装决定**直接复用**
`chenaizhang/KernelSU-Style-UI-Kit` 的代码（同样是 Kotlin/Compose 的安卓外壳实现），
而那个仓库是 **GPL-3.0**。

GPL-3.0 是强传染许可：把它的代码并入本工程，整份分发物就必须整体按 GPL-3.0 授权。
MPL-2.0 与 GPL-3.0 本身兼容（MPL-2.0 §3.3 允许按 GPL 再授权分发），所以这次转换
**在法理上没有障碍**；代价是本仓库不再是宽松许可，任何分发都必须附带源码。

> 一句话：**想复用 GPL 代码，就只能整仓 GPL。** 这是有意的取舍，不是疏漏。

## 来源清单

| 来源 | 许可 | 用在哪里 |
|---|---|---|
| `bqv/dsh-android` | MPL-2.0 | 本 fork 的起点（协议层、终端、聊天渲染、view model） |
| `chenaizhang/KernelSU-Style-UI-Kit` | GPL-3.0 | 阶段 3 起的外壳与组件层：液态玻璃底栏、`liquid/` 效果、Dialog、SegmentedList、SearchBar、WarningCard、ScrollToTop、动画工具 |
| `compose-miuix-ui/miuix` 0.9.4 | Apache-2.0 | 依赖引入（Maven），不是源码复制 |
| 官方 DSH Web 客户端（编译产物） | 见上游 | 行为对齐的参考，不复制代码 |

## 复用 GPL 代码时的不变量

只要某个文件来自 KernelSU-Style-UI-Kit（或由它改写而来），就必须：

1. **保留原版权头**，并在文件头注明来源仓库与原始路径；
2. 在提交信息里用 `Adapted from` / `Copied from` 说明来源；
3. 不改写许可声明，不把它挪到更宽松的子目录；
4. 若某个文件其实源自 compose-miuix-ui 的 Apache-2.0 示例（例如
   `FloatingBottomBar.kt` 首行就写着 `Adapted from compose-miuix-ui example`），
   就**按 Apache-2.0 保留归属**——上游是 Apache 的部分不因为经过 GPL 仓库中转而变成 GPL。

## 对本仓库自身的义务

- 分发 APK 时**必须提供完整源码**（GPL-3.0 §6）。本仓库转为公开正是为了满足这一条。
- 保留所有原始版权声明与免责声明（§5）。
- 修改过的文件需保留改动说明（§5a）。

## 与 MPL-2.0 的历史关系

2026-11-01 之前合入的提交是在 MPL-2.0 下产出的。MPL-2.0 §3.3 允许把这些代码
按 GPL-3.0 分发，因此**历史提交不需要重写**，也不需要追溯修改它们的文件头。
