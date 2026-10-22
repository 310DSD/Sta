# Sta fork 交付报告

## 交付物

| 文件 | 说明 |
| --- | --- |
| `dist/Eta-v3.3.1-sta-release.apk` | 已签名的 release APK（targetSdk 37，11 MB） |
| `dist/Eta-v3.3.1-sta-debug.apk` | 可调试的 debug APK（86 MB） |
| `docs/ANDROID_17_IME_FIX.md` | Android 17 输入法动画问题根因与修复说明 |
| `docs/EXTENSION_AGENTS.md` | OpenCode 2 / ZCode / DeepSeek Harness 评估（ZCode 实现阶段落选） |
| `docs/CODE_REVIEW.md` | 严格代码审查记录（含 chroot/root 调用链） |
| 分支 `sta/android17-extension-agents` | 基于 `Mangi-11/Eta` main（c8e05d6）的 fork 提交 |

release APK 使用本地生成的 `CN=Sta Fork` 证书签名；如需上架或长期分发，请替换为自己的正式密钥。

## 需求 1：修复 Android 17 输入框/输入法动画丢失

### 根因

Eta 3.3.1 使用 Compose `foundation-layout 1.12.0`。该版本的 IME insets 监听器存在
Google IssueTracker **b/552500419**：IME 动画在 `onStart()` 之后被取消时不会收到
`onEnd()`，内部 `runningAnimation` 会一直保持 `true`（直到下一次成功动画的 `onEnd` 或 holder 重置），期间 `WindowInsets.ime` 冻结。
Android 17 的预测性返回手势与键盘收起动画竞争时容易触发。冻结后 `imePadding()` 不再跟随
键盘，表现为输入框上升/下降动画丢失或停在错误高度。

### 修复

新增 `SafeImeInsets.kt`：

- 正常状态继续使用 `WindowInsets.ime`，保持系统逐帧同步；
- 连续两个 64ms 窗口（128ms）内动画值与 `WindowInsets.imeAnimationTarget` 不一致且没有新帧时，判定为冻结；
- 使用本地 240ms 动画追向 `imeAnimationTarget`，恢复上升/下降动画；
- 冻结判定后本窗口生命周期内不再信任动画值；
- `imePaddingSafe()` 已替换聊天输入栏及项目内其他 `imePadding()` 调用；
- `rememberImeVisible()` 改用 `imeAnimationTarget`，避免建议词等 UI 受冻结值影响。

### 验证

- `SafeImeInsetsTest` 覆盖健康、冻结回退、非 bottom 边使用 target。
- `SafeImeInsetsWatchTest` 用虚拟时间覆盖健康帧取消、双窗口确认冻结、冻结后恢复。
- Android 37（API 37）x86_64 模拟器上安装 release APK，反复切换键盘显示/隐藏、
  用返回手势取消动画，输入栏均回到 `y=2052` 的关闭位置，无残留空白或卡死。
- 说明：模拟器无法稳定复现特定厂商 ROM 上的取消时序，建议在 Xiaomi 17 / HyperOS 4
  或 ColorOS 17 真机上再跑一轮回归。

## 需求 2：用两个扩展 Agent 替换 Kimi Code

### 选型

选择 **OpenCode 2.1.2** 与 **DeepSeek Harness 0.2.1-rc.2**，ZCode 在实现阶段落选；**Kimi Code 保留为第三个扩展 Agent**。
关键数据：

| 指标 | OpenCode | ZCode | DeepSeek Harness |
| --- | --- | --- | --- |
| 官方 ARM64 分发 | ✅ npm 单二进制（含 musl） | ❌ 官方 CLI Web 需源码自建/自托管；npm 社区包仅 TUI | ✅ npm |
| 实测安装体积（ARM64） | 209.5 MB 包 | 123 MB（仅 TUI，已淘汰） | **787 MB / 610 个包** |
| Web 入口 | `opencode service start` + `opencode pair` | 社区包不支持 `--web` | `dsh web`，直接打印带 token URL |
| 成熟度 | 2.x 稳定线 | 3.16.x 稳定产品 | 开发者预览，明确破坏兼容 |

