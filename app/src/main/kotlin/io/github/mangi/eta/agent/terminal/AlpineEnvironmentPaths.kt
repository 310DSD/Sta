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
    // 已就绪环境需重跑安装脚本以替换注入的样式块。
    const val DSH_TOOLS_REVISION = 7
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
