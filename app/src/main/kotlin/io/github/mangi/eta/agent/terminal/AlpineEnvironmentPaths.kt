package io.github.mangi.eta.agent.terminal

import android.content.Context
import java.io.File

/** Eta 管理的 Linux 工具环境路径；内部历史包名不参与对外展示。 */
internal object AlpineEnvironmentPaths {
    const val READY_MARKER = LinuxEnvironmentPaths.READY_MARKER
    const val COMMON_TOOLS_MARKER = ".eta-common-tools-ready"
    const val APK_ANALYSIS_MARKER = ".eta-apk-analysis-ready"
    const val PYTHON_TOOLS_MARKER = ".eta-python-tools-ready"
    const val NODE_TOOLS_MARKER = ".eta-node-tools-ready"
    const val SSH_TOOLS_MARKER = ".eta-ssh-tools-ready"
    const val OPENCODE_TOOLS_MARKER = ".eta-opencode-tools-ready"
    const val DSH_TOOLS_MARKER = ".eta-dsh-tools-ready"
    const val KIMI_TOOLS_MARKER = ".eta-kimi-tools-ready"
    const val CODEX_TOOLS_MARKER = ".eta-codex-tools-ready"
    const val CLAUDE_CODE_TOOLS_MARKER = ".eta-claude-code-tools-ready"
    const val CLOUDCLI_TOOLS_MARKER = ".eta-cloudcli-tools-ready"
    const val ACP_TOOLS_MARKER = ".eta-acp-tools-ready"
    const val TOOLSET_REVISION = 1
    const val APK_ANALYSIS_REVISION = 1
    const val PYTHON_TOOLS_REVISION = 1
    // revision 2：Debian 规格补装 libatomic1，已就绪环境需重走安装补齐依赖。
    const val NODE_TOOLS_REVISION = 2
    const val SSH_TOOLS_REVISION = 1
    const val OPENCODE_TOOLS_REVISION = 1
    // revision 2：移动端样式 v3（Eta 风格、抽屉/设置过渡动画、布局与滚动性能）。
    // revision 3：样式 v4（主题过渡、层级与选中态、抽屉分段入场、消息渲染收敛）。
    // revision 4：v4 修正顶栏重复 safe-area（DSH 上部空白）。
    // revision 5：v5 柔和动效（更长缓出、抽屉淡入缩放、消息/弹层更渐进的位移）。
    // revision 6：v6 恢复原生头部操作区（后台任务/子智能体/预设），统一不透明弹层，修复详细模式底部信息和问答输入。
    // revision 7：v7 紧凑详细模式底部排版、移动端右侧栏全屏预览、头部/预设/右栏动效。
    // revision 8：v8 原生头部操作合并进 Eta 顶栏、右侧栏覆盖层、底部排版与新建会话焦点控制。
    // revision 9：v9 暗色字体 token 继承修正。
    // revision 10：v10 修复 DSH mobile CSS 注入的单引号 shell 语法错误。
    // revision 11：v11 顶栏任务/子智能体图标角标、预设两行、紧凑统计排版。
    // revision 12：v12 修正统计 pill 文本节点目标。
    // revision 13：v13 顶栏图标垂直居中、预设图标与两行标题。
    // revision 14：v14 健壮的任务/子智能体 aria 查找与预设备选识别。
    // revision 15：v15 标题左对齐和预设 header 后备识别。
    // revision 16：v16 回退详细底栏、修复标题溢出、焦点框、思考白块、菜单贴顶和监听开销。
    // revision 17：v17 用页面类替换昂贵 :has 选择器，降低 WebView 样式重算开销。
    // revision 18：v18 WebView 外框主题色、GPU 图层提示和消息动画补全。
    // revision 19：v19 修复 Activity 构造期读取 resources 导致的启动闪退。
    // revision 20：v20 Eta 暗色同步到嵌入页、输入法 insets 动画。
    // revision 21：v21 外层标签栏/新建页高对比文字与图标。
    // revision 22：v22 移除与系统 adjustResize 冲突的 IME padding 动画。
    // revision 23：v23 移除预设硬编码兜底，只从 DSH 读取真实预设。
    // revision 24：v24 顶栏观察器改为 header 局部监听，WebView 渲染优先级提升。
    // revision 25：v25 首轮对话隐藏预设、会话切换快速同步、菜单强制贴顶。
    // revision 26：v26 任务/子智能体菜单通用定位与 Eta 风格模型菜单宽度。
    // revision 17：v17 用页面类替换昂贵 :has 选择器，降低 WebView 样式重算开销。
    // 已就绪环境需重跑安装脚本以替换注入的样式块。
    const val DSH_TOOLS_REVISION = 26
    const val KIMI_TOOLS_REVISION = 1
    const val CODEX_TOOLS_REVISION = 1
    const val CLAUDE_CODE_TOOLS_REVISION = 1
    const val CLOUDCLI_TOOLS_REVISION = 1
    const val ACP_TOOLS_REVISION = 1

    fun environmentDir(context: Context): File =
        LinuxEnvironmentPaths.environmentDir(context, LinuxDistribution.ALPINE)

    fun rootfsDir(context: Context): File =
        LinuxEnvironmentPaths.rootfsDir(context, LinuxDistribution.ALPINE)

    fun artifactDir(context: Context): File =
        File(context.cacheDir, "linux-installer/artifacts")

    fun profileStagingDir(context: Context, profile: String): File =
        File(context.cacheDir, "linux-installer/profiles/$profile.installing")

    fun rootfsReady(rootfsPath: String?): Boolean {
        return LinuxEnvironmentPaths.rootfsReady(rootfsPath)
    }

    fun commonToolsReady(rootfsPath: String?): Boolean {
        if (!rootfsReady(rootfsPath)) return false
        val marker = File(rootfsPath, COMMON_TOOLS_MARKER)
        if (!marker.isFile) return false
        return runCatching {
            marker.useLines { lines ->
                lines.any { line -> line.trim() == "toolset=$TOOLSET_REVISION" }
            }
        }.getOrDefault(false)
    }

}
