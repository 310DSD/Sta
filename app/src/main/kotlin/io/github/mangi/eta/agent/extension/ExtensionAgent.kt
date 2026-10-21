package io.github.mangi.eta.agent.extension

/**
 * External coding agents that Eta can install into its Linux environment and expose through a
 * mobile-friendly web UI.
 *
 * The catalogue is deliberately small: each entry must have an installable Linux ARM64 build, a
 * headless web mode that prints a loopback URL, and a TUI fallback for the terminal. Kimi Code was
 * the previous single choice; OpenCode and DeepSeek Harness replace it as the two supported
 * extension agents. ZCode's official Web CLI is source-build/self-host only, so it is not shipped.
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
    /**
     * Optional command that turns the running server URL into a one-time browser login URL.
     * OpenCode 2.x protects `serve` with an auto-generated password and exposes `pair` for this.
     */
    val pairCommand: ((String) -> String)? = null,
) {
    OPENCODE(
        id = "opencode",
        displayName = "OpenCode",
        packageProfileId = "opencode",
        // OpenCode 2.x has no `web` subcommand; `serve` is the foreground server that also
        // serves the web UI. `opencode pair` then produces the browser login link.
        webCommand = "opencode serve --hostname 127.0.0.1 --port 4096",
        acceptedCommands = setOf(
            "opencode serve --hostname 127.0.0.1 --port 4096",
            "opencode serve",
            "opencode web",
        ),
        webUrlPattern = Regex("""http://(?:127\.0\.0\.1|localhost):\d+(?:/[A-Za-z0-9/?:=&._~%+#-]*)?"""),
        terminalCommand = "opencode",
        pairCommand = { serverUrl -> "opencode pair --url $serverUrl" },
    ),
    DSH(
        id = "dsh",
        displayName = "DeepSeek Harness",
        packageProfileId = "dsh",
        // `dsh web` prints a tokenized loopback URL directly and stays in the foreground.
        webCommand = "dsh web --host 127.0.0.1 --port 3080 --no-open",
        acceptedCommands = setOf(
            "dsh web --host 127.0.0.1 --port 3080 --no-open",
            "dsh web",
        ),
        webUrlPattern = Regex("""http://(?:127\.0\.0\.1|localhost):\d+(?:/[A-Za-z0-9/?:=&._~%+#-]*)?"""),
        terminalCommand = "dsh",
    ),
    ;

    companion object {
        fun fromId(id: String?): ExtensionAgent =
            entries.firstOrNull { it.id == id } ?: OPENCODE
    }
}
