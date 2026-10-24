# Sta fork 交付报告

## 交付物

| 文件 | 说明 |
| --- | --- |
| `dist/Eta-v3.3.3-sta-release.apk` | 已签名的 release APK（targetSdk 37，10 MB，ARM64） |
| `dist/Eta-v3.3.3-sta-debug.apk` | 可调试的 debug APK（87 MB，ARM64） |
| `docs/ANDROID_17_IME_FIX.md` | Android 17 输入法动画问题根因与修复说明 |
| `docs/EXTENSION_AGENTS.md` | OpenCode 2 / ZCode / DeepSeek Harness 评估（ZCode 实现阶段落选） |
| `docs/CODE_REVIEW.md` | 严格代码审查记录（含 chroot/root 调用链） |
| 分支 `Sta` | 基于 `Mangi-11/Eta` main（`5e9d3a7`，v3.3.3）的 fork 提交 |

release APK 使用本地生成的 `CN=Sta Fork` 证书签名；如需上架或长期分发，请替换为自己的正式密钥。

## 上游同步（Eta 3.3.3）

已将上游 `5e9d3a7`（v3.3.3）合并进 Sta，保留 Sta 的扩展 Agent、DSH 移动壳与 Linux 文件管理器改动，并适配：

- Codex/Grok 订阅登录与远端模型同步；
- 网页搜索/读取切换 DeepSeek 或自定义接口；
- 消息排队发送与插话发送；
- 聊天回复和 GUI 执行期间保持屏幕常亮；
- ARM64-only 打包与应用图标更新；
- Kimi 未运行状态误报修复（Sta 的 Kimi 走扩展 Agent，启动状态由 `ExtensionAgentLauncher` 统一处理）；
- DB 升到 v24，兼容 Sta v23（`max_output_tokens`）与上游 v23（`auth_mode`）两种数据库。

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
- OpenCode 上游 2.1.2 的“自定义提供商”表单固定报 `provider.custom.unavailable`；Eta 改为从自身 provider/model 数据直接同步 `opencode.json`，映射上下文、视觉、工具、思考等级和自定义请求头/体，并执行 `opencode reload`；每次打开 OpenCode 前自动同步，运行中后台刷新，手动按钮只保留在 WebView 菜单。同步会先从 provider 的 `/models` 补全工具/视觉/上下文/输出上限，再写入 `limit.context`、`limit.output`、`capabilities.tools`、`capabilities.input` 和 `capabilities.reasoning`；同时保留已存在 provider 的手工 baseURL、API key、headers/body。
- 修复 DSH 移动 CSS 的 `#root` 规则泄漏到 OpenCode 的问题：CSS 仅在检测到 DSH `.pI_x6G_frame` 后注入，并在选择器上用 `:has` 限定 DSH，OpenCode 不再多出 56px 顶部空白。
- 扩展 Agent WebView 标签栏改用 Compose/Miuix 主题；Enter 捕获改为 window capture，OpenCode 用 `insertLineBreak`，DSH Lexical 用 `beforeinput(insertLineBreak)`，均能换行而不发送。
- OpenCode 原生 titlebar 在 Android WebView 中会重复读取 safe-area inset，导致顶部约 60px 空白；已把移动端 titlebar 固定为 36px，连接页/首页/会话页统一从顶部开始。
- 扩展 Agent 已运行时点击右上角启动/打开走快速路径：只查一次状态和日志 URL，直接打开 WebView，不再重复执行启动检查、日志轮询和配对。
- WebView 新建页面弹窗改为 Miuix 样式，提供 OpenCode / DeepSeek Harness / Kimi Code / 文件管理器快速入口，三个 Agent 使用各自品牌图标，并保留自定义 loopback 地址输入。
- 新增 MT 管理器风格的双窗口 Linux 文件管理器：左右窗口各自维护路径/历史/选择，点击或滚动任意一列立即激活，活跃列通过中缝阴影浮起；复制/移动直接从当前窗口到另一窗口（无粘贴步骤，长按菜单箭头指向目标列），底部后退/前进/新建/同步/上级五个动作；长按居中菜单含复制/移动/删除/重命名/压缩/属性/添加书签/多选，压缩生成 tar.gz，书签持久化；目录列举优先直接读取宿主可访问路径，避免切换目录转圈。
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

## 需求 4：Codex / Claude Code 与 CLI 账号（CC Switch 风格）

### 实现

- `CliAgent` 定义 Codex 与 Claude Code 的安装 profile、启动命令与登录/配置文件路径；
  `LinuxPackageProfiles` 新增两个固定版本 profile（Codex 0.164.0、Claude Code 2.1.309），
  Linux 工具页增加对应安装行。
- 新增 `CliAccountStore` / `CliConfigManager`：按 Agent 保存官方登录快照、API Key、第三方端点三类账号，
  手动切换。切换语义参考 CC Switch：只改连接信息和 Sta 自己的 provider 表，保留 hooks、MCP、
  profiles、注释等其它配置；第一次改写前备份，备份失败或 JSON 解析失败时失败关闭。
- Codex：官方/API Key 恢复或写入 `auth.json` 并清除 Sta 选路；第三方 provider 写
  `[model_providers.eta]`，Key 放在 `experimental_bearer_token`，不再依赖 `auth.json` 或
  `env_key` 环境变量。`config.toml` 由行级编辑器只替换关键字段与 eta 表；活动 profile 覆盖选路时
  拒绝写入并提示。
- Claude Code：官方账号只恢复 `.credentials.json`，并从 `settings.json.env` 移除 Sta 管理的
  Anthropic/云厂商键；API Key/第三方只合并 `env`，保留其它设置。
