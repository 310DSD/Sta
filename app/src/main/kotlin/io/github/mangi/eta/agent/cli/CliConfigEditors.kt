package io.github.mangi.eta.agent.cli

import org.json.JSONObject

/** Codex `config.toml` 的目标路由；只描述连接信息，不携带整份配置快照。 */
internal sealed interface CodexRoute {
    data object Official : CodexRoute
    data class ApiKey(val model: String?) : CodexRoute
    data class ThirdParty(val baseUrl: String, val apiKey: String, val model: String?) : CodexRoute
}

/** Codex 当前 profile 覆盖了选路；继续写只会得到一个不生效的配置。 */
internal class CodexProfileOverrideException(val profile: String) : Exception()

/**
 * 参考 CC Switch 的“只改关键字段”策略：保留用户已有的 hooks、MCP、profiles、注释与其它
 * 设置，只替换 Sta 管理的选路键和 `[model_providers.eta]` 表。
 */
internal object CodexConfigEditor {
    private const val ROUTE_ID = "eta"
    private const val ROUTE_TABLE = "model_providers.$ROUTE_ID"

    private val MANAGED_ROOT_KEYS = setOf(
        "model",
        "model_provider",
        "openai_base_url",
        "experimental_bearer_token",
    )

    private val ROOT_LINE = Regex("""^(\s*)([A-Za-z0-9_-]+)\s*=\s*(.*)$""")

    fun apply(existing: String, route: CodexRoute): String {
        val lines = existing.lines().toMutableList()
        val profiles = profileBlocks(lines)
        val activeProfile = activeProfile(lines)
        if (activeProfile != null && profiles[activeProfile]?.let(::profileOverridesRoute) == true) {
            throw CodexProfileOverrideException(activeProfile)
        }
        if (route !is CodexRoute.ThirdParty && profiles.any { (_, block) -> profileReferencesRoute(block) }) {
            throw CodexProfileOverrideException("profiles")
        }
        removeTable(lines, ROUTE_TABLE)

        val firstTable = lines.indexOfFirst { tableHeader(it) != null }
        val rootEnd = if (firstTable < 0) lines.size else firstTable
        val root = lines.subList(0, rootEnd).toMutableList()
        val rest = lines.subList(rootEnd, lines.size).toMutableList()

        val desired = when (route) {
            CodexRoute.Official -> emptyMap()
            is CodexRoute.ApiKey -> buildMap {
                route.model?.takeIf(String::isNotBlank)?.let { put("model", quote(it)) }
            }
            is CodexRoute.ThirdParty -> buildMap {
                route.model?.takeIf(String::isNotBlank)?.let { put("model", quote(it)) }
                put("model_provider", quote(ROUTE_ID))
            }
        }

        val written = mutableSetOf<String>()
        val keptRoot = mutableListOf<String>()
        for (line in root) {
            val key = rootKey(line)
            if (key == null || key !in MANAGED_ROOT_KEYS) {
                keptRoot += line
                continue
            }
            val value = desired[key] ?: continue
            if (written.add(key)) keptRoot += replaceValue(line, value)
        }
        for ((key, value) in desired) {
            if (key !in written) keptRoot += "$key = $value"
        }

        val output = keptRoot.toMutableList()
        if (rest.isNotEmpty()) {
            if (output.isNotEmpty() && output.last().isNotBlank()) output += ""
            output += rest
        }
        if (route is CodexRoute.ThirdParty) {
            if (output.isNotEmpty() && output.last().isNotBlank()) output += ""
            output += listOf(
                "[$ROUTE_TABLE]",
                "name = \"Eta\"",
                "base_url = ${quote(route.baseUrl.trimEnd('/'))}",
                "wire_api = \"responses\"",
                "experimental_bearer_token = ${quote(route.apiKey)}",
                "requires_openai_auth = false",
            )
        }

        val joined = output.joinToString("\n").trimEnd()
        return if (joined.isEmpty()) "" else "$joined\n"
    }

    private fun profileBlocks(lines: List<String>): Map<String, List<String>> {
        val blocks = mutableMapOf<String, MutableList<String>>()
        var current: String? = null
        for (line in lines) {
            val header = tableHeader(line)
            if (header != null) {
                current = if (header.startsWith("profiles.")) header.removePrefix("profiles.") else null
                current?.let { blocks.getOrPut(it) { mutableListOf() } }
            } else {
                current?.let { blocks.getValue(it).add(line) }
            }
        }
        return blocks
    }

    private fun activeProfile(lines: List<String>): String? {
        val firstTable = lines.indexOfFirst { tableHeader(it) != null }
        val rootEnd = if (firstTable < 0) lines.size else firstTable
        for (line in lines.subList(0, rootEnd)) {
            val match = ROOT_LINE.matchEntire(line) ?: continue
            if (match.groupValues[2] == "profile") return unquote(match.groupValues[3])
        }
        return null
    }

