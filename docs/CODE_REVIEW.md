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
| Medium | chroot 下 Node 启动 OpenCode/DeepSeek Harness 的冷启动可能超过 20 秒（原 `waitAttempts=40 × 500ms`），在手机上会误报 `URL_TIMEOUT`。 | 已修复：等待提升到 60 次 × 1 秒（60 秒），同时减少状态探测次数。 |
| Medium | 安装校验使用 `opencode web --help` / `zcode --help`（旧方案），CLI 的 help 退出码或分页行为可能导致“安装成功但校验失败”。 | 已修复：改为 `command -v <cmd>` + `--version`（新方案统一为 `command -v` + `--version`）。 |
| Medium | `EtaVoicePanel` 仍直接使用 `WindowInsets.ime`，语音面板的输入区同样可能被 Compose insets 冻结影响。 | 已修复：改用 `rememberSafeImeInsets()`，padding 使用 safe insets。 |
| Medium | 项目内仍有 `Modifier.imePadding()` 调用点，未覆盖全部输入场景。 | 已修复：全部替换为 `imePaddingSafe()`；`grep` 确认只剩文档与 `SafeImeInsets` 内部实现引用。 |
| Medium | `ExtensionAgentWebSession` 的 URL 正则过宽，遇到日志中的 `]` 或 `#token=` 时可能截断或带上无关字符，导致浏览器打开错误地址。 | 已修复：收紧到 URL 安全字符集，并覆盖 `?token=` / `#token=`；新增回归断言。 |
| High | 运行中的 Agent 使用固定 loopback 端口；切换发行版/后端后旧守护任务仍占用端口，但状态过滤按 `environment+backend` 精确匹配，UI 看不到也停不掉它。 | 已修复主要路径：`runningAgents` 非空时禁用发行版/后端切换；跨后端遗留守护任务仍需后续统一探测。 |
| High | chroot 安装走裸 `rememberCoroutineScope().launch`，离开 Linux 页面会取消 30 分钟、约 200 MB 的 npm 安装，留下半安装 rootfs。 | 已修复：chroot/PRoot 一律使用 `EtaApp.applicationScope.launchForegroundExecution`，前台服务租约 + Application 作用域，离开页面不会取消安装。 |
| Medium | `ExtensionAgentLauncher.launch` 只检查 rootfs 就绪，不检查 Agent profile 是否安装；从 FAILED 状态重试可能启动一个立刻退出的进程并误报 `AGENT_EXITED`。 | 已修复：launch 前检查 `linuxPackageProfileReady`，返回 `PROFILE_NOT_INSTALLED`。 |
| Medium | `ConsoleScreen` 与 `AgentMemoryScreen` 仍使用 `WindowInsets.isImeVisible`；该值同样在 Compose insets 冻结时不再更新。 | 已修复：统一改用 `rememberImeVisible()`。 |
| Medium | `SafeImeInsets` 的看门狗逻辑没有任何针对性测试，只有 pass-through 断言。 | 已修复：抽出 `watchForStrandedIme`，新增 `SafeImeInsetsWatchTest`，覆盖健康帧取消、双窗口确认、冻结后恢复。 |
| Medium | 当前 Agent Web UI 只监听 loopback 且无认证 token；同设备其他应用可访问，chroot/root 下可读写工作区。 | 已记录为安全限制；建议后续加 per-launch token 或 Unix socket，不应把 127.0.0.1 当作安全边界。 |
| Medium | `DetachedTaskSupervisor` 的 `linuxRootfsPathProvider` 仍通过仓库缓存解析 rootfs，与 launcher 的显式 backend 存在两套解析来源；缓存冷/切换瞬间可能不一致。 | 已记录：`EtaApp` 启动时初始化缓存且 launcher 显式路径已覆盖主路径；后续可把 backend 直接传入 supervisor。 |
| Low | PRoot/user 身份在 launch 返回后释放前台租约，守护任务要到下一次 `list()` 才被重新认领，立刻切后台可能被系统杀掉。 | 已记录；chroot/root 主路径不受影响。 |
| Low | Root 被撤销后，`ExtensionAgentLauncher.status()` 在列出任务前直接返回 `ROOT_REQUIRED`，UI 无法在无 Root 状态下停止已运行的 root 守护任务。 | 已记录为限制：恢复 Root 后可停止；root 守护任务本身不随 App 进程退出。 |
| High | IME 恢复分支在 `animated` 仅变化一次后立即解除看门狗；若该帧仍不等于 target 且随后无新帧，下一次键盘切换会重新冻结。 | 已修复：恢复帧若不等于 target 重新等待两个 64ms 窗口；新增 `rearmsTheWatchdog` 虚拟时间测试。 |
| Medium | `rememberImeVisible()` 读取 target，target 在关闭动画开始时即变 0，建议/记忆设置会在键盘尚未降下时提前出现。 | 新增 `rememberImeVisibleSafe()` 读取安全 inset，并替换 AgentChatBody/AgentMemory/Console 的调用。 |
| Medium | `EtaVoicePanel` 根与 `AssistantPanel` 各自启动 watcher，共享 fallback 但各自持有 Animatable，可能交错写入。 | `AssistantPanel` 改为接收根部同一 `safeImeInsets`；晚 watcher 先 snap 到共享值再动画。 |
| Low | watcher 在密度变化重启时可能先冻结 128ms。 | `watchForStrandedIme` 增加 `resumeStranded`，重启时立即恢复回退动画。 |
| Low | IME 测试缺少“恢复帧仍不匹配再冻结”“双窗口中途新帧重置”“重启立即恢复”等场景。 | 已补充 `SafeImeInsetsWatchTest` 对应用例。 |
| High | DSH 的 npm 依赖包含 `koffi` / `node-pty` 原生模块；若国内镜像缺少 optional 预编译包，npm 会尝试源码编译，而基础 rootfs 没有 build-essential/cmake，安装会失败。 | 已改为 `--include=optional` 并固定 `@deepseek-ai/dsh@0.2.1-rc.2`；文档记录 ARM64 预编译依赖与回退风险，仍需真机安装验证。 |
| High | OpenCode 2.x 的 `opencode web` 不是有效子命令；若 `opencode pair` 失败却仍打开 `serve` 根地址，用户会看到密码/401 页面。 | 已改为共享服务 `opencode service start` + `opencode pair`；`pairRequired` 时拿不到登录链接直接返回 `PAIRING_FAILED` 并回收本次任务。 |
| Medium | `AGENT_EXITED` 只显示“进程已退出”，没有后台日志，无法定位 CLI 参数/依赖错误。 | `ExtensionAgentLaunchResult.Failed` 增加 `detail`，读取最后 800 字符日志并显示最后 6 行；新增 pair 失败回归测试。 |
| Low | DSH 体积/版本漂移：`@latest` 可能升级到破坏 CLI 参数的预览版。 | 固定 `@deepseek-ai/dsh@0.2.1-rc.2`；UI 摘要标注 ARM64 约 787 MB。 |
| Medium | ZCode 的官方 Web CLI 只在源码构建/自托管分发中提供；npm 社区包 `zcode-app-cli` 实际不支持 `--web`，原方案启动即退出。 | 已在实现阶段淘汰 ZCode，改用官方 npm `@deepseek-ai/dsh` + `dsh web`；选型文档记录原因。 |
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
- 连续两个 64ms 窗口（128ms）未更新且与 `imeAnimationTarget` 不一致才判定冻结，健康动画每帧都会重置计时；
- 冻结后本地 240ms 动画追向 target，恢复上升/下降；如果 `animated` 之后再次变化，说明 Compose 监听器已恢复，立即回到系统同步路径；
- 共享实例按 `View` 弱引用缓存，避免 Activity 泄漏；`imePaddingSafe()` 消费语义与
  `imePadding()` 一致；
- 残余风险：低帧率设备上双窗口仍可能误判，但回退动画仍会落到正确 target，不会产生错误 padding。

## 测试结果

- 针对性测试通过：
  `ExtensionAgentWebSessionTest`（含 pair 失败/日志摘要）、`ExtensionAgentTest`、
  `SafeImeInsetsTest`、`SafeImeInsetsWatchTest`（含新恢复/重启场景）、`AlpineEnvironmentInstallerTest`、
  `ShellProcessSupervisorTest.linuxPayload...`。
- `:app:compileDebugKotlin`、`:app:assembleDebug`、`:app:assembleRelease`、`:app:lintDebug`
  均通过。
- 完整 `testDebugUnitTest` 中部分既有 Shell/PRoot 测试在 Windows 宿主失败（例如
  `sh -n` 语法检查、PTY/文件权限），与本次改动无关；新增/修改测试均通过。
