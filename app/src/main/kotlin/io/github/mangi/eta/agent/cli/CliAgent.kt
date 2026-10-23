package io.github.mangi.eta.agent.cli

/** Codex / Claude Code 的终端型 Agent；没有官方 Web UI，统一走 Eta 的 PTY 终端。 */
internal enum class CliAgent(
    val id: String,
    val displayName: String,
    val packageProfileId: String,
    val command: String,
    val homeDir: String,
    val authFile: String,
    val configFile: String,
    val credentialsFile: String,
    val settingsFile: String,
) {
    CODEX(
        id = "codex",
        displayName = "Codex",
        packageProfileId = "codex",
        command = "codex",
        homeDir = "/root/.codex",
        authFile = "/root/.codex/auth.json",
        configFile = "/root/.codex/config.toml",
        credentialsFile = "",
        settingsFile = "",
    ),
    CLAUDE_CODE(
        id = "claude-code",
        displayName = "Claude Code",
        packageProfileId = "claude-code",
        command = "claude",
        homeDir = "/root/.claude",
        authFile = "",
        configFile = "",
        credentialsFile = "/root/.claude/.credentials.json",
        settingsFile = "/root/.claude/settings.json",
    ),
    ;

    companion object {
        fun fromId(id: String?): CliAgent? = entries.firstOrNull { it.id == id }
    }
}
