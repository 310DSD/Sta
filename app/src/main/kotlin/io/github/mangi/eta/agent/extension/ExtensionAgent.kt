package io.github.mangi.eta.agent.extension

import io.github.mangi.eta.agent.terminal.DshMobileCss

private const val OPENCODE_SERVICE_COMMAND =
    "trap 'opencode service stop >/dev/null 2>&1 || true' EXIT TERM INT; " +
        "opencode service start >/dev/null 2>&1 || true; " +
        "opencode service status; while :; do sleep 3600; done"

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
    /** Commands accepted when looking for an already-running instance (current forms only). */
    val acceptedCommands: Set<String>,
    /** Older launcher forms that must be stopped before starting the current command. */
    val legacyCommands: Set<String> = emptySet(),
    /** Loopback URL printed by the agent's web mode. */
    val webUrlPattern: Regex,
    /** Command users can type in Eta's terminal for the TUI. */
    val terminalCommand: String,
    /** Working directory for the daemon command; ACP bridge does not need /workspace. */
    val webCwd: String = "/root",
    /**
     * Optional command that prints a one-time browser login URL for the running service.
     * OpenCode 2.x pairing only works with its shared background service.
     */
    val pairCommand: ((String) -> String)? = null,
) {
    OPENCODE(
        id = "opencode",
        displayName = "OpenCode",
        packageProfileId = "opencode",
        // OpenCode 2.x has no `web` subcommand. `opencode serve` is standalone and cannot use
        // `pair`; the supported Web UI path is the shared service started by `service start`.
        // The wrapper keeps a daemon task alive and stops the service when Eta stops the task.
        webCommand = OPENCODE_SERVICE_COMMAND,
        acceptedCommands = setOf(OPENCODE_SERVICE_COMMAND),
        legacyCommands = setOf(
            "opencode serve --hostname 127.0.0.1 --port 4096",
            "opencode serve",
        ),
        webUrlPattern = Regex("""http://(?:127\.0\.0\.1|localhost):\d+(?:/[A-Za-z0-9/?:=&._~%+#-]*)?"""),
        terminalCommand = "opencode",
        pairCommand = { "opencode pair" },
    ),
    KIMI(
        id = "kimi",
        displayName = "Kimi Code",
        packageProfileId = "kimi",
        // Kimi Code's own web mode prints a tokenized loopback URL.
        webCommand = "kimi web --no-open",
        acceptedCommands = setOf("kimi web --no-open", "kimi web"),
        webUrlPattern = Regex("""http://(?:127\.0\.0\.1|localhost):\d+/#token=[A-Za-z0-9_-]+"""),
        terminalCommand = "kimi",
    ),
    DSH(
        id = "dsh",
        displayName = "DeepSeek Harness",
        packageProfileId = "dsh",
        // `dsh web` prints a tokenized loopback URL directly and stays in the foreground.
        // Apply the mobile CSS patch on every launch so an already-installed DSH also gets it.
        webCommand = "$DshMobileCss.installScript; dsh web --host 127.0.0.1 --port 3080 --no-open",
        acceptedCommands = setOf(
            "$DshMobileCss.installScript; dsh web --host 127.0.0.1 --port 3080 --no-open",
        ),
        legacyCommands = setOf(
            "dsh web --host 127.0.0.1 --port 3080 --no-open",
            "dsh web",
        ),
        webUrlPattern = Regex("""http://(?:127\.0\.0\.1|localhost):\d+(?:/[A-Za-z0-9/?:=&._~%+#-]*)?"""),
        terminalCommand = "dsh",
    ),
    CLOUDCLI(
        id = "cloudcli",
        displayName = "CloudCLI",
        packageProfileId = "cloudcli",
        // 绑定 loopback，避免 CloudCLI 默认监听 0.0.0.0；首次启动在 WebView 内完成本地账号初始化。
        webCommand = "HOST=127.0.0.1 SERVER_PORT=3001 cloudcli --port 3001",
        acceptedCommands = setOf(
            "HOST=127.0.0.1 SERVER_PORT=3001 cloudcli --port 3001",
            "cloudcli --port 3001",
        ),
        webUrlPattern = Regex("""http://(?:127\.0\.0\.1|localhost):3001(?:/[A-Za-z0-9/?:=&._~%+#-]*)?"""),
        terminalCommand = "cloudcli",
    ),
    ACP(
        id = "acp",
        displayName = "ACP",
        packageProfileId = "acp",
        // ACP bridge 同时暴露 Claude / Codex 两个官方 adapter；界面由 /opt/eta/acp/ui 提供。
        webCommand = "node /opt/eta/acp/server.mjs",
        acceptedCommands = setOf("node /opt/eta/acp/server.mjs"),
        webUrlPattern = Regex("""http://(?:127\.0\.0\.1|localhost):3210(?:/[A-Za-z0-9/?:=&._~%+#-]*)?"""),
        terminalCommand = "node /opt/eta/acp/server.mjs",
        webCwd = "/root",
    ),
    ;

    companion object {
        fun fromId(id: String?): ExtensionAgent =
            entries.firstOrNull { it.id == id } ?: OPENCODE
    }
}
