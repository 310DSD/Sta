package io.github.mangi.eta.agent.acp

import android.content.Context
import android.util.Base64
import io.github.mangi.eta.agent.terminal.InstallerShellRunner
import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.agent.terminal.TerminalRuntime
import io.github.mangi.eta.agent.terminal.terminalEnvironment

/**
 * 把随 APK 分发的 ACP bridge 与静态界面写入 Linux rootfs。
 *
 * 不在 Android 侧直接拼 rootfs 路径：通过实际运行环境（chroot / PRoot）的 shell 写入，
 * 保证终端里看到的 `/opt/eta/acp` 与 App 启动的 bridge 是同一份文件。
 *
 * 每个文件通过 stdin 传给 `base64 -d`，不把整份 base64 放进 `sh -c` 的参数里：
 * Android 的单个 exec 参数有上限，旧实现把几 MB 的脚本塞进一个参数时会在创建
 * 后台任务之前直接失败，表现为“没有日志、无法开始后台任务”。
 */
internal object AcpGuiInstaller {
    private const val VERSION = "6"
    @Volatile
    var lastError: String? = null
        private set
    private const val ROOT = "/opt/eta/acp"
    private val FILES = listOf(
        "acp/server.mjs" to "$ROOT/server.mjs",
        "acp/ui/index.html" to "$ROOT/ui/index.html",
        "acp/ui/app.js" to "$ROOT/ui/app.js",
        "acp/ui/style.css" to "$ROOT/ui/style.css",
        "acp/ui/favicon.svg" to "$ROOT/ui/favicon.svg",
        "acp/ui/fonts/geist-latin-wght-normal.woff2" to "$ROOT/ui/fonts/geist-latin-wght-normal.woff2",
        "acp/ui/fonts/dm-sans-latin-wght-normal.woff2" to "$ROOT/ui/fonts/dm-sans-latin-wght-normal.woff2",
        "acp/ui/fonts/instrument-serif-latin-400-normal.woff2" to "$ROOT/ui/fonts/instrument-serif-latin-400-normal.woff2",
        "acp/ui/fonts/instrument-serif-latin-400-italic.woff2" to "$ROOT/ui/fonts/instrument-serif-latin-400-italic.woff2",
    )

    suspend fun install(
        context: Context,
        distribution: LinuxDistribution,
        backend: LinuxExecutionBackend,
    ): Boolean {
        lastError = null
        val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution, backend)
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) {
            lastError = "Linux rootfs 尚未就绪"
            return false
        }
        val environment = distribution.terminalEnvironment
        val identity = TerminalRuntime.defaultIdentity(environment, rootfs.absolutePath)
        val setup = InstallerShellRunner.run(
            command = "umask 077; mkdir -p ${shellQuote("$ROOT/ui/fonts")}",
            timeoutSeconds = 15,
            environment = environment,
            linuxRootfsPath = rootfs.absolutePath,
            identity = identity,
        )
        if (setup.exitCode != 0) {
            lastError = "创建 ACP 目录失败（exit=${setup.exitCode}）：${setup.output.take(240)}"
            return false
        }

        for ((asset, target) in FILES) {
            val bytes = runCatching {
                context.assets.open(asset).use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                lastError = "读取内置资源失败：$asset"
                return false
            }
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP).toByteArray(Charsets.US_ASCII)
            val parent = target.substringBeforeLast('/')
            val result = InstallerShellRunner.run(
                command = "mkdir -p ${shellQuote(parent)} && base64 -d > ${shellQuote(target)}",
                timeoutSeconds = 20,
                environment = environment,
                linuxRootfsPath = rootfs.absolutePath,
                identity = identity,
                stdin = encoded,
            )
            if (result.exitCode != 0) {
                lastError = "写入 $target 失败（exit=${result.exitCode}）：${result.output.take(240)}"
                return false
            }
        }

        val versionResult = InstallerShellRunner.run(
            command = "printf '%s\\n' ${shellQuote(VERSION)} > ${shellQuote("$ROOT/.version")}",
            timeoutSeconds = 15,
            environment = environment,
            linuxRootfsPath = rootfs.absolutePath,
            identity = identity,
        )
        if (versionResult.exitCode != 0) {
            lastError = "写入 ACP 版本标记失败（exit=${versionResult.exitCode}）"
            return false
        }
        return true
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