ZCode 的社区 npm 包实际执行 `zcode --web` 会报 `Unknown option '--web'`，无法作为 Web 扩展
Agent；DeepSeek Harness 虽然依赖体积大（611 个包）、属于开发者预览，但其官方 npm 包和
`dsh web` 是当前三个候选中唯一可直接安装并提供 Web UI 的第二个选择，因此改选 DSH。

### 实现

- `LinuxPackageProfiles` 新增 `OPENCODE` 与 `DSH` 安装 profile（依赖 Node.js，
  npm 国内镜像优先、官方 registry 兜底），固定 OpenCode 2.1.2 / DSH 0.2.1-rc.2，
  使用 `--include=optional` 保留 koffi/node-pty 的 ARM64 预编译依赖；Kimi Code profile 保留。
- 安装、启动、状态、停止均显式传入当前 `LinuxExecutionBackend`，root/chroot 固定使用
  `filesDir/terminal/<dist>/rootfs`，不会因缓存后端误用 PRoot 目录。
- 新增 `ExtensionAgent` 目录、`ExtensionAgentWebSession`、`ExtensionAgentLauncher`、
  `ExtensionAgentUiState`，统一安装、启动、复用、停止与 URL 解析；chroot/root 下
  由 `su -c` + `unshare -m` + `chroot` 启动，`/workspace` 为可写 bind mount。
- 首页顶栏溢出菜单的 Kimi Web 改为当前默认扩展 Agent 的启动/打开/停止入口。
- Linux 工具环境页用 OpenCode、DeepSeek Harness 两行替换 Kimi Code 行，支持安装、启动 Web、
  停止；最近一次成功启动的 Agent 自动成为顶栏默认入口。
- 默认 Agent 持久化在 `SettingsDataStore` 的 `extension_agent` 键。
- 扩展 Agent 的 Web UI 改由内置 `ExtensionAgentWebActivity` 承载：移动端 Enter 插入换行，
  支持文件选择、返回栈和外部链接转系统浏览器；顶栏溢出菜单同时提供 OpenCode / DSH 两个入口。
- 原生对话的用户消息也走 Markdown；新增常见 LaTeX 希腊字母、运算/集合/箭头符号归一化。
- Kimi Code 原 profile、Linux 工具行、顶栏入口和停止入口一并恢复/保留。
- DSH 移动端改为独立壳层，并按 Eta 的视觉语言重做：暖灰画布 + 白色卡片、16–24px 圆角与 hairline 描边；桌面 rail 改为固定顶栏 + 左侧抽屉，聊天列占满视口，设置页改为全屏 sheet（横向分类 chip、设置行单卡化、说明文字不再被挤成竖排）。工作区/模式改为 chip，选中会话带 3px 强调条，代码块与行内代码有独立表面，主题切换对画布/卡片/边框做 0.3s 颜色过渡，并补 `:focus-visible` 焦点环。动画只用 transform+opacity：抽屉 0.34s 缓出、入口分段入场、sheet 上滑、菜单缩放淡入、仅最后一条消息上浮；`prefers-reduced-motion` 下全部关闭。性能上滚动容器加 `overscroll-behavior: contain`，抽屉 `translate3d`+`contain`，消息项 `contain: layout style`，不使用 `transition: all`，也不对大子树做入场动画。样式块带 `eta-mobile-start/end` 标记，安装脚本可重复清理并追加，旧版 CSS 不会残留。
- 原生 Markdown 分式改为“分子 / 横向分数线 / 分母”的居中布局，分数线按最宽文本测量，并修正 frac、dfrac、big、lim 等命令的归一化。
- OpenCode 上游 2.1.2 的“自定义提供商”表单固定报 `provider.custom.unavailable`；Eta 改为从自身 provider/model 数据直接同步 `opencode.json`，映射上下文、视觉、工具、思考等级和自定义请求头/体，并执行 `opencode reload`。同步会先从 provider 的 `/models` 补全工具/视觉/上下文/输出上限，再写入 `limit.context`、`limit.output`、`capabilities.tools`、`capabilities.input` 和 `capabilities.reasoning`；同时保留已存在 provider 的手工 baseURL、API key、headers/body。
- 修复 DSH 移动 CSS 的 `#root` 规则泄漏到 OpenCode 的问题：CSS 仅在检测到 DSH `.pI_x6G_frame` 后注入，并在选择器上用 `:has` 限定 DSH，OpenCode 不再多出 56px 顶部空白。
- 扩展 Agent WebView 标签栏改用 Compose/Miuix 主题；Enter 捕获改为 window capture，OpenCode 用 `insertLineBreak`，DSH Lexical 用 `beforeinput(insertLineBreak)`，均能换行而不发送。
- OpenCode 原生 titlebar 在 Android WebView 中会重复读取 safe-area inset，导致顶部约 60px 空白；已把移动端 titlebar 固定为 36px，连接页/首页/会话页统一从顶部开始。
- 扩展 Agent 已运行时点击右上角启动/打开走快速路径：只查一次状态和日志 URL，直接打开 WebView，不再重复执行启动检查、日志轮询和配对。
- WebView 新建页面弹窗改为 Miuix 样式，提供 OpenCode / DeepSeek Harness / Kimi Code / 文件管理器快速入口，并保留自定义 loopback 地址输入。
- 新增 MT 管理器风格的 Linux 文件管理器：快捷入口、路径跳转、搜索、排序、隐藏文件、多选、复制/移动/重命名/属性/删除、跨目录剪贴板、文本查看与编辑；操作优先走 root shell / PRoot，shell 不可用时回退到 App 直接访问 rootfs。
- 修复 DSH 顶部空白：不再在 CSS 中重复叠加 `env(safe-area-inset-top)`，且 `#root` 只在 `#eta-mobile-bar` 实际插入后才让出高度。
- 更新中英文 README 与三语字符串。

