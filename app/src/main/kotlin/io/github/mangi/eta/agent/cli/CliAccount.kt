package io.github.mangi.eta.agent.cli

import kotlinx.serialization.Serializable

@Serializable
internal enum class CliAccountKind {
    /** 官方账号：保存 CLI 自己生成的登录文件快照。 */
    OFFICIAL,
    /** 官方 API Key。 */
    API_KEY,
    /** 第三方 OpenAI/Anthropic 兼容端点。 */
    THIRD_PARTY,
}

@Serializable
internal data class CliModelInfo(
    val id: String,
    val displayName: String,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val reasoningEfforts: List<String> = emptyList(),
    val defaultReasoningEffort: String = "",
)

@Serializable
internal data class CliAccount(
    val id: String,
    val agentId: String,
    val name: String,
    val kind: CliAccountKind,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    /** 在线获取的模型目录，供模型/上下文/思考等级选择。 */
    val models: List<CliModelInfo> = emptyList(),
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val reasoningEffort: String = "",
    /** Codex 官方登录快照（`auth.json`）。 */
    val authJson: String? = null,
    /** Claude Code 官方登录快照（`.credentials.json`）。 */
    val credentialsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
internal data class CliAccountState(
    val accounts: List<CliAccount> = emptyList(),
    val activeByAgent: Map<String, String> = emptyMap(),
)
