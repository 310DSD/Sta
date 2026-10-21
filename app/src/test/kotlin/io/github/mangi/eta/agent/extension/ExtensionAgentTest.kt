package io.github.mangi.eta.agent.extension

import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.TerminalRuntime
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionAgentTest {
    @Test
    fun catalogueHasTheTwoSelectedAgents() {
        assertEquals(listOf("opencode", "zcode"), ExtensionAgent.entries.map { it.id })
    }

    @Test
    fun unknownPersistedIdFallsBackToOpenCode() {
        assertEquals(ExtensionAgent.OPENCODE, ExtensionAgent.fromId(null))
        assertEquals(ExtensionAgent.OPENCODE, ExtensionAgent.fromId("kimi"))
    }

    @Test
    fun bothWebCommandsAreNonInteractiveAndUseAFixedLoopbackPort() {
        ExtensionAgent.entries.forEach { agent ->
            assertTrue(agent.webCommand.contains("127.0.0.1"))
            assertTrue(agent.webCommand.contains("--port"))
            assertTrue(agent.acceptedCommands.contains(agent.webCommand))
            assertTrue(agent.webUrlPattern.containsMatchIn("http://127.0.0.1:4096"))
        }
    }

    @Test
    fun chrootUsesRootIdentityWhileProotUsesUserIdentity() {
        assertEquals(
            "root",
            TerminalRuntime.defaultIdentity(
                LinuxDistribution.DEBIAN.terminalEnvironment,
                "/data/user/0/io.github.mangi.eta/files/terminal/debian/rootfs",
            ),
        )
        assertEquals(
            "user",
            TerminalRuntime.defaultIdentity(
                LinuxDistribution.DEBIAN.terminalEnvironment,
                "/data/user/0/io.github.mangi.eta/files/terminal-user/proot/debian/rootfs",
            ),
        )
    }
}