- 新增 `CliAccountsScreen`（Miuix 页面）：使用 Eta 的 preference group、choice chip、arrow
  preference 与 overlay dialog 语言；支持 Agent 切换、添加/删除/切换账号、安装提示与跳转、
  直接打开终端；API Key 输入做密码遮罩；账号文件位于 App 私有 `noBackupFilesDir/cli_accounts.json`。
- 新增 **CloudCLI GUI**：npm 全局安装 `@cloudcli-ai/cloudcli@1.37.5`，以
  `HOST=127.0.0.1 SERVER_PORT=3001 cloudcli --port 3001` 启动本地 Web UI，入口在
  “智能体 → CLI”分组，Linux 工具页有安装行。CloudCLI 复用官方 Claude Code / Codex
  会话与配置，不使用 T3 式编排提示；AGPL-3.0，包体和原生依赖较重。
- 账号行新增“模型设置”：在线拉取 `/models`、选择模型、设置上下文/最大输出/思考等级；
  “从 Eta 同步”可从当前 provider/model 填充连接信息与模型参数。Codex 写
  `model_context_window` / `model_reasoning_effort`，Claude 写
  `CLAUDE_CODE_MAX_CONTEXT_TOKENS` / `CLAUDE_CODE_EFFORT_LEVEL`。
- 路由新增 `Agents`（Web / CLI 分组的总入口）、`CliAgents`、`CliTerminal(agentId)`。首页右上角
  菜单只保留“智能体”总入口；页面内按字母排序列出 5 个 Agent 与“CLI 账号管理器”。WebView 的
  `+` 新建页弹窗也改为同一个总入口。
- 新增 **ACP Workspace**：自研 Web GUI，使用 Claude/Codex 的 ACP adapter，bridge 监听
  127.0.0.1:3210。Claude 与 Codex 分别采用暖白/陶土色与深色/白色品牌视觉，布局、字体、
  工具卡、内联审批和 composer 按各自 remote / desktop 风格重做；模型、思考强度、Fast mode、
  权限模式全部从 `configOptions` 读取并通过 `session/set_config_option` 切换。新增会话列表与恢复、
  历史回放、图片附件、slash commands、计划、diff、usage、认证状态、诊断日志。
- ACP bridge 改为每个 Agent 常驻 CLI 子进程：WebView 关闭/重载不会杀掉运行中的任务，重连后重放
  认证状态与未完成审批。`AcpGuiInstaller` 通过实际 chroot/PRoot shell 写入 `server.mjs`、UI、
  Geist/DM Sans/Instrument Serif 字体和品牌 SVG，避免 Android 侧直写 rootfs 路径。Codex App
  入口已移除，CloudCLI 保留为兜底。
- 修复 PRoot 下 `/root` 可能不可进入导致的后台启动失败：扩展启动器、`DetachedTaskSupervisor`
  与 ACP bridge 依次探测并回退到 `/workspace`；前台服务启动被拒绝时，用户明确触发的扩展 Agent
  允许回退为普通前台进程。守护进程使用绝对路径 `/system/bin/sh` 启动，避免 PATH 解析不到 `sh`
  时直接失败；启动失败会把 shell/异常 detail 带入提示，便于定位。
- CLI 账号存储增加第一版数据迁移（旧 `claude` agent id、缺失 id/name、旧 active map），账号操作
  按钮从父级 clickable 行移出，模型设置/切换/删除使用窗口级对话框，旧账号仍可完整操作。
- 终端页支持 `initialCommand`、`sessionTag` 与停止按钮：PTY 控制台按 Agent 标签隔离会话，首次
  连接后自动执行 `codex` / `claude`，返回页面不重复发送；停止后显示“CLI 已停止”并可重连。
  智能体页的 CLI 行新增后台启动/停止/打开，启动不强制跳转终端。PTY 不可用时显示“需要 PTY”占位，
  不再把 TUI 命令丢进块式终端。
- CLI 终端使用 `imePaddingSafe` 并在键盘显示时避免重复叠加导航栏 padding；账号页与添加/模型设置
  弹窗使用 `imePaddingSafe` + Compose overlay dialog，修复输入法动画与 inset 不同步。

### 验证

- `compileDebugKotlin`、`assembleDebug`、`assembleRelease`、`lintDebug` 通过；release APK 使用
  `CN=Sta Fork` 证书签名。
- `CliConfigEditorsTest`（12 个用例）覆盖 Codex 官方/API Key/第三方切换、上下文/思考等级、profile 覆盖拒绝、Claude 合并与非法 JSON、官方登录格式校验；`AppRouteSerializationTest` 覆盖新增路由，
  `ExtensionAgentTest` 覆盖 CloudCLI 启动命令与 loopback URL，`AlpineEnvironmentInstallerTest` 覆盖两个 CLI 与 CloudCLI 安装脚本，`EtaDatabaseMigrationTest` 覆盖 DB v24 迁移。
- 受上游 ARM64-only 打包限制，x86_64 模拟器无法安装最终 APK；CLI 安装/登录需在 ARM64 设备或
  root/chroot 环境中实机验证。

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
4. CLI 账号文件目前是 App 私有目录下的明文 JSON，尚未接入 Android Keystore；官方订阅账号通过
   CLI 在官方客户端之外使用可能违反服务条款，建议优先使用 API Key 或第三方端点。
   Codex 第三方模式按 Responses 兼容端点写入（`wire_api = "responses"`）；只支持 Chat Completions
   的网关需要本地转换（CC Switch 的路由模式）才能使用。若用户把 `cli_auth_credentials_store`
   设为 `keyring`，`auth.json` 切换可能不生效，需要在 Codex 配置里改回 `file`。
   官方登录快照可能因 CLI 刷新或 refresh token 轮换而失效，失效后重新导入即可；活动 Codex
   profile 覆盖 provider 选路时 Sta 会拒绝切换并提示先清除 profile。