    private fun profileOverridesRoute(block: List<String>): Boolean = block.any { line ->
        val match = ROOT_LINE.matchEntire(line) ?: return@any false
        match.groupValues[2] in setOf("model_provider", "openai_base_url", "experimental_bearer_token")
    }

    private fun profileReferencesRoute(block: List<String>): Boolean = block.any { line ->
        val match = ROOT_LINE.matchEntire(line) ?: return@any false
        match.groupValues[2] == "model_provider" && unquote(match.groupValues[3]) == ROUTE_ID
    }

    private fun unquote(value: String): String =
        value.substringBefore('#').trim().trim('"', '\'')

    private fun removeTable(lines: MutableList<String>, table: String) {
        var index = 0
        while (index < lines.size) {
            val header = tableHeader(lines[index])
            if (header == table || header?.startsWith("$table.") == true) {
                val start = index
                index++
                while (index < lines.size && tableHeader(lines[index]) == null) index++
                lines.subList(start, index).clear()
            } else {
                index++
            }
        }
    }

    private fun tableHeader(line: String): String? {
        val clean = line.trim().substringBefore('#').trim()
        if (!clean.startsWith("[")) return null
        val end = clean.indexOf(']')
        if (end <= 1) return null
        return clean.substring(1, end).trim()
    }

    private fun rootKey(line: String): String? {
        if (tableHeader(line) != null) return null
        val match = ROOT_LINE.matchEntire(line) ?: return null
        return match.groupValues[2]
    }

    private fun replaceValue(line: String, value: String): String {
        val match = ROOT_LINE.matchEntire(line) ?: return line
        val indent = match.groupValues[1]
        val key = match.groupValues[2]
        val rest = match.groupValues[3]
        val comment = rest.indexOf('#').takeIf { it >= 0 }?.let { " ${rest.substring(it)}" }.orEmpty()
        return "$indent$key = $value$comment"
    }

    private fun quote(value: String): String = "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n") + "\""
}

/** Claude Code 的 `settings.json` 合并；只动 `env` 里 Sta 管理的四个键。 */
internal object ClaudeSettingsEditor {
    private val MANAGED_ENV = listOf(
        "ANTHROPIC_BASE_URL",
        "ANTHROPIC_AUTH_TOKEN",
        "ANTHROPIC_API_KEY",
        "ANTHROPIC_MODEL",
        "CLAUDE_CODE_OAUTH_TOKEN",
        "CLAUDE_CODE_USE_BEDROCK",
        "CLAUDE_CODE_USE_VERTEX",
        "CLAUDE_CODE_USE_FOUNDRY",
    )

    fun merge(
        existing: String?,
        kind: CliAccountKind,
        apiKey: String,
        baseUrl: String,
        model: String,
    ): String {
        val root = parse(existing)
        val env = root.optJSONObject("env") ?: JSONObject()
        MANAGED_ENV.forEach(env::remove)
        when (kind) {
            CliAccountKind.OFFICIAL -> Unit
            CliAccountKind.API_KEY -> env.put("ANTHROPIC_API_KEY", apiKey)
            CliAccountKind.THIRD_PARTY -> {
                env.put("ANTHROPIC_BASE_URL", baseUrl.trimEnd('/'))
                env.put("ANTHROPIC_AUTH_TOKEN", apiKey)
            }
        }
        if (model.isNotBlank()) env.put("ANTHROPIC_MODEL", model)
        if (env.length() == 0) root.remove("env") else root.put("env", env)
        return root.toString(2)
    }

    fun stripManaged(existing: String?): String {
        val root = parse(existing)
        val env = root.optJSONObject("env") ?: return root.toString(2)
        MANAGED_ENV.forEach(env::remove)
        if (env.length() == 0) root.remove("env") else root.put("env", env)
        return root.toString(2)
    }

    /** 解析失败必须抛给调用方，不能回退成空对象覆盖用户配置。 */
    fun parse(existing: String?): JSONObject =
        if (existing.isNullOrBlank()) JSONObject() else JSONObject(existing)
}

/** 登录文件格式校验与 API Key 登录文件生成。 */
internal object CliAuthEditor {
    fun codexApiKey(key: String): String = JSONObject()
        .put("OPENAI_API_KEY", key)
        .put("auth_mode", "apikey")
        .toString(2)

    fun isOfficialCodexAuth(json: String?): Boolean {
        val root = runCatching { JSONObject(json ?: return false) }.getOrNull() ?: return false
        val tokens = root.optJSONObject("tokens") ?: return false
        return tokens.optString("access_token").isNotBlank() ||
            tokens.optString("refresh_token").isNotBlank()
    }

    fun isOfficialClaudeCredentials(json: String?): Boolean {
        val root = runCatching { JSONObject(json ?: return false) }.getOrNull() ?: return false
        val oauth = root.optJSONObject("claudeAiOauth")
        if (oauth != null) {
            return oauth.optString("accessToken").isNotBlank() ||
                oauth.optString("refreshToken").isNotBlank()
        }
        return root.optString("accessToken").isNotBlank() ||
            root.optString("refreshToken").isNotBlank()
    }
}