### 验证

- `ExtensionAgentWebSessionTest`：URL 提取、复用已有任务、超时回收、浏览器不可用。
- `ExtensionAgentTest`：目录只包含 OpenCode/DeepSeek Harness、未知 id 回退、固定 loopback 端口，
  并验证 chroot rootfs 使用 `root` 身份、PRoot rootfs 使用 `user` 身份。
- `AlpineEnvironmentInstallerTest`：两个 profile 的 npm 安装脚本、依赖与镜像参数。
- `ShellProcessSupervisorTest.linuxPayload...`：chroot payload 包含 `unshare + chroot`、
  `/usr/local/bin` PATH 和 `/workspace` bind mount。
- 代码审查发现并修复了后端/rootfs 串用、ViewModel 刷新竞态、启动超时过短等问题，
  详见 [docs/CODE_REVIEW.md](docs/CODE_REVIEW.md)。
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
  --tests "io.github.mangi.eta.ui.insets.SafeImeInsetsWatchTest" \
  --tests "io.github.mangi.eta.agent.extension.ExtensionAgentTest" \
  --tests "io.github.mangi.eta.agent.terminal.AlpineEnvironmentInstallerTest"
```

构建结果：`:app:assembleDebug`、`:app:assembleRelease`、`:app:lintDebug` 均通过。
完整 `testDebugUnitTest` 中部分既有 Shell/PRoot 测试在 Windows 宿主上失败（与本次改动无关），
本次新增/修改的针对性测试全部通过。

## 已知风险与后续

1. DeepSeek Harness 是开发者预览，ARM64 约 787 MB / 610 个包；升级可能破坏兼容，安装耗时长。
   已固定版本并启用 optional 预编译依赖；若镜像缺少 koffi/node-pty ARM64 预编译包，
   安装会尝试源码编译而失败。ZCode 若要启用 Web，必须自建并托管官方 CLI tarball。
2. 扩展 Agent 的 Web UI 已统一由 Eta 内置 `ExtensionAgentWebActivity` 承载（多标签、状态栏/IME inset、Enter→换行）；DSH 额外使用移动壳层，OpenCode 的自定义提供商改为从 Eta 的 provider/model 数据同步（上游 2.1.2 Web 表单固定报 `provider.custom.unavailable`）。
3. Android 17 的 IME 修复依赖 `imeAnimationTarget`（Compose 实验 API），
   已在 1.12.0 上验证；升级 Compose 后应保留 `SafeImeInsetsTest` 作为看门测试。
