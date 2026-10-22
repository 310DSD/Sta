package io.github.mangi.eta.agent.extension

import io.github.mangi.eta.data.model.AnthropicProviderSetting
import io.github.mangi.eta.data.model.CustomBody
import io.github.mangi.eta.data.model.CustomHeader
import io.github.mangi.eta.data.model.CustomProviderSetting
import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.ReasoningEffort
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import org.json.JSONTokener

/**
 * OpenCode 2.1.2 的 Web 设置页把“自定义提供商”保存实现成了无条件抛错的占位代码，
 * 服务端也没有配置写入 API。官方可用路径是直接编辑 `opencode.json` 的 `providers`
 * 段；这里把表单转换成同一种结构，绕开上游损坏的对话框。
 */
internal data class OpenCodeProviderDraft(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelIds: List<String>,
)

internal object OpenCodeProviderConfig {
    const val CONFIG_PATH = "/root/.config/opencode/opencode.json"
    const val PACKAGE = "@opencode/ai/providers/openai-compatible"

    /** 返回合并后的完整 JSON；输入为空时创建最小配置。 */
    fun merge(existingJson: String?, draft: OpenCodeProviderDraft): String {
        require(draft.id.matches(Regex("[a-z0-9_-]+"))) { "provider id" }
        require(draft.name.isNotBlank()) { "name" }
        require(draft.baseUrl.startsWith("http://") || draft.baseUrl.startsWith("https://")) { "baseURL" }
        require(draft.modelIds.isNotEmpty()) { "models" }

        val root = if (existingJson.isNullOrBlank()) {
            JSONObject()
        } else {
            JSONObject(stripJsonComments(existingJson))
        }
        if (!root.has("\$schema")) root.put("\$schema", "https://opencode.ai/config.json")
        val providers = root.optJSONObject("providers") ?: JSONObject().also { root.put("providers", it) }

        val settings = JSONObject().apply {
            put("baseURL", draft.baseUrl.trim())
            if (draft.apiKey.isNotBlank()) put("apiKey", draft.apiKey.trim())
        }
        val models = JSONObject().apply {
            draft.modelIds.forEach { modelId ->
                put(modelId, JSONObject().put("name", modelId))
            }
        }
        providers.put(
            draft.id,
            JSONObject().apply {
                put("name", draft.name.trim())
                put("package", PACKAGE)
                put("settings", settings)
                put("models", models)
            },
        )
        return root.toString(2)
    }

    /**
     * 把 Eta 的 provider/model 数据映射成 OpenCode 2.x 的 `providers` 配置。
     * 只同步启用且有模型的 provider；上下文、视觉输入、工具、自定义头和请求体
     * 以及 Eta 的思考等级都写入对应字段。
     */
    fun mergeEtaProviders(existingJson: String?, providers: List<ProviderSetting>): String {
        val root = parseRoot(existingJson)
        if (!root.has("\$schema")) root.put("\$schema", "https://opencode.ai/config.json")
        val targetProviders = root.optJSONObject("providers") ?: JSONObject().also {
            root.put("providers", it)
        }
        val usedIds = mutableSetOf<String>()
        syncableProviders(providers).forEach { provider ->
                val openCodeId = findExistingProviderId(targetProviders, provider)
                    ?: uniqueProviderId(provider, usedIds)
                val generated = providerJson(provider)
                val existing = targetProviders.optJSONObject(openCodeId)
                targetProviders.put(
                    openCodeId,
                    if (existing == null) generated else mergeProvider(existing, generated),
                )
            }
        return root.toString(2)
    }

    fun syncSummary(providers: List<ProviderSetting>): Pair<Int, Int> {
        val enabled = syncableProviders(providers)
        return enabled.size to enabled.sumOf { provider -> provider.models.count { it.isEnabled } }
    }

    /**
     * 没有 API key、也没有认证 header 的远端 built-in provider 不应覆盖 OpenCode 自己的
     * 同名 provider（否则用户未在 Eta 配置 OpenAI key 时会把 OpenCode 的 native 配置冲掉）。
     * 本机 loopback provider 通常不需要 key，仍然同步。
     */
    internal fun syncableProviders(providers: List<ProviderSetting>): List<ProviderSetting> =
        providers.filter { provider ->
            provider.isEnabled &&
                provider.baseUrl.isNotBlank() &&
                provider.models.any { it.isEnabled } &&
                (provider.apiKey.isNotBlank() || provider.customHeaders.isNotEmpty())
        }

    private fun providerJson(provider: ProviderSetting): JSONObject = JSONObject().apply {
        put("name", provider.name)
        put("package", packageName(provider))
        put("settings", JSONObject().apply {
            put("baseURL", provider.baseUrl.trim().trimEnd('/'))
            if (provider.apiKey.isNotBlank()) put("apiKey", provider.apiKey)
        })
        if (provider.customHeaders.isNotEmpty()) put("headers", headersJson(provider.customHeaders))
        if (provider.customBody.isNotEmpty()) put("body", bodyJson(provider.customBody))
        put("models", JSONObject().apply {
            provider.models.filter { it.isEnabled }.forEach { model ->
                put(modelKey(model), modelJson(model))
            }
        })
    }

