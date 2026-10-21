package io.github.mangi.eta.agent.extension

/**
 * External coding agents that Eta can install into its Linux environment and expose through a
 * mobile-friendly web UI.
 *
 * The catalogue is deliberately small: each entry must have an installable Linux ARM64 build, a
 * headless web mode that prints a loopback URL, and a TUI fallback for the terminal. Kimi Code was
 * the previous single choice; OpenCode and ZCode replace it as the two supported extension agents.
 */
internal enum class ExtensionAgent(
    val id: String,
    val displayName: String,
    val packageProfileId: String,
    /** Command started by the daemon supervisor. Keep it fully non-interactive. */
    val webCommand: String,
    /** Commands accepted when looking for an already-running instance (including legacy forms). */
    val acceptedCommands: Set<String>,
    /** Loopback URL printed by the agent's web mode. */
    val webUrlPattern: Regex,
    /** Command users can type in Eta's terminal for the TUI. */
    val terminalCommand: String,
) {
    OPENCODE(
        id = "opencode",
        displayName = "OpenCode",
        packageProfileId = "opencode",
        webCommand = "opencode web --hostname 127.0.0.1 --port 4096",
        acceptedCommands = setOf(
            "opencode web --hostname 127.0.0.1 --port 4096",
            "opencode web",
        ),
        webUrlPattern = Regex("""http://(?:127\.0\.0\.1|localhost):\d+(?:/[^\s\u001B]*)?"""),
        terminalCommand = "opencode",
    ),
    ZCODE(
        id = "zcode",
        displayName = "ZCode",
        packageProfileId = "zcode",
        webCommand = "zcode --web --workspace /workspace --port 3030 --host 127.0.0.1 --no-open",
        acceptedCommands = setOf(
            "zcode --web --workspace /workspace --port 3030 --host 127.0.0.1 --no-open",
            "zcode --web",
        ),
        webUrlPattern = Regex("""http://(?:127\.0\.0\.1|localhost):\d+(?:/[^\s\u001B]*)?"""),
        terminalCommand = "zcode",
    ),
    ;

    companion object {
        fun fromId(id: String?): ExtensionAgent =
            entries.firstOrNull { it.id == id } ?: OPENCODE
    }
}
