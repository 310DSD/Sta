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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 把随 APK 分发的 ACP bridge 与静态界面写入 Linux rootfs。
 *
 * 不在 Android 侧直接拼 rootfs 路径：通过实际运行环境（chroot / PRoot）的 shell 写入，
 * 保证终端里看到的 `/opt/eta/acp` 与 App 启动的 bridge 是同一份文件。
 *
 * 所有文件通过 stdin 传给一个 `base64 -d` 写入循环，不把整份 base64 放进
 * `sh -c` 的参数里：Android 的单个 exec 参数有上限，旧实现把数百 KB 的脚本塞进
 * 一个参数时会在创建后台任务之前直接失败，表现为“没有日志、无法开始后台任务”。
 */
internal object AcpGuiInstaller {
    private const val VERSION = "8"
    private const val ROOT = "/opt/eta/acp"
    private val installMutex = Mutex()
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
        "acp/ui/fonts/anthropic-sans.woff2" to "$ROOT/ui/fonts/anthropic-sans.woff2",
        "acp/ui/fonts/anthropic-serif.woff2" to "$ROOT/ui/fonts/anthropic-serif.woff2",
        "acp/ui/fonts/anthropic-mono.woff2" to "$ROOT/ui/fonts/anthropic-mono.woff2",
        "acp/ui/fonts/noto-serif-sc-ui.woff2" to "$ROOT/ui/fonts/noto-serif-sc-ui.woff2",
    )

    @Volatile
    var lastError: String? = null
        private set

    suspend fun install(
        context: Context,
        distribution: LinuxDistribution,
        backend: LinuxExecutionBackend,
    ): Boolean = installMutex.withLock {
        installLocked(context, distribution, backend)
    }

    private suspend fun installLocked(
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
        val installed = InstallerShellRunner.run(
            command = "cat ${shellQuote("$ROOT/.version")} 2>/dev/null || true",
            timeoutSeconds = 15,
            environment = environment,
            linuxRootfsPath = rootfs.absolutePath,
            identity = identity,
        )
        if (installed.exitCode == 0 && installed.output.trim() == VERSION) return true

        val payload = ByteArrayOutputStream()
        val script = buildString {
            appendLine("set -e")
            appendLine("umask 077")
            appendLine("mkdir -p ${shellQuote("$ROOT/ui/fonts")}")
            appendLine("while IFS='\t' read -r target encoded; do")
            appendLine("  printf '%s' \"${'$'}encoded\" | base64 -d > \"${'$'}target\"")
            appendLine("done")
            appendLine("printf '%s\\n' ${shellQuote(VERSION)} > ${shellQuote("$ROOT/.version")}")
        }
        for ((asset, target) in FILES) {
            val bytes = runCatching {
                context.assets.open(asset).use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                lastError = "读取内置资源失败：$asset"
                return false
            }
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            payload.write(target.toByteArray(Charsets.UTF_8))
            payload.write('\t'.code)
            payload.write(encoded.toByteArray(Charsets.US_ASCII))
            payload.write('\n'.code)
        }
        val installResult = InstallerShellRunner.run(
            command = script,
            timeoutSeconds = 90,
            environment = environment,
            linuxRootfsPath = rootfs.absolutePath,
            identity = identity,
            stdin = payload.toByteArray(),
        )
        if (installResult.exitCode != 0) {
            lastError = "写入 ACP 资源失败（exit=${installResult.exitCode}）：${installResult.output.take(240)}"
            return false
        }
        return true
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
