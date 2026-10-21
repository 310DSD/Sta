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
| 官方分发 | npm `@opencode/cli`，含 `linux-arm64` 与 `linux-arm64-musl` 二进制 | 官方提供桌面版与 CLI 源码；CLI 需按 `ZCODE_DIST_BASE_URL` 自建/自托管 | npm `@deepseek-ai/dsh` |
| 运行时依赖 | npm 安装后是单文件二进制，运行时不需 Node | Node.js ≥ 22.19（`zcode-app-cli` 社区打包版） | Node.js + pnpm + 86 个直接依赖/插件包 |
| 安装体积（参考） | Linux ARM64 包约 209 MB；x64 实测 `node_modules` 418 MB | 约 123 MB（npm 包）；x64 实测 `node_modules` 135 MB | x64 实测 827 MB / 611 个包 |
| Web 入口 | `opencode web --port 4096` | `zcode --web --port 3030 --no-open` | `npx @deepseek-ai/dsh web`（默认 3080） |
| 移动端 Web UI | 官方 Web UI，响应式布局可用 | 官方 Web UI，产品本身提供手机远程控制入口 | 以桌面为主，移动端适配较弱 |
| 终端 TUI | `opencode` | `zcode` | `dsh` / profile 启动 |
| 扩展能力 | 插件、Code Mode、SDK、后台子 Agent | Agent CLI/运行时、插件市场、MCP | 一切皆插件、Cordis 架构 |
| 成熟度 | 2.x 稳定线，更新频繁 | 3.16.x 稳定产品 | **开发者预览**，明确会破坏兼容 |
| Android/PRoot 适配风险 | 低：官方 musl/arm64 包，单二进制 | 中：CLI 分发依赖 Node 与社区 npm 打包 | 高：依赖多、pnpm/profile 初始化重、Web UI 未针对手机 |

## 3. 结论

**选择 OpenCode 2.x + ZCode 3.16.x，替换 Kimi Code。**

- **OpenCode**：官方 npm 包直接提供 `linux-arm64` 与 `linux-arm64-musl` 二进制，
  在 Alpine PRoot 与 Debian PRoot 下都可安装；`opencode web` 是无头模式，正好匹配 Eta
  的 daemon + 浏览器启动方式；单二进制也减少了手机上的依赖故障面。
- **ZCode**：Web UI 完整、产品化程度高，且官方更新日志里有手机远程控制能力；`zcode --web`
  支持固定端口、`--no-open` 和工作区参数，适合被 Eta 托管。代价是 CLI 需要 Node 运行时，
  当前通过 npm 上的 `zcode-app-cli` 社区打包版安装，属于已知风险；后续可把
  `ZCODE_INSTALL_SCRIPT` 替换为官方 `install.sh` 或自托管 tarball。
- **DeepSeek Harness 落选**：它是开发者预览，86 个直接依赖加插件式 profile 初始化，在
  PRoot 手机环境里安装时间和磁盘占用都明显更高；Web UI 也以桌面为主。Eta 的作者在
  3.0.0 发布说明里也明确提到“没选 DeepSeek Harness——依赖太多，Web UI 没适配移动端”。
  本次独立评估与该结论一致。

## 4. 代码接入

### 4.1 安装 profile

`app/src/main/kotlin/io/github/mangi/eta/agent/terminal/LinuxPackageProfiles.kt`

- `OPENCODE`：依赖 `NODE` profile，执行
  `npm install -g --prefix /usr/local @opencode/cli@latest`，安装后校验
  `opencode --version` 和 `opencode web --help`。
- `ZCODE`：依赖 `NODE` profile，执行
  `npm install -g --prefix /usr/local zcode-app-cli@latest`，安装后校验 `zcode --help`。
- 两个 profile 都优先使用 `registry.npmmirror.com`，失败后回退官方 registry；这与原
  Kimi Code 的安装策略一致。

### 4.2 Agent 目录与启动

- `app/src/main/kotlin/io/github/mangi/eta/agent/extension/ExtensionAgent.kt`
  定义两个 Agent 的 id、显示名、安装 profile、Web 命令、可复用命令集合、URL 正则和 TUI 命令。
- `ExtensionAgentWebSession.kt`：启动/复用 daemon，轮询日志提取 loopback URL；失败时只回收
  本次创建且未成功交给浏览器的任务。
- `ExtensionAgentLauncher.kt`：解析 Linux 环境、取得前台执行租约、按 Agent 串行化启动。
- `ExtensionAgentUiState.kt`：检查中/未安装/就绪/启动中/运行中/失败六态与错误文案。

### 4.3 UI

- 首页顶栏溢出菜单的 Kimi Web 项改为当前默认扩展 Agent 的“启动/打开/停止”入口。
- Linux 工具环境页的 Kimi Code 行替换为 OpenCode、ZCode 两行：安装、启动 Web、停止、
  设为默认。
- 默认 Agent 持久化在 `SettingsDataStore` 的 `extension_agent` 键，由
  `ExtensionAgentSettingsRepository` 读写；切换后顶栏立即跟随。

## 5. 验证

- 单元测试 `ExtensionAgentWebSessionTest` 覆盖 URL 提取、复用已有任务、失败回收、
  浏览器不可用。
- `AlpineEnvironmentInstallerTest` 校验两个 profile 的安装脚本、依赖与 npm 参数。
- 构建 `:app:assembleDebug` 通过后，在真机上的验证路径：
  1. 安装 Linux 基础环境与 Node.js；
  2. 在“可选工具”中安装 OpenCode / ZCode；
  3. 点“启动 Web”，确认浏览器打开 `http://127.0.0.1:4096` 或 `http://127.0.0.1:3030`；
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

- OpenCode/ZCode 的 Web UI 默认只监听 `127.0.0.1`，但 **loopback 不是安全边界**：
  同一设备上的其他应用仍可连接并驱动 Agent，而 chroot/root 下 Agent 可读写工作区。
- 当前版本未给 Web UI 加认证 token；建议后续为每次启动生成 per-launch token（路径或
  query secret），或改用 Unix socket + 本地转发。固定端口也会让多个 Agent 实例互相冲突，
  这与“切换后端前停止旧实例”的限制一起，属于后续需要补齐的生命周期管理。

## 8. 参考资料

- OpenCode CLI/Web 文档：<https://opencode.ai/docs/cli/>、<https://opencode.ai/docs/web/>
- OpenCode 2.0 发布说明：<https://anoma.ly/notes/opencode-2-0/>
- ZCode 仓库与 CLI 说明：<https://github.com/zai-org/ZCode>
- ZCode 安装文档：<https://zcode.z.ai/en/docs/install>
- DeepSeek Harness：<https://github.com/deepseek-ai/deepseek-harness>
