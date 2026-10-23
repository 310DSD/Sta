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
internal data class CliAccount(
    val id: String,
    val agentId: String,
    val name: String,
    val kind: CliAccountKind,
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val authJson: String? = null,
    val configToml: String? = null,
    val credentialsJson: String? = null,
    val settingsJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
internal data class CliAccountState(
    val accounts: List<CliAccount> = emptyList(),
    val activeByAgent: Map<String, String> = emptyMap(),
)
