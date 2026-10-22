package io.github.mangi.eta.hook.system

import android.content.Context
import android.content.Intent
import io.github.mangi.eta.core.HookSupport
import io.github.mangi.eta.core.ModuleConfig
import io.github.mangi.eta.core.ModuleLogger

internal object CircleToSearchInvoker {

    fun isAvailable(
        context: Context,
        logger: ModuleLogger,
        source: String,
        fallbackMessage: String
    ): Boolean {
        if (!HookSupport.isPackageInstalled(context, ModuleConfig.GOOGLE_PACKAGE)) {
            logger.warnThrottled("${source}_cts_google_missing") {
                "$source: Google App 未安装，$fallbackMessage"
            }
            return false
        }

        val intent = Intent(ModuleConfig.CONTEXTUAL_SEARCH_ACTION).setPackage(ModuleConfig.GOOGLE_PACKAGE)
        if (!HookSupport.resolvesActivity(context, intent)) {
            logger.warnThrottled("${source}_cts_entry_missing") {
                "$source: Google App 未暴露 Contextual Search 入口，$fallbackMessage"
            }
            return false
        }

        if (!CircleToSearchService.isBinderAlive()) {
            logger.warnThrottled("${source}_cts_service_missing") {
                "$source: contextual_search service 不可用，$fallbackMessage"
            }
            return false
        }

        return true
    }

    fun trigger(
        logger: ModuleLogger,
        source: String,
        entryPoint: Int = ModuleConfig.CIRCLE_TO_SEARCH_ENTRYPOINT,
    ): Boolean {
        if (!CircleToSearchService.trigger(entryPoint)) {
            logger.errorThrottled(key = "${source}_cts_trigger_failed") {
                "$source: 触发 Circle to Search 失败"
            }
            return false
        }
        logger.debug { "$source: 已触发 Circle to Search" }
        return true
    }

    /** 供 Root 手机命令通道使用；系统服务调用不需要 Xposed logger。 */
    fun trigger(
        source: String,
        entryPoint: Int = ModuleConfig.CIRCLE_TO_SEARCH_ENTRYPOINT,
    ): Boolean = CircleToSearchService.trigger(entryPoint)
}
