package io.github.mangi.eta.agent.cli

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CliAccountStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun clearStore() {
        File(context.noBackupFilesDir, "cli_accounts.json").delete()
    }

    @Test
    fun normalizesFirstVersionAccountAndKeepsItUsable() {
        File(context.noBackupFilesDir, "cli_accounts.json").writeText(
            """
            {
              "accounts": [
                {
                  "id": "legacy-1",
                  "agentId": "claude",
                  "name": "旧版 Claude 账号",
                  "kind": "OFFICIAL",
                  "credentialsJson": "{}",
                  "createdAt": 1
                }
              ],
              "activeByAgent": { "claude": "legacy-1" }
            }
            """.trimIndent(),
        )

        val state = CliAccountStore(context).load()

        assertEquals(1, state.accounts.size)
        assertEquals(CliAgent.CLAUDE_CODE.id, state.accounts.single().agentId)
        assertEquals("旧版 Claude 账号", state.accounts.single().name)
        assertTrue(state.activeByAgent[CliAgent.CLAUDE_CODE.id] == "legacy-1")
    }
}
