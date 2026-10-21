package io.github.mangi.eta.agent.extension

import org.json.JSONObject

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
    const val PACKAGE = "aisdk:@ai-sdk/openai-compatible"

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
