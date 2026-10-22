package io.github.mangi.eta.agent.phone

import io.github.mangi.eta.core.ModuleConfig
import io.github.mangi.eta.hook.system.CircleToSearchService
import kotlin.system.exitProcess

/**
 * 给其他 Root 程序使用的一圈即搜命令行入口。
 *
 * 用法（在 Android root shell 中）：
 * ```
 * APK="$(pm path io.github.mangi.eta | sed 's/^package://')"
 * CLASSPATH="$APK" app_process /system/bin \
 *   io.github.mangi.eta.agent.phone.CircleSearchCommand [entryPoint]
 * ```
 *
 * 默认 entryPoint 为 [ModuleConfig.CIRCLE_TO_SEARCH_ENTRYPOINT]；成功输出 OK 并返回 0，
 * 失败输出 CIRCLE_TO_SEARCH_UNAVAILABLE 并返回 1。
 */
internal object CircleSearchCommand {
    @JvmStatic
    fun main(args: Array<String>) {
        val entryPoint = args.firstOrNull()?.toIntOrNull()
            ?: ModuleConfig.CIRCLE_TO_SEARCH_ENTRYPOINT
        if (!CircleToSearchService.trigger(entryPoint)) {
            System.err.println("CIRCLE_TO_SEARCH_UNAVAILABLE")
            exitProcess(1)
        }
        println("OK")
        exitProcess(0)
    }
}
