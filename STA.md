# Sta

**Sta** 是 [Eta](https://github.com/Mangi-11/Eta) 的分支，保留上游全部系统级 AI 助手能力，并在其之上集中完善
**扩展 Agent（OpenCode / DeepSeek Harness / Kimi Code）的移动端体验**。

- 上游：<https://github.com/Mangi-11/Eta>
- 分支：`Sta`
- 基线版本：Eta `3.3.1`
- Android：minSdk 33 / targetSdk 37

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

### DSH 移动壳

DSH Web 前端是桌面优先布局。Sta 在**不修改 DSH 逻辑和 DOM 锚点**的前提下注入一层移动样式：

- 固定顶栏 + 左侧抽屉；桌面 rail 隐藏，抽屉仍由 DSH 自己的 sidebar 状态驱动。
- 会话列占满视口，覆盖桌面端 `clamp(680px, …)` 的最小宽度。
- 设置弹窗改为全屏 sheet：横向分类 chip、设置行单行卡片、关闭按钮固定在右上角。
- 视觉向 Eta 靠拢：暖灰画布、白色卡片、16–24px 圆角、hairline 描边、克制阴影。
- 动画只使用 `transform` / `opacity`：抽屉 0.34s 缓出、sheet 上滑、菜单缩放淡入、消息上浮；
  `prefers-reduced-motion: reduce` 下全部关闭。
- 性能：`overscroll-behavior: contain`、`translate3d` 合成层、不使用 `transition: all`，
  不在滚动区域叠加大面积 `backdrop-filter`。
- 深浅色全部从 DSH 的 `--dsw-*` 变量派生，不写死颜色。

样式块带 `/* eta-mobile-start */` / `/* eta-mobile-end */` 标记，安装脚本先删除旧块再追加，
可重复执行；WebView 侧在检测到 `.pI_x6G_frame` 后才注入，避免影响 OpenCode。

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
  agent/terminal/        Linux 环境与 npm 工具 profile（含 DSH 移动 CSS）
  ui/web/                扩展 Agent WebView、OpenCode provider 同步
docs/EXTENSION_AGENTS.md 扩展 Agent 设计与约束
REPORT.md                本分支改动报告
```

## 说明

Sta 是非官方分支，与 Eta 上游、OpenCode、DeepSeek、Moonshot 均无隶属关系。使用第三方模型服务
需要自备 API Key；Root / LSPosed 相关能力取决于设备授权与 ROM 适配。