    /**
     * 保留用户手工添加的 provider 连接信息。同步只补充/更新模型元数据，不能把手工配置里
     * 可用的 baseURL、API key、headers 或 body 冲掉；本地网关通常正是靠这些字段工作。
     */
    private fun mergeProvider(existing: JSONObject, generated: JSONObject): JSONObject {
        val merged = JSONObject(existing.toString())
        if (!generated.optString("name").isNullOrBlank()) merged.put("name", generated.optString("name"))
        if (!generated.optString("package").isNullOrBlank()) merged.put("package", generated.optString("package"))

        val existingSettings = existing.optJSONObject("settings")
        val generatedSettings = generated.optJSONObject("settings")
        if (generatedSettings != null) {
            val settings = JSONObject(generatedSettings.toString())
            existingSettings?.keys()?.forEach { key ->
                val value = existingSettings.opt(key)
                if (value != null && value != JSONObject.NULL && value.toString().isNotBlank()) {
                    settings.put(key, value)
                }
            }
            merged.put("settings", settings)
        }

        mergeObjectField(existing, generated, merged, "headers")
        mergeObjectField(existing, generated, merged, "extraBody")
        mergeObjectField(existing, generated, merged, "body")

        val models = merged.optJSONObject("models") ?: JSONObject().also { merged.put("models", it) }
        generated.optJSONObject("models")?.keys()?.forEach { key ->
            val generatedModel = generated.getJSONObject("models").getJSONObject(key)
            val existingModel = models.optJSONObject(key)
            models.put(key, if (existingModel == null) generatedModel else mergeModel(existingModel, generatedModel))
        }
        return merged
    }

    private fun mergeObjectField(
        existing: JSONObject,
        generated: JSONObject,
        target: JSONObject,
        key: String,
    ) {
        val existingValue = existing.optJSONObject(key)
        val generatedValue = generated.optJSONObject(key)
        if (existingValue == null && generatedValue == null) return
        val merged = JSONObject(generatedValue?.toString() ?: "{}")
        existingValue?.keys()?.forEach { nestedKey ->
            val value = existingValue.opt(nestedKey)
            if (value != null && value != JSONObject.NULL && value.toString().isNotBlank()) {
                merged.put(nestedKey, value)
            }
        }
        target.put(key, merged)
    }

    private fun mergeModel(existing: JSONObject, generated: JSONObject): JSONObject {
        val merged = JSONObject(generated.toString())
        // 手工模型配置里的自定义 header/body 优先；其余 Eta 元数据继续由 generated 提供。
        mergeObjectField(existing, generated, merged, "headers")
        mergeObjectField(existing, generated, merged, "body")
        mergeObjectField(existing, generated, merged, "extraBody")
        existing.optJSONObject("settings")?.let { settings ->
            val generatedSettings = generated.optJSONObject("settings")
            val mergedSettings = JSONObject(generatedSettings?.toString() ?: "{}")
            settings.keys().forEach { key ->
                val value = settings.opt(key)
                if (value != null && value != JSONObject.NULL && value.toString().isNotBlank()) {
                    mergedSettings.put(key, value)
                }
            }
            merged.put("settings", mergedSettings)
        }
        return merged
    }

    private fun modelJson(model: Model): JSONObject = JSONObject().apply {
        put("name", model.displayName)
        put("modelID", model.modelId.ifBlank { model.id })
        val context = model.contextWindowOverride ?: model.contextWindow
        if (context != null && context > 0) {
            put("limit", JSONObject().put("context", context))
        }
        val input = buildSet {
            addAll(model.inputModalities)
            if (model.supportsVision) add(Model.IMAGE_MODALITY)
        }.toList()
        put("capabilities", JSONObject().apply {
            put("tools", model.supportsTools)
            put("input", input)
            put("output", model.outputModalities.ifEmpty { listOf(Model.TEXT_MODALITY) })
            reasoningCapabilities(model)?.let { put("reasoning", it) }
        })
        val variants = reasoningVariants(model)
        if (variants != null && variants.length() > 0) put("variants", variants)
        if (model.customHeaders.isNotEmpty()) put("headers", headersJson(model.customHeaders))
        if (model.customBody.isNotEmpty()) put("body", bodyJson(model.customBody))
    }

    private fun reasoningCapabilities(model: Model): JSONObject? {
        if (!model.supportsReasoning) return null
        val capabilities = model.effectiveReasoningCapabilities
        val efforts = capabilities?.selectableEfforts
            ?.filter { it != ReasoningEffort.DEFAULT && it != ReasoningEffort.OFF }
            ?.map { it.wireValue }
            .orEmpty()
        return JSONObject().apply {
            put("supported", true)
            if (capabilities?.canDisable == true) put("toggle", true)
            if (efforts.isNotEmpty()) put("effort", org.json.JSONArray(efforts))
            if (capabilities?.supportsBudget == true && capabilities.maxBudgetTokens != null) {
                put("budget", JSONObject().put("max", capabilities.maxBudgetTokens))
            }
        }
    }

