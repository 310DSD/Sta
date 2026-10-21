package io.github.mangi.eta.ui.app

import io.github.mangi.eta.agent.extension.ExtensionAgent
import io.github.mangi.eta.agent.terminal.DaemonLogsResult
import io.github.mangi.eta.agent.terminal.DaemonStartResult
import io.github.mangi.eta.agent.terminal.DetachedTask
import io.github.mangi.eta.agent.terminal.DetachedTaskStatus
import io.github.mangi.eta.agent.terminal.LinuxDistribution
import io.github.mangi.eta.agent.terminal.LinuxExecutionBackend
import io.github.mangi.eta.agent.terminal.TerminalEnvironment
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionAgentWebSessionTest {
    private val environment = LinuxDistribution.DEBIAN.terminalEnvironment

    private fun task(id: String, command: String, running: Boolean = true) = DetachedTaskStatus(
        task = DetachedTask(
            id = id,
            pid = 1,
            token = "token",
            command = command,
            cwd = "/workspace",
            identity = "user",
            environment = environment,
            logPath = "/tmp/$id.log",
            startedAt = 0,
            backend = LinuxExecutionBackend.PROOT,
        ),
        running = running,
    )

    private class FakeTasks(
        val statuses: MutableList<DetachedTaskStatus> = mutableListOf(),
        var logs: DaemonLogsResult = DaemonLogsResult(ok = true, text = ""),
    ) : ExtensionAgentWebSession.Tasks {
        val started = mutableListOf<String>()
        val stopped = mutableListOf<String>()

        override fun list(): List<DetachedTaskStatus> = statuses.toList()

        override fun start(
            agent: ExtensionAgent,
            environment: TerminalEnvironment,
            identity: String,
        ): DaemonStartResult {
            started += agent.id
            val status = DetachedTaskStatus(
                task = DetachedTask(
                    id = "started-${agent.id}",
                    pid = 2,
                    token = "token",
                    command = agent.webCommand,
                    cwd = "/workspace",
                    identity = identity,
                    environment = environment,
                    logPath = "/tmp/started.log",
                    startedAt = 0,
                    backend = LinuxExecutionBackend.PROOT,
                ),
                running = true,
            )
            statuses += status
            return DaemonStartResult.Started(status.task)
        }

        override fun logs(id: String): DaemonLogsResult = logs

        override fun stop(id: String) {
            stopped += id
            statuses.replaceAll { if (it.task.id == id) it.copy(running = false) else it }
        }
    }

    @Test
    fun parsesOpenCodeAndZCodeLoopbackUrls() {
        assertEquals(
            "http://127.0.0.1:4096",
            ExtensionAgentWebSession.addressFromLogs(
                ExtensionAgent.OPENCODE,
                "server listening on http://127.0.0.1:4096",
            ),
        )
        assertEquals(
            "http://127.0.0.1:3030/?token=abc",
            ExtensionAgentWebSession.addressFromLogs(
                ExtensionAgent.ZCODE,
                "\u001B[32mhttp://127.0.0.1:3030/?token=abc\u001B[0m",
            ),
        )
        assertEquals(
            "http://127.0.0.1:4096/#token=abc",
            ExtensionAgentWebSession.addressFromLogs(
                ExtensionAgent.OPENCODE,
                "[http://127.0.0.1:4096/#token=abc]",
            ),
        )
    }

    @Test
    fun reusesAnExistingAgentWebTask() = runBlocking {
        val existing = task("existing", ExtensionAgent.OPENCODE.webCommand)
        val tasks = FakeTasks(
            statuses = mutableListOf(existing),
            logs = DaemonLogsResult(ok = true, text = "http://127.0.0.1:4096"),
        )

        val result = ExtensionAgentWebSession(tasks, openUrl = { true }, waitAttempts = 1)
            .launch(ExtensionAgent.OPENCODE, environment, "user", LinuxExecutionBackend.PROOT)

        assertEquals(ExtensionAgentLaunchResult.Opened("http://127.0.0.1:4096"), result)
        assertTrue(tasks.started.isEmpty())
        assertTrue(tasks.stopped.isEmpty())
    }

    @Test
    fun stopsATaskItCreatedWhenNoUrlAppears() = runBlocking {
        val tasks = FakeTasks(
            logs = DaemonLogsResult(ok = true, text = "booting"),
        )

        val result = ExtensionAgentWebSession(tasks, openUrl = { true }, waitAttempts = 1, waitIntervalMs = 0)
            .launch(ExtensionAgent.ZCODE, environment, "user", LinuxExecutionBackend.PROOT)

        assertEquals(ExtensionAgentLaunchResult.Failed("URL_TIMEOUT"), result)
        assertEquals(listOf("zcode"), tasks.started)
        assertEquals(listOf("started-zcode"), tasks.stopped)
    }

    @Test
    fun stopsTheCreatedTaskWhenTheBrowserCannotOpen() = runBlocking {
        val tasks = FakeTasks(logs = DaemonLogsResult(ok = true, text = "http://127.0.0.1:3030/"))

        val result = ExtensionAgentWebSession(tasks, openUrl = { false }, waitAttempts = 1)
            .launch(ExtensionAgent.ZCODE, environment, "user", LinuxExecutionBackend.PROOT)

        assertEquals(ExtensionAgentLaunchResult.Failed("BROWSER_UNAVAILABLE"), result)
        assertTrue(tasks.stopped.contains("started-zcode"))
    }
}
