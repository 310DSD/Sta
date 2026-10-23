# Sta

**Sta** 是 [Eta](https://github.com/Mangi-11/Eta) 的分支，保留上游全部系统级 AI 助手能力，并在其之上集中完善
**扩展 Agent（OpenCode / DeepSeek Harness / Kimi Code）与 CLI 智能体（Codex / Claude Code）的移动端体验**。

- 上游：<https://github.com/Mangi-11/Eta>
- 分支：`Sta`
- 基线版本：Eta `3.3.3`（上游 `5e9d3a7`）
- Android：minSdk 33 / targetSdk 37

## 上游同步

已合并 Eta 3.3.3（`5e9d3a7`），保留 Sta 的扩展 Agent、DSH 移动壳与 Linux 文件管理器改动，并适配上游的
Codex/Grok 订阅登录、网页搜索/读取切换、消息排队与插话、聊天/GUI 屏幕常亮、ARM64-only 打包和图标更新。
Sta 与上游都曾停在 DB v23 但新增了不同列，合并后升到 v24，按列存在性补齐，兼容两种来源。

## 这个分支做了什么

### 扩展 Agent

| Agent | 集成方式 | 说明 |
| --- | --- | --- |
| OpenCode | `@opencode/cli@2.1.2` | 共享 service + `opencode pair`，内置 WebView 多标签 |
| DeepSeek Harness | `@deepseek-ai/dsh@0.2.1-rc.2` | `dsh web` 直接输出带 token 的 loopback URL |
| Kimi Code | `@moonshot-ai/kimi-code@latest` | 保留原有 TUI / profile 入口 |

- 三个 Agent 共用 `ExtensionAgentWebActivity`：多标签、独立 WebView 会话、返回栈、文件选择、
  系统浏览器外链、状态栏/输入法 inset。
- 运行中的 Agent 走快速打开路径：只查一次任务状态和日志 URL，不再重复执行安装检查、
  日志轮询和配对。
- 扩展 Agent 输入框的 Enter 只换行、不发送：OpenCode 用 `insertLineBreak`，DSH Lexical 用
  `beforeinput(insertLineBreak)`，textarea 用 `setRangeText`。
- WebView 右上角 `+` 改为 Miuix 新建页面弹窗，提供 OpenCode / DeepSeek Harness / Kimi Code /
  文件管理器快速入口，三个 Agent 使用各自品牌图标，并保留自定义 loopback 地址输入。

### DSH 移动壳

DSH Web 前端是桌面优先布局。Sta 在**不修改 DSH 逻辑和 DOM 锚点**的前提下注入一层移动样式：

- 固定顶栏 + 左侧抽屉；桌面 rail 隐藏，抽屉仍由 DSH 自己的 sidebar 状态驱动。
- 会话列占满视口，覆盖桌面端 `clamp(680px, …)` 的最小宽度。
- 设置弹窗改为全屏 sheet：横向分类 chip、设置行单行卡片、关闭按钮固定在右上角。
- 视觉向 Eta 靠拢：暖灰画布、白色卡片、16–24px 圆角、hairline 描边、克制阴影；
  工作区/模式为 chip，选中会话带 3px 强调条，代码块与行内代码有独立表面。
- 主题切换时画布、卡片、边框做 0.3s 颜色过渡，`:focus-visible` 提供键盘焦点环。
- 动画只使用 `transform` / `opacity`：抽屉 0.42s 缓出并轻微缩放/淡入、入口分段入场、
  sheet 44px 上滑、菜单缩放淡入、仅最后一条消息 10px 上浮；`prefers-reduced-motion: reduce` 下全部关闭。
- 性能：`overscroll-behavior: contain`、`translate3d` + `contain` 合成层、
  消息项 `contain: layout style`、不使用 `transition: all`，不对大子树做入场动画，
  不在滚动区域叠加大面积 `backdrop-filter`。
- 深浅色全部从 DSH 的 `--dsw-*` 变量派生，不写死颜色。
- 顶栏不重复叠加 `env(safe-area-inset-top)`，并只在 `#eta-mobile-bar` 真正插入后才为它让出
  高度，避免 DSH 顶部出现空白条。

样式块带 `/* eta-mobile-start */` / `/* eta-mobile-end */` 标记，安装脚本先删除旧块再追加，
可重复执行；WebView 侧在检测到 `.pI_x6G_frame` 后才注入，避免影响 OpenCode。

### Linux 文件管理器

内置 MT 管理器风格的双窗口 rootfs 文件管理器，可从 WebView 新建页面弹窗或 Linux 工具页进入：

- 左右两个文件窗口同时显示，各自维护路径、历史、选择与滚动位置；点击或滚动任意一列立即激活，当前窗口通过中缝阴影浮起；
- 复制 / 移动直接从当前窗口到另一窗口，没有单独的粘贴步骤；长按菜单里的箭头指向目标列；
- 长按文件/文件夹在屏幕中央打开双列菜单：复制 / 移动 / 删除 / 重命名 / 压缩 / 属性 / 添加书签 / 多选；
- 压缩生成 `tar.gz`，书签持久化；底部提供后退、前进、新建、同步、上级五个动作；
- 目录列举优先直接读取宿主可访问路径（`/workspace` 映射到宿主工作区），不可读时才回退 root shell / PRoot，切换目录不再每次转圈；
- 文本文件查看与编辑，二进制文件提示不可预览。

### Codex / Claude Code 与 CLI 账号

Codex 与 Claude Code 是终端 TUI 型 Agent，没有官方移动 Web UI，Sta 统一用内置 PTY 终端承载：

- Linux 工具页新增 Codex（`@openai/codex@0.164.0`）与 Claude Code（`@anthropic-ai/claude-code@2.1.309`）
  安装行，依赖 Node.js；安装脚本固定版本，可重复执行。
- 首页右上角二级菜单新增“智能体”总入口：页面按 **Web / CLI** 分组、组内按字母排序，
  包含 OpenCode、DeepSeek Harness、Kimi Code、Claude Code、Codex 五个 Agent 与
  “CLI 账号管理器”。两个 CLI 在总入口内各有快速入口；内置 WebView 的 `+` 新建页弹窗
  也提供同一个总入口。
- 账号页沿用 Eta 的 preference group / choice chip / arrow preference 语言；账号行可打开
  “模型设置”：在线拉取 `/models`、选择模型、设置上下文/最大输出/思考等级，也支持从 Eta
  当前 provider/model 同步。
- 终端工具栏在 CLI 会话中提供停止按钮；停止后显示“CLI 已停止”并可重新连接。终端会话按
  Agent 标签隔离，Codex 与 Claude Code 各自保留自己的 PTY。
- CLI 终端与账号页使用 `imePaddingSafe`，弹窗改为 Compose overlay，避免输入法动画与
  WindowDialog 的 inset 不同步。
- 账号类型：官方账号（只导入 CLI 自己生成的登录文件）、官方 API Key、第三方兼容端点
  （baseURL + key + model）。
- 切换语义参考 CC Switch：只改连接信息与 Sta 自己的 provider 表，保留 hooks、MCP、profiles、
  注释等其它配置；第一次改写前备份，备份失败就中止，配置解析失败也失败关闭。
- Codex：官方/API Key 会恢复或写入 `~/.codex/auth.json` 并清除 Sta 的选路；第三方 provider 写成
  `[model_providers.eta]`，Key 放在 `experimental_bearer_token`，不依赖 `auth.json` 或进程环境变量。
  `config.toml` 由行级编辑器只替换 `model` / `model_provider` / `openai_base_url` 与 eta 表。
- Claude Code：官方账号只恢复 `~/.claude/.credentials.json`，并从 `settings.json` 的 `env` 中移除
  Sta 管理的 Anthropic/云厂商键；API Key/第三方只合并 `env`，保留其它设置。
- 账号列表保存在 App 私有 `noBackupFilesDir/cli_accounts.json`（明文，尚未接入 Keystore）；
  官方登录快照可能因 CLI 刷新/轮换而失效，失效后重新导入即可。

### OpenCode 提供商同步

OpenCode 2.1.2 上游的“自定义提供商”表单固定报 `provider.custom.unavailable`，因此 Sta 直接从
Eta 的 provider/model 数据生成 `~/.config/opencode/opencode.json`：

- 使用 OpenCode V2 schema：`providers`（复数）、`package`、`settings.baseURL`、`settings.apiKey`、
  `headers`、`body`、`models`。
- 模型写入 `modelID`、`limit.context`、`limit.output`、`capabilities.tools`、
  `capabilities.input` / `output`（显式 JSON 数组）、`capabilities.reasoning`、`variants`。
- 同步前先读取 provider 的 `/models`，补全上下文、输出上限、视觉与工具能力；读取失败则使用
  Eta 已保存的元数据。
- 合并时保留已存在 provider 的手工 `baseURL`、API key、headers/body 与模型自定义字段。
- 只调用 `opencode reload`，不重启 service（重启会导致 WebView `failed to fetch`）。
- 每次打开 OpenCode 前自动同步；已在运行时后台刷新，不阻塞打开；手动按钮只保留在 WebView 页面菜单。

### 其他上游修复

- 原生 Markdown 分式改为“分子 / 横向分数线 / 分母”，分数线按最宽文本测量。
- 原生对话用户消息也走 Markdown，补充 LaTeX 希腊字母与常用符号归一化。
- OpenCode WebView titlebar 固定 36px，修复重复读取 safe-area 造成的顶部空白。
- 扩展 Agent 标签栏使用 Compose/Miuix 主题。

## 构建

环境：JDK 25、Android SDK 37、Gradle 9.7.1（`./gradlew` 自动下载）。

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew :app:assembleDebug
```

签名 release：

```bash
export ETA_RELEASE_STORE_FILE=/path/to/sta-release.jks
export ETA_RELEASE_STORE_PASSWORD=...
export ETA_RELEASE_KEY_ALIAS=sta
export ETA_RELEASE_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

产物在 `app/build/outputs/apk/`。`dist/` 只存放本地分发的 APK 与校验和，不纳入版本管理。

## 与上游同步

```bash
git remote add upstream https://github.com/Mangi-11/Eta.git
git fetch upstream
git switch Sta
git merge upstream/main
```

扩展 Agent 的版本固定在
`app/src/main/kotlin/io/github/mangi/eta/agent/terminal/LinuxPackageProfiles.kt`：
升级 OpenCode / DSH 时修改版本号，并递增
`AlpineEnvironmentPaths.kt` 中对应的 `*_TOOLS_REVISION`，已安装环境才会重新执行安装脚本。

DSH 升级后需要重新核对 `pI_x6G_*`、`wSkVaW_*`、`VOzbGW_*`、`Sixlwa_*` 等 hash 选择器。

## 目录

```
app/src/main/kotlin/io/github/mangi/eta/
  agent/extension/       扩展 Agent 定义、启动、URL 解析
  agent/cli/             Codex / Claude Code 定义、账号存储与配置切换
  agent/terminal/        Linux 环境与 npm 工具 profile（含 DSH 移动 CSS）
  ui/web/                扩展 Agent WebView、OpenCode provider 同步
  ui/screens/cli/        CLI 账号管理页
docs/EXTENSION_AGENTS.md 扩展 Agent 设计与约束
REPORT.md                本分支改动报告
```

## 说明

Sta 是非官方分支，与 Eta 上游、OpenCode、DeepSeek、Moonshot 均无隶属关系。使用第三方模型服务
需要自备 API Key；Root / LSPosed 相关能力取决于设备授权与 ROM 适配。
