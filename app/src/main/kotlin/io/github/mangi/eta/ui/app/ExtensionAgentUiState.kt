package io.github.mangi.eta.ui.app

import android.content.Context
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.extension.ExtensionAgent

internal enum class ExtensionAgentPhase { CHECKING, NOT_INSTALLED, READY, STARTING, RUNNING, FAILED }

internal data class ExtensionAgentUiState(
    val agent: ExtensionAgent = ExtensionAgent.OPENCODE,
    val phase: ExtensionAgentPhase = ExtensionAgentPhase.CHECKING,
    val errorCode: String? = null,
) {
    val canStop: Boolean
        get() = phase == ExtensionAgentPhase.STARTING || phase == ExtensionAgentPhase.RUNNING

    fun actionLabel(context: Context): String = when (phase) {
        ExtensionAgentPhase.CHECKING -> context.getString(R.string.extension_agent_checking)
        ExtensionAgentPhase.NOT_INSTALLED -> context.getString(R.string.extension_agent_install)
        ExtensionAgentPhase.READY -> context.getString(R.string.action_launch_agent_web, agent.displayName)
        ExtensionAgentPhase.STARTING -> context.getString(R.string.extension_agent_preparing)
        ExtensionAgentPhase.RUNNING -> context.getString(R.string.extension_agent_open, agent.displayName)
        ExtensionAgentPhase.FAILED -> context.getString(R.string.extension_agent_retry)
    }
}

internal fun ExtensionAgentLaunchResult.Failed.message(context: Context): String {
    val base = context.getString(
        when (code) {
            "ROOT_REQUIRED" -> R.string.extension_agent_error_root
            "BACKGROUND_START_NOT_ALLOWED" -> R.string.extension_agent_error_background
            "AGENT_EXITED", "PROCESS_EXITED" -> R.string.extension_agent_exited
            "LINUX_ENVIRONMENT_NOT_READY", "PROFILE_NOT_INSTALLED" -> R.string.extension_agent_not_installed
            "PROOT_UNAVAILABLE" -> R.string.extension_agent_error_proot
            "LOGS_UNAVAILABLE" -> R.string.extension_agent_error_logs
            "URL_TIMEOUT" -> R.string.extension_agent_error_url
            "BROWSER_UNAVAILABLE" -> R.string.extension_agent_error_browser
            "STOP_FAILED" -> R.string.extension_agent_error_stop
            "PAIRING_FAILED" -> R.string.extension_agent_error_pairing
            else -> R.string.extension_agent_error_start
        },
    )
    val detailText = detail.orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { line ->
            if (line.contains("server password", ignoreCase = true) ||
                line.contains("token", ignoreCase = true) ||
                line.contains("auth/connect", ignoreCase = true)
            ) {
                "[redacted]"
            } else {
                line
            }
        }
        .toList()
        .takeLast(6)
        .joinToString("\n")
        .take(600)
    return if (detailText.isBlank()) base else "$base\n$detailText"
}
