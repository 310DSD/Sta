package io.github.mangi.eta.ui.web

import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.mangi.eta.agent.extension.OpenCodeProviderConfig
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.agent.terminal.LinuxFileExplorer
import io.github.mangi.eta.agent.terminal.ShellProcessSupervisor
import io.github.mangi.eta.agent.terminal.runOneShotShell
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import io.github.mangi.eta.data.repository.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 直接把 Eta 的 provider/model 数据同步为 OpenCode 的 `providers` 配置。
 *
 * OpenCode 2.1.2 网页里的“自定义提供商”表单是上游未实现的占位代码，而且手工填表
 * 会丢掉上下文、视觉、思考等级等 Eta 已有元数据；因此这里不维护第二套表单，只做
 * 一次性映射。原有 OpenCode 配置（MCP、插件、其他 provider）会保留。
 */
internal object OpenCodeProviderDialog {
    fun show(activity: ComponentActivity, onSaved: () -> Unit) {
        activity.lifecycleScope.launch {
            val providers = withContext(Dispatchers.IO) {
                runCatching {
                    ProviderRepository.ensureBuiltInsMerged()
                    ProviderRepository.allProviders()
                }.getOrElse { emptyList() }
            }
            val (providerCount, modelCount) = OpenCodeProviderConfig.syncSummary(providers)
            if (providerCount == 0) {
                Toast.makeText(activity, "Eta 中没有已启用的提供商/模型", Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(
                activity,
                "正在同步 $providerCount 个提供商、$modelCount 个模型…",
                Toast.LENGTH_SHORT,
            ).show()
            val error = withContext(Dispatchers.IO) { sync(activity, providers) }
            if (error == null) {
                Toast.makeText(
                    activity,
                    "已同步 $providerCount 个提供商、$modelCount 个模型到 ${OpenCodeProviderConfig.CONFIG_PATH}",
                    Toast.LENGTH_SHORT,
                ).show()
                onSaved()
            } else {
                Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    internal fun sync(context: Context, providers: List<ProviderSetting>): String? {
        val distribution = LinuxEnvironmentSettingsRepository.current(context)
        val backend = LinuxEnvironmentSettingsRepository.backend(context, distribution)
        val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution, backend)
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) return "Linux 环境尚未安装"
        val supervisor = ShellProcessSupervisor()
        return try {
            val existing = when (val read = LinuxFileExplorer.readText(supervisor, rootfs, OpenCodeProviderConfig.CONFIG_PATH)) {
                is LinuxFileExplorer.ReadResult.Text -> read.content
                LinuxFileExplorer.ReadResult.NotFile -> null
                LinuxFileExplorer.ReadResult.NotInstalled -> return "Linux 环境尚未安装"
                LinuxFileExplorer.ReadResult.Unreadable,
                LinuxFileExplorer.ReadResult.CommandFailed -> return "无法读取 OpenCode 配置"
                LinuxFileExplorer.ReadResult.Binary -> return "OpenCode 配置不是文本文件"
            }
            val merged = runCatching { OpenCodeProviderConfig.mergeEtaProviders(existing, providers) }
                .getOrElse { return "配置文件解析失败：${it.message}" }
            when (LinuxFileExplorer.writeText(supervisor, rootfs, OpenCodeProviderConfig.CONFIG_PATH, merged)) {
                LinuxFileExplorer.WriteResult.Success -> Unit
                LinuxFileExplorer.WriteResult.NotInstalled -> return "Linux 环境尚未安装"
                LinuxFileExplorer.WriteResult.NotFile -> return "配置路径不是文件"
                LinuxFileExplorer.WriteResult.Unreadable -> return "没有权限写入 OpenCode 配置"
                LinuxFileExplorer.WriteResult.CommandFailed -> return "写入 OpenCode 配置失败"
            }
            val identity = if (backend == LinuxExecutionBackend.PROOT) "user" else "root"
            runOneShotShell(
                processSupervisor = supervisor,
                identity = identity,
                command = "/usr/local/bin/opencode reload >/dev/null 2>&1 || true; " +
                    "/usr/local/bin/opencode service restart >/dev/null 2>&1 || true",
                timeoutSeconds = 30,
                environment = distribution.terminalEnvironment,
                linuxRootfsPath = rootfs.absolutePath,
            )
            null
        } finally {
            supervisor.beginClosing()
        }
    }
}
