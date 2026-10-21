# 严格代码审查记录

审查范围：`c8e05d6..HEAD`（`sta/android17-extension-agents` 分支），重点为 **Root + chroot**
模式下的扩展 Agent 安装/启动/停止，以及 Android 17 IME 修复。

审查方式：

- 逐文件静态审查与调用链追踪（`LinuxPackageProfileInstaller` → `InstallerShellRunner` →
  `ShellProcessSupervisor.buildLinuxPayload` → `DetachedTaskSupervisor.launchRaw`）；
- 针对性单元测试；
- Android 37（API 37）x86_64 模拟器 smoke test；
- 无法在模拟器上获得可用的 `su -c`，因此 chroot/root 真机行为列为残余风险，不伪造测试结论。

## 发现与处理

| 级别 | 发现 | 处理 |
| --- | --- | --- |
| High | 安装/启动/状态查询使用 `LinuxEnvironmentPaths.rootfsDir(context, distribution)` 的默认后端，而该值来自可变的 `LinuxEnvironmentSettingsRepository` 缓存；同时存在 PRoot 与 chroot 环境时，UI 选择的 chroot 可能被错误解析到 PRoot rootfs。 | 已修复：`LinuxPackageProfileInstaller`、`ExtensionAgentLauncher.launch/status/stop`、`AgentAppViewModel` 全部显式传递 `LinuxExecutionBackend`，rootfs 使用 `rootfsDir(context, distribution, backend)`。 |
| High | `AgentAppViewModel.refreshExtensionAgent()` 每次启动独立协程，且用 `extensionAgentJob` 判断状态；启动/停止期间后台刷新可能用旧的 `READY` 覆盖 `STARTING/RUNNING`，切换 Agent 时旧刷新也可能覆盖新 Agent。 | 已修复：增加单独的 `extensionAgentRefreshJob`，刷新前取消旧任务；启动/停止前取消刷新；写入状态前校验 `extensionAgent == agent`。 |
| Medium | chroot 下 Node 启动 OpenCode/ZCode 的冷启动可能超过 20 秒（原 `waitAttempts=40 × 500ms`），在手机上会误报 `URL_TIMEOUT`。 | 已修复：等待提升到 90 次（45 秒）。 |
| Medium | 安装校验使用 `opencode web --help` / `zcode --help`，CLI 的 help 退出码或分页行为可能导致“安装成功但校验失败”。 | 已修复：改为 `command -v <cmd>` + `--version`（ZCode 保留 `--help` 作为可用性探测）。 |
| Medium | `EtaVoicePanel` 仍直接使用 `WindowInsets.ime`，语音面板的输入区同样可能被 Compose insets 冻结影响。 | 已修复：改用 `rememberSafeImeInsets()`，padding 使用 safe insets。 |
| Medium | 项目内仍有 `Modifier.imePadding()` 调用点，未覆盖全部输入场景。 | 已修复：全部替换为 `imePaddingSafe()`；`grep` 确认只剩文档与 `SafeImeInsets` 内部实现引用。 |
| Medium | `ExtensionAgentWebSession` 的 URL 正则过宽，遇到日志中的 `]` 或 `#token=` 时可能截断或带上无关字符，导致浏览器打开错误地址。 | 已修复：收紧到 URL 安全字符集，并覆盖 `?token=` / `#token=`；新增回归断言。 |
| Low | Root 被撤销后，`ExtensionAgentLauncher.status()` 在列出任务前直接返回 `ROOT_REQUIRED`，UI 无法在无 Root 状态下停止已运行的 root 守护任务。 | 已记录为限制：恢复 Root 后可停止；root 守护任务本身不随 App 进程退出。 |
| Low | ZCode 使用 npm 社区包 `zcode-app-cli`，并非 Z.ai 官方 CLI 分发；存在供应链与版本漂移风险。 | 已在选型文档标注；`ZCODE_INSTALL_SCRIPT` 可替换为官方 `install.sh` 或固定版本 tarball。 |
| Low | chroot/root 真机未覆盖：模拟器 `su` 只接受 uid/gid，无法执行 `su -c`。 | 已记录残余风险；建议在 Root 设备（Magisk/KernelSU）上按 `docs/EXTENSION_AGENTS.md` 的步骤回归。 |

## chroot/root 调用链核对

1. `LinuxEnvironmentScreen` 当前 `backend` → `LinuxPackageProfileInstaller(backend)`：
   rootfs 固定到 `filesDir/terminal/<dist>/rootfs`。
2. 安装命令经 `InstallerShellRunner` → `ShellProcessSupervisor.startShellProcess`，
   `TerminalRuntime.defaultIdentity(environment, rootfs)` 对 chroot 返回 `root`。
3. `ExtensionAgentLauncher.launch` 对 chroot 不申请 `AgentExecutionService` 租约，
   直接调用 `DetachedTaskSupervisor.start(identity=root)`。
4. `launchRaw` 用 `su -c <buildLinuxPayload>`：`unshare -m --propagation private` +
   `chroot`，内层 `PATH` 包含 `/usr/local/bin`，`cwd=/workspace` 是
   `/data/local/tmp/eta` 的 bind mount。
5. 状态/停止按 `environment + identity + backend + command` 精确匹配，避免误操作 PRoot 任务。

## IME 修复审查

- `SafeImeInsets` 在正常状态继续使用 `WindowInsets.ime`，不会破坏系统逐帧同步；
- 64ms 未更新且与 `imeAnimationTarget` 不一致才判定冻结，健康动画每帧都会重置计时；
- 冻结后本地 240ms 动画追向 target，恢复上升/下降；判定后不再信任旧动画值，避免下次弹键盘瞬跳；
- 共享实例按 `View` 弱引用缓存，避免 Activity 泄漏；`imePaddingSafe()` 消费语义与
  `imePadding()` 一致；
- 残余风险：低帧率设备上 64ms 可能误判，但回退动画仍会落到正确 target，不会产生错误 padding。

## 测试结果

- 针对性测试通过：
  `ExtensionAgentWebSessionTest`、`ExtensionAgentTest`、`SafeImeInsetsTest`、
  `AlpineEnvironmentInstallerTest`、`ShellProcessSupervisorTest.linuxPayload...`。
- `:app:compileDebugKotlin`、`:app:assembleDebug`、`:app:assembleRelease`、`:app:lintDebug`
  均通过。
- 完整 `testDebugUnitTest` 中部分既有 Shell/PRoot 测试在 Windows 宿主失败（例如
  `sh -n` 语法检查、PTY/文件权限），与本次改动无关；新增/修改测试均通过。
