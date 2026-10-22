package io.github.mangi.eta.hook.system

import android.os.IBinder
import io.github.mangi.eta.core.ModuleConfig
import java.lang.reflect.Method

/**
 * 直接调用 Android Contextual Search binder。
 *
 * 这里刻意不依赖 Xposed 的 ModuleLogger，Root 手机命令通道（app_process）也能直接复用。
 */
internal object CircleToSearchService {
    @Volatile
    private var getServiceMethod: Method? = null

    @Volatile
    private var asInterfaceMethod: Method? = null

    @Volatile
    private var startContextualSearchMethod: StartContextualSearchMethod? = null

    fun isBinderAlive(): Boolean = getContextualSearchBinder()?.isBinderAlive == true

    fun trigger(entryPoint: Int = ModuleConfig.CIRCLE_TO_SEARCH_ENTRYPOINT): Boolean {
        val binder = getContextualSearchBinder() ?: return false
        return runCatching {
            val asInterface = resolveAsInterfaceMethod() ?: return@runCatching false
            val startContextualSearch = resolveStartContextualSearchMethod() ?: return@runCatching false
            val service = asInterface.invoke(null, binder) ?: return@runCatching false
            if (startContextualSearch.hasConfigParameter) {
                startContextualSearch.method.invoke(service, entryPoint, null)
            } else {
                startContextualSearch.method.invoke(service, entryPoint)
            }
            true
        }.getOrDefault(false)
    }

    private fun getContextualSearchBinder(): IBinder? =
        runCatching {
            resolveGetServiceMethod()?.invoke(null, ModuleConfig.CONTEXTUAL_SEARCH_SERVICE) as? IBinder
        }.getOrNull()

    private fun resolveGetServiceMethod(): Method? {
        getServiceMethod?.let { return it }
        return runCatching {
            Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("getService", String::class.java)
                .apply { isAccessible = true }
        }.getOrNull()?.also { getServiceMethod = it }
    }

    private fun resolveAsInterfaceMethod(): Method? {
        asInterfaceMethod?.let { return it }
        return runCatching {
            Class.forName("android.app.contextualsearch.IContextualSearchManager\$Stub")
                .getDeclaredMethod("asInterface", IBinder::class.java)
                .apply { isAccessible = true }
        }.getOrNull()?.also { asInterfaceMethod = it }
    }

    private fun resolveStartContextualSearchMethod(): StartContextualSearchMethod? {
        startContextualSearchMethod?.let { return it }
        val serviceClass = runCatching {
            Class.forName("android.app.contextualsearch.IContextualSearchManager")
        }.getOrNull() ?: return null

        val withConfig = runCatching {
            val configClass = Class.forName("android.app.contextualsearch.ContextualSearchConfig")
            serviceClass.getDeclaredMethod(
                "startContextualSearch",
                Int::class.javaPrimitiveType!!,
                configClass,
            ).apply { isAccessible = true }
        }.getOrNull()
        if (withConfig != null) {
            return StartContextualSearchMethod(withConfig, hasConfigParameter = true)
                .also { startContextualSearchMethod = it }
        }

        return runCatching {
            serviceClass.getDeclaredMethod(
                "startContextualSearch",
                Int::class.javaPrimitiveType!!,
            ).apply { isAccessible = true }
        }.getOrNull()?.let { method ->
            StartContextualSearchMethod(method, hasConfigParameter = false)
        }?.also { startContextualSearchMethod = it }
    }

    private data class StartContextualSearchMethod(
        val method: Method,
        val hasConfigParameter: Boolean,
    )
}