    private fun reasoningVariants(model: Model): org.json.JSONArray? {
        val capabilities = model.effectiveReasoningCapabilities ?: return null
        val efforts = capabilities.selectableEfforts
            .filter { it != ReasoningEffort.DEFAULT }
            .distinctBy { it.wireValue }
        if (efforts.isEmpty()) return null
        return org.json.JSONArray().apply {
            efforts.forEach { effort ->
                put(JSONObject().apply {
                    put("id", effort.wireValue)
                    put(
                        "settings",
                        JSONObject().put(
                            "reasoningEffort",
                            if (effort == ReasoningEffort.OFF) "none" else effort.wireValue,
                        ),
                    )
                })
            }
        }
    }

    private fun packageName(provider: ProviderSetting): String = when {
        provider is AnthropicProviderSetting -> "@opencode/ai/providers/anthropic"
        provider is OpenAiCompatibleProviderSetting ->
            if (provider.endpointMode == OpenAiEndpointMode.RESPONSES) "@opencode/ai/providers/openai-compatible/responses"
            else PACKAGE
        provider is CustomProviderSetting ->
            if (provider.endpointMode == OpenAiEndpointMode.RESPONSES) "@opencode/ai/providers/openai-compatible/responses"
            else PACKAGE
        else -> PACKAGE
    }

    /**
     * 如果用户手工添加过同一个本地网关（ID 可能不同），按规范化 baseURL 复用原 provider ID，
     * 不新建一个指向相同地址的新 provider；这样同步只会补充模型，不会让用户选到另一份坏配置。
     */
    private fun findExistingProviderId(
        providers: JSONObject,
        provider: ProviderSetting,
    ): String? {
        val baseUrl = provider.baseUrl.trim().trimEnd('/')
        val modelKeys = provider.models.filter { it.isEnabled }.map(::modelKey).toSet()
        val keys = providers.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val existingProvider = providers.optJSONObject(key) ?: continue
            val settings = existingProvider.optJSONObject("settings")
            if (settings?.optString("baseURL")?.trim()?.trimEnd('/') == baseUrl) return key
            val existingModels = existingProvider.optJSONObject("models") ?: continue
            if (modelKeys.any { existingModels.has(it) }) return key
        }
        return null
    }

    private fun uniqueProviderId(provider: ProviderSetting, used: MutableSet<String>): String {
        val raw = provider.id.lowercase().replace(Regex("[^a-z0-9_-]+"), "-").trim('-')
        val base = raw.ifBlank {
            "eta-" + Integer.toHexString(provider.id.hashCode())
        }
        val normalizedBase = if (base.firstOrNull()?.isLetter() == true) base else "eta-$base"
        var candidate = normalizedBase
        var suffix = 2
        while (!used.add(candidate)) {
            candidate = "$normalizedBase-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun modelKey(model: Model): String = model.modelId.ifBlank { model.id }

    private fun headersJson(headers: List<CustomHeader>): JSONObject = JSONObject().apply {
        headers.forEach { put(it.name, it.value) }
    }

    private fun bodyJson(body: List<CustomBody>): JSONObject = JSONObject().apply {
        body.forEach { put(it.key, toJsonValue(it.value)) }
    }

    private fun toJsonValue(value: kotlinx.serialization.json.JsonElement): Any = when (value) {
        JsonNull -> JSONObject.NULL
        is JsonPrimitive -> if (value.isString) value.content else JSONTokener(value.toString()).nextValue()
        else -> JSONTokener(value.toString()).nextValue()
    }

    private fun parseRoot(existingJson: String?): JSONObject =
        if (existingJson.isNullOrBlank()) {
            JSONObject()
        } else {
            JSONObject(stripJsonComments(existingJson))
        }

    fun parseModels(raw: String): List<String> =
        raw.split(',', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** 允许常见的 JSONC 注释；字符串里的 `//` 不会被误删。 */
    private fun stripJsonComments(input: String): String {
        val output = StringBuilder(input.length)
        var inString = false
        var escaped = false
        var index = 0
        while (index < input.length) {
            val ch = input[index]
            if (inString) {
                output.append(ch)
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                index += 1
                continue
            }
            when {
                ch == '"' -> {
                    inString = true
                    output.append(ch)
                    index += 1
                }
                ch == '/' && input.getOrNull(index + 1) == '/' -> {
                    index += 2
                    while (index < input.length && input[index] != '\n') index += 1
                }
                ch == '/' && input.getOrNull(index + 1) == '*' -> {
                    index += 2
                    while (index + 1 < input.length && !(input[index] == '*' && input[index + 1] == '/')) {
                        if (input[index] == '\n') output.append('\n')
                        index += 1
                    }
                    index = (index + 2).coerceAtMost(input.length)
                }
                else -> {
                    output.append(ch)
                    index += 1
                }
            }
        }
        return output.toString()
    }
}
