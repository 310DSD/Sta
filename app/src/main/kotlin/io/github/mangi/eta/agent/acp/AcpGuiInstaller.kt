package io.github.mangi.eta.agent.acp

import android.content.Context
import android.util.Base64
import io.github.mangi.eta.agent.terminal.InstallerShellRunner
import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.agent.terminal.TerminalRuntime
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import java.io.ByteArrayOutputStream

/**
 * 把随 APK 分发的 ACP bridge 与静态界面写入 Linux rootfs。
 *
 * 不在 Android 侧直接拼 rootfs 路径：通过实际运行环境（chroot / PRoot）的 shell 写入，
 * 保证终端里看到的 `/opt/eta/acp` 与 App 启动的 bridge 是同一份文件。
 */
internal object AcpGuiInstaller {
    private const val VERSION = "5"
    private val FILES = listOf(
        "acp/server.mjs" to "/opt/eta/acp/server.mjs",
        "acp/ui/index.html" to "/opt/eta/acp/ui/index.html",
        "acp/ui/app.js" to "/opt/eta/acp/ui/app.js",
        "acp/ui/style.css" to "/opt/eta/acp/ui/style.css",
        "acp/ui/favicon.svg" to "/opt/eta/acp/ui/favicon.svg",
        "acp/ui/fonts/geist-latin-wght-normal.woff2" to "/opt/eta/acp/ui/fonts/geist-latin-wght-normal.woff2",
        "acp/ui/fonts/dm-sans-latin-wght-normal.woff2" to "/opt/eta/acp/ui/fonts/dm-sans-latin-wght-normal.woff2",
        "acp/ui/fonts/instrument-serif-latin-400-normal.woff2" to "/opt/eta/acp/ui/fonts/instrument-serif-latin-400-normal.woff2",
        "acp/ui/fonts/instrument-serif-latin-400-italic.woff2" to "/opt/eta/acp/ui/fonts/instrument-serif-latin-400-italic.woff2",
    )

    suspend fun install(
        context: Context,
        distribution: LinuxDistribution,
        backend: LinuxExecutionBackend,
    ): Boolean {
        val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution, backend)
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) return false
        val command = try {
            buildCommand(context)
        } catch (_: Throwable) {
            return false
        }
        val environment = distribution.terminalEnvironment
        val identity = TerminalRuntime.defaultIdentity(environment, rootfs.absolutePath)
        val result = InstallerShellRunner.run(
            command = command,
            timeoutSeconds = 30,
            environment = environment,
            linuxRootfsPath = rootfs.absolutePath,
            identity = identity,
        )
        return result.exitCode == 0
    }

    private fun buildCommand(context: Context): String {
        val command = StringBuilder()
        command.append("umask 077\n")
        command.append("mkdir -p /opt/eta/acp/ui/fonts\n")
        FILES.forEach { (asset, target) ->
            val bytes = ByteArrayOutputStream().use { output ->
                context.assets.open(asset).use { input -> input.copyTo(output) }
                output.toByteArray()
            }
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            command.append("printf '%s' '")
                .append(encoded)
                .append("' | base64 -d > ")
                .append(target)
                .append('\n')
        }
        command.append("printf '%s\\n' '")
            .append(VERSION)
            .append("' > /opt/eta/acp/.version\n")
        return command.toString()
    }
}
