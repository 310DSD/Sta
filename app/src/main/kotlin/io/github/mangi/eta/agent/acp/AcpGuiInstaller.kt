package io.github.mangi.eta.agent.acp

import android.content.Context
import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import java.io.File

/**
 * 把随 APK 分发的 ACP bridge 与静态界面写入 Linux rootfs。
 *
 * Node 进程运行在 rootfs 内，不能直接读取 APK assets；因此启动前把少量文本资源落到
 * `/opt/eta/acp`。资源很小（几十 KB），不涉及用户数据。
 */
internal object AcpGuiInstaller {
    private const val VERSION = "1"
    private val FILES = listOf(
        "acp/server.mjs" to "server.mjs",
        "acp/ui/index.html" to "ui/index.html",
        "acp/ui/app.js" to "ui/app.js",
        "acp/ui/style.css" to "ui/style.css",
    )

    fun install(context: Context, distribution: LinuxDistribution, backend: LinuxExecutionBackend): Boolean {
        val rootfs = LinuxEnvironmentPaths.rootfsDir(context, distribution, backend)
        if (!LinuxEnvironmentPaths.rootfsReady(rootfs.absolutePath)) return false
        val target = File(rootfs, "opt/eta/acp")
        val marker = File(target, ".version")
        if (marker.isFile && marker.readText().trim() == VERSION && FILES.all { File(target, it.second).isFile }) {
            return true
        }
        return runCatching {
            target.mkdirs()
            File(target, "ui").mkdirs()
            FILES.forEach { (asset, relative) ->
                val destination = File(target, relative)
                destination.parentFile?.mkdirs()
                context.assets.open(asset).use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                destination.setReadable(true, false)
            }
            marker.writeText(VERSION)
            true
        }.getOrDefault(false)
    }
}
