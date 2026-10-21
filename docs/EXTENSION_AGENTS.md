# 扩展 Agent 选型与接入说明

## 1. 背景

Eta 3.3.1 原先在 Linux 工具环境里通过 npm 安装 **Kimi Code**，并用 `kimi web --no-open`
启动 Kimi Web，解析日志里的带 token 地址后交给系统浏览器。本次 fork 需要从下列三个候选中
选择两个，替换 Kimi Code 作为 Eta 的扩展 Agent：

- OpenCode 2.x
- ZCode 3.16.x
- DeepSeek Harness (`dsh`)

约束条件来自 Eta 的实际运行环境：Android 手机、Linux 发行版跑在 PRoot/chroot 中、ARM64、
无 systemd、内存和存储有限、Web UI 由系统浏览器打开、后台进程必须能被 Eta 的
`DetachedTaskSupervisor` 管理。

## 2. 候选对比

| 维度 | OpenCode 2.1.2 | ZCode 3.16.2 | DeepSeek Harness 0.2.1-rc.2 |
| --- | --- | --- | --- |
| 许可证 | MIT | Apache-2.0（第一方源码） | MIT |
| 官方分发 | npm `@opencode/cli`，含 `linux-arm64` 与 `linux-arm64-musl` 二进制 | 官方桌面版；官方 CLI 需 `pnpm build:zcode` 自建/自托管 | npm `@deepseek-ai/dsh` |
| 运行时依赖 | npm 安装后是单文件二进制，运行时不需 Node | 官方 CLI 需 Node + 自托管运行包；社区 `zcode-app-cli` 仅 TUI | Node.js + 86 个直接依赖/插件包（611 个包） |
| 安装体积（参考） | Linux ARM64 包 209.5 MB；运行时另需平台二进制依赖 | 社区包约 123 MB（仅 TUI，已淘汰） | **ARM64 实测 787 MB / 610 个包** |
| Web 入口 | `opencode service start`（共享服务）+ `opencode pair` 登录链接 | 官方 CLI 为 `zcode --web ...`，但社区包不支持 `--web` | `dsh web --host 127.0.0.1 --port 3080 --no-open`，直接打印带 token URL |
| 移动端 Web UI | 官方 Web UI，响应式布局可用 | 官方 Web UI 完整，但无可用 npm CLI 分发 | 以桌面为主，移动端适配一般 |
| 终端 TUI | `opencode` | `zcode`（社区包） | `dsh` |
| 扩展能力 | 插件、Code Mode、SDK、后台子 Agent | Agent CLI/运行时、插件市场、MCP | 一切皆插件、Cordis 架构 |
| 成熟度 | 2.x 稳定线，更新频繁 | 3.16.x 稳定产品 | **开发者预览**，明确会破坏兼容 |

## 3. 结论

**选择 OpenCode 2.x + DeepSeek Harness 0.2.1，替换 Kimi Code。**

- **OpenCode**：官方 npm 包直接提供 `linux-arm64` 与 `linux-arm64-musl` 二进制，单二进制
  运行时不依赖 Node；2.x 没有 `web` 子命令；Web UI 由共享后台服务提供。正确入口是
  `opencode service start` + `opencode pair`（一次性登录链接）。`serve` 是独立服务器，
  不支持 `pair`，不能用于 Web UI 配对。
- **DeepSeek Harness**：官方 npm `@deepseek-ai/dsh`，`dsh web` 直接打印带 token 的 loopback
  URL，生命周期与 Eta 的 daemon 模型完全匹配。代价是 611 个包、约 827 MB，且是开发者预览；
  这是为了拿到“官方可安装 + 官方 Web UI”而接受的体积/稳定性折衷。
- **ZCode 落选（实现阶段修正）**：官方仓库的 Web CLI 只在源码构建产物
  （`pnpm build:zcode` + `ZCODE_DIST_BASE_URL` 自托管）中提供；npm 上可安装的
  `zcode-app-cli` 是社区 TUI 打包，实际执行 `zcode --web` 会报
  `Unknown option '--web'`，无法作为 Web 扩展 Agent。除非自建并托管官方 CLI tarball，
  否则 ZCode 不能替换 Kimi Web。

## 4. 代码接入

### 4.1 安装 profile

`app/src/main/kotlin/io/github/mangi/eta/agent/terminal/LinuxPackageProfiles.kt`

- `OPENCODE`：依赖 `NODE` profile，执行
  `npm install -g --prefix /usr/local @opencode/cli@2.1.2`，安装后校验
  `command -v opencode` + `opencode --version`。
- `DSH`：依赖 `NODE` profile，执行
  `npm install -g --prefix /usr/local @deepseek-ai/dsh@0.2.1-rc.2`，安装后校验
  `command -v dsh` + `dsh --version`。
- 两个 profile 都优先使用 `registry.npmmirror.com`，失败后回退官方 registry；OpenCode
  安装超时 30 分钟，DSH 610 个包使用 60 分钟。
- DSH 的 `koffi` / `node-pty` 会使用 `@koromix/koffi-linux-arm64`、`node-pty/prebuilds/linux-arm64`
  预编译文件；若镜像缺少 optional dependency，npm 会尝试源码编译，而基础 rootfs 没有
  build-essential/cmake，安装会失败。选型验证使用了完整 ARM64 optional 依赖。
- DSH Web 前端是桌面优先；安装 profile 会在
  `@deepseek-ai/dsh-web-frontend` 的 CSS 末尾追加一段保守的 `max-width:720px` 覆盖
  （收窄 sidebar、让聊天区宽度 100%、设置弹窗改为纵向导航），不修改 JS。升级 DSH 版本后
  需重新检查 `VOzbGW_*` 等选择器；设置弹窗的样式由插件运行时注入，覆盖规则带
  `!important`。

