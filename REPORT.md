# Sta fork 交付报告

## 交付物

| 文件 | 说明 |
| --- | --- |
| `dist/Eta-v3.3.1-sta-release.apk` | 已签名的 release APK（targetSdk 37，11 MB） |
| `dist/Eta-v3.3.1-sta-debug.apk` | 可调试的 debug APK（86 MB） |
| `docs/ANDROID_17_IME_FIX.md` | Android 17 输入法动画问题根因与修复说明 |
| `docs/EXTENSION_AGENTS.md` | OpenCode 2 / ZCode / DeepSeek Harness 选型报告 |
| 分支 `sta/android17-extension-agents` | 基于 `Mangi-11/Eta` main（c8e05d6）的 fork 提交 |

release APK 使用本地生成的 `CN=Sta Fork` 证书签名；如需上架或长期分发，请替换为自己的正式密钥。

## 需求 1：修复 Android 17 输入框/输入法动画丢失

### 根因

Eta 3.3.1 使用 Compose `foundation-layout 1.12.0`。该版本的 IME insets 监听器存在
Google IssueTracker **b/552500419**：IME 动画在 `onStart()` 之后被取消时不会收到
`onEnd()`，内部 `runningAnimation` 永久为 `true`，导致 `WindowInsets.ime` 冻结。
Android 17 的预测性返回手势与键盘收起动画竞争时容易触发。冻结后 `imePadding()` 不再跟随
键盘，表现为输入框上升/下降动画丢失或停在错误高度。

### 修复

新增 `SafeImeInsets.kt`：

- 正常状态继续使用 `WindowInsets.ime`，保持系统逐帧同步；
- 64ms 内动画值与 `WindowInsets.imeAnimationTarget` 不一致且没有新帧时，判定为冻结；
- 使用本地 240ms 动画追向 `imeAnimationTarget`，恢复上升/下降动画；
- 冻结判定后本窗口生命周期内不再信任动画值；
- `imePaddingSafe()` 已替换聊天输入栏及项目内其他 `imePadding()` 调用；
- `rememberImeVisible()` 改用 `imeAnimationTarget`，避免建议词等 UI 受冻结值影响。

### 验证

- `SafeImeInsetsTest` 覆盖健康、冻结回退、非 bottom 边使用 target。
- Android 37（API 37）x86_64 模拟器上安装 release APK，反复切换键盘显示/隐藏、
  用返回手势取消动画，输入栏均回到 `y=2052` 的关闭位置，无残留空白或卡死。
- 说明：模拟器无法稳定复现特定厂商 ROM 上的取消时序，建议在 Xiaomi 17 / HyperOS 4
  或 ColorOS 17 真机上再跑一轮回归。

## 需求 2：用两个扩展 Agent 替换 Kimi Code

### 选型

选择 **OpenCode 2.1.2** 与 **ZCode 3.16.2**，DeepSeek Harness 落选。关键数据：

| 指标 | OpenCode | ZCode | DeepSeek Harness |
| --- | --- | --- | --- |
| 官方 ARM64 分发 | ✅ npm 单二进制（含 musl） | ⚠️ CLI 需自建/自托管，采用社区 npm 包 | ✅ npm |
| 实测安装体积（x64） | 418 MB | 135 MB | **827 MB / 611 个包** |
| 移动端 Web UI | 官方 Web UI | 官方 Web UI + 手机远程控制 | 以桌面为主 |
| 成熟度 | 2.x 稳定线 | 3.16.x 稳定产品 | 开发者预览，明确破坏兼容 |

DeepSeek Harness 的依赖体量（611 个包）与桌面优先的 Web UI 不适合 PRoot 手机环境，
因此排除；这与 Eta 作者 3.0.0 发布说明中的判断一致。

### 实现

- `LinuxPackageProfiles` 新增 `OPENCODE` 与 `ZCODE` 安装 profile（依赖 Node.js，
  npm 国内镜像优先、官方 registry 兜底），删除 `KIMI` profile。
- 新增 `ExtensionAgent` 目录、`ExtensionAgentWebSession`、`ExtensionAgentLauncher`、
  `ExtensionAgentUiState`，统一安装、启动、复用、停止与 URL 解析。
- 首页顶栏溢出菜单的 Kimi Web 改为当前默认扩展 Agent 的启动/打开/停止入口。
- Linux 工具环境页用 OpenCode、ZCode 两行替换 Kimi Code 行，支持安装、启动 Web、
  停止与设为默认。
- 默认 Agent 持久化在 `SettingsDataStore` 的 `extension_agent` 键。
- 更新中英文 README 与三语字符串。

### 验证

- `ExtensionAgentWebSessionTest`：URL 提取、复用已有任务、超时回收、浏览器不可用。
- `ExtensionAgentTest`：目录只包含 OpenCode/ZCode、未知 id 回退、固定 loopback 端口。
- `AlpineEnvironmentInstallerTest`：两个 profile 的 npm 安装脚本、依赖与镜像参数。
- Android 37 模拟器安装 release APK 可正常启动、打开键盘、切换页面，无崩溃。

## 构建

```bash
# 需要 JDK 25 与 Android SDK Platform 37
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease   # 需要 ETA_RELEASE_* 签名环境变量

# 本次实际使用的针对性测试
./gradlew :app:testDebugUnitTest \
  --tests "io.github.mangi.eta.ui.app.ExtensionAgentWebSessionTest" \
  --tests "io.github.mangi.eta.ui.insets.SafeImeInsetsTest" \
  --tests "io.github.mangi.eta.agent.extension.ExtensionAgentTest" \
  --tests "io.github.mangi.eta.agent.terminal.AlpineEnvironmentInstallerTest"
```

构建结果：`:app:assembleDebug`、`:app:assembleRelease`、`:app:lintDebug` 均通过。
完整 `testDebugUnitTest` 中部分既有 Shell/PRoot 测试在 Windows 宿主上失败（与本次改动无关），
本次新增/修改的针对性测试全部通过。

## 已知风险与后续

1. ZCode 目前通过 npm 社区包 `zcode-app-cli` 安装；官方 CLI 需要自建/自托管分发。
   后续可将 `ZCODE_INSTALL_SCRIPT` 指向官方 `install.sh` 或固定版本的 tarball。
2. 扩展 Agent 的 Web UI 仍由系统浏览器打开，未做 Eta 内置 WebView 容器。
3. Android 17 的 IME 修复依赖 `imeAnimationTarget`（Compose 实验 API），
   已在 1.12.0 上验证；升级 Compose 后应保留 `SafeImeInsetsTest` 作为看门测试。
