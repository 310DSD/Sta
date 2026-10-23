package io.github.mangi.eta.agent.extension

import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.TerminalRuntime
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionAgentTest {
    @Test
    fun catalogueHasTheSelectedAgentsIncludingKimi() {
        assertEquals(listOf("opencode", "kimi", "dsh", "t3code"), ExtensionAgent.entries.map { it.id })
    }

    @Test
    fun unknownPersistedIdFallsBackToOpenCode() {
        assertEquals(ExtensionAgent.OPENCODE, ExtensionAgent.fromId(null))
        assertEquals(ExtensionAgent.OPENCODE, ExtensionAgent.fromId("unknown"))
        assertEquals(ExtensionAgent.KIMI, ExtensionAgent.fromId("kimi"))
    }

    @Test
    fun bothWebCommandsAreNonInteractiveAndUseLoopbackUrls() {
        ExtensionAgent.entries.forEach { agent ->
            if (agent == ExtensionAgent.OPENCODE) {
                assertTrue(agent.webCommand.contains("opencode service start"))
                assertTrue(agent.webCommand.contains("opencode service status"))
            } else if (agent == ExtensionAgent.DSH) {
                assertTrue(agent.webCommand.contains("127.0.0.1"))
                assertTrue(agent.webCommand.contains("--port"))
            } else if (agent == ExtensionAgent.T3_CODE) {
                assertTrue(agent.webCommand.contains("t3 serve"))
                assertTrue(agent.webCommand.contains("--host 127.0.0.1"))
            } else {
                assertTrue(agent.webCommand.contains("kimi web"))
            }
            assertTrue(agent.acceptedCommands.contains(agent.webCommand))
            val sample = when (agent) {
                ExtensionAgent.KIMI -> "http://127.0.0.1:4096/#token=abc"
                ExtensionAgent.T3_CODE -> "http://127.0.0.1:3773/pair#token=abc"
                else -> "http://127.0.0.1:4096"
            }
            assertTrue(agent.webUrlPattern.containsMatchIn(sample))
        }
    }

    @Test
    fun openCodeUsesSharedServiceAndPairInsteadOfTheRemovedWebSubcommand() {
        val agent = ExtensionAgent.OPENCODE
        assertTrue(agent.webCommand.contains("opencode service start"))
        assertTrue(agent.webCommand.contains("opencode service status"))
        assertEquals("opencode pair", agent.pairCommand?.invoke("http://127.0.0.1:4096"))
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