### 4.2 Agent 目录与启动

- `app/src/main/kotlin/io/github/mangi/eta/agent/extension/ExtensionAgent.kt`
  定义两个 Agent 的 id、显示名、安装 profile、Web 命令、可复用命令集合、URL 正则、
  TUI 命令，以及 OpenCode 的 `pairCommand`。
- `ExtensionAgentWebSession.kt`：启动/复用 daemon，轮询日志提取 loopback URL；
  OpenCode wrapper 启动共享服务并输出 `service status` 地址，随后执行 `opencode pair` 获取一次性
  `/auth/connect/...` 登录链接；失败时只回收本次创建且未成功交给浏览器的任务。
- `ExtensionAgentLauncher.kt`：解析 Linux 环境、取得前台执行租约、按 Agent 串行化启动。
- `ExtensionAgentUiState.kt`：检查中/未安装/就绪/启动中/运行中/失败六态与错误文案。

### 4.3 UI

- 首页顶栏溢出菜单的 Kimi Web 项改为当前扩展 Agent 的“启动/打开/停止”入口。
- Linux 工具环境页的 Kimi Code 行替换为 OpenCode、DeepSeek Harness 两行：安装、
  启动 Web、停止；最近一次成功启动的 Agent 自动成为顶栏默认入口，不再额外占用一行
  “设为默认”按钮。
- 默认 Agent 持久化在 `SettingsDataStore` 的 `extension_agent` 键，由
  `ExtensionAgentSettingsRepository` 读写；切换后顶栏立即跟随。
- 顶栏溢出菜单同时提供 OpenCode / DeepSeek Harness 两个快速入口，点击未安装的 Agent
  会转到 Linux 工具环境页，不再只能打开当前默认 Agent。
- 扩展 Agent URL 由 `ExtensionAgentWebActivity` 内置 WebView 承载；移动宽度下注入
  Enter→换行脚本（Shift/Ctrl/Cmd/Alt 语义保留），textarea 与 Lexical contenteditable
  都覆盖，解决手机键盘没有 Shift 时误发送。

## 5. 验证

- 单元测试 `ExtensionAgentWebSessionTest` 覆盖 URL 提取、复用已有任务、失败回收、
  浏览器不可用。
- `AlpineEnvironmentInstallerTest` 校验两个 profile 的安装脚本、依赖与 npm 参数。
- 构建 `:app:assembleDebug` 通过后，在真机上的验证路径：
  1. 安装 Linux 基础环境与 Node.js；
  2. 在“可选工具”中安装 OpenCode / DeepSeek Harness；
  3. 点“启动 Web”，确认浏览器打开 OpenCode 的 `/auth/connect/...` 登录链接或
     DSH 的 `http://127.0.0.1:3080/?token=...`；
  4. 回到 Eta，确认顶栏按钮变为“打开 …”，并能停止守护任务。

## 6. chroot + root 适配

本 fork 主要面向 **Root 授权后的 chroot 模式**（`LinuxExecutionBackend.CHROOT`）：

- 安装扩展 Agent 时显式把当前 `backend` 传给 `LinuxPackageProfileInstaller`，rootfs 固定为
  `filesDir/terminal/<distribution>/rootfs`，不会因缓存的后端选择误装到 PRoot 目录。
- 启动时 `TerminalRuntime.defaultIdentity()` 对 chroot 返回 `root`；
  `DetachedTaskSupervisor` 通过 `su -c` 进入 `unshare -m` + `chroot`，内层环境已设置
  `HOME=/root`、`PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin`，
  因此 npm 的 `--prefix /usr/local` 安装结果可直接执行。
- 工作目录 `/workspace` 由 `/data/local/tmp/eta` bind mount 到 rootfs，root 可读写；
  Web 服务监听 `127.0.0.1`，与 Android 浏览器共享网络命名空间。
- 只有 `identity=user`（PRoot）才申请 `AgentExecutionService` 前台执行租约；chroot/root
  守护任务直接由 root shell 启动，stop/status 仍按 `environment + identity + backend`
  精确匹配，避免误停 PRoot 任务。
- PRoot 仍保留为无 Root 回退路径，但其身份是普通 App 用户，文件权限和性能均弱于 chroot。

## 7. 安全边界

- OpenCode/DSH 的 Web UI 默认只监听 `127.0.0.1`，但 **loopback 不是安全边界**：
  同一设备上的其他应用仍可连接并驱动 Agent，而 chroot/root 下 Agent 可读写工作区。
- OpenCode 使用一次性 `/auth/connect/...` 链接登录；DSH 的 URL 自带 token。仍建议后续
  为每次启动生成独立 secret，或改用 Unix socket + 本地转发。固定端口也会让多个 Agent
  实例互相冲突，这与“切换后端前停止旧实例”的限制一起，属于后续需要补齐的生命周期管理。

## 8. 参考资料

- OpenCode CLI/Web 文档：<https://opencode.ai/docs/cli/>、<https://opencode.ai/v2/docs/cli/web>
- OpenCode 2.0 发布说明：<https://anoma.ly/notes/opencode-2-0/>
- ZCode 仓库与 CLI 说明：<https://github.com/zai-org/ZCode>
- ZCode 安装文档：<https://zcode.z.ai/en/docs/install>
- DeepSeek Harness：<https://github.com/deepseek-ai/deepseek-harness>
