package io.github.mangi.eta.agent.cli

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CliConfigEditorsTest {
    private val existingCodex = """
        # keep this comment
        model = "gpt-5"
        approval_policy = "never"

        [profiles.fast]
        model = "o4-mini"

        [mcp_servers.demo]
        command = "demo"
    """.trimIndent()

    @Test
    fun thirdPartyOnlyChangesConnectionFieldsAndKeepsOtherConfig() {
        val updated = CodexConfigEditor.apply(
            existingCodex,
            CodexRoute.ThirdParty("https://relay.example/v1/", "sk-test", "gpt-5.1"),
        )

        assertTrue(updated.contains("model = \"gpt-5.1\""))
        assertTrue(updated.contains("model_provider = \"eta\""))
        assertTrue(updated.contains("[model_providers.eta]"))
        assertTrue(updated.contains("base_url = \"https://relay.example/v1\""))
        assertTrue(updated.contains("experimental_bearer_token = \"sk-test\""))
        assertTrue(updated.contains("requires_openai_auth = false"))
        assertTrue(updated.contains("# keep this comment"))
        assertTrue(updated.contains("approval_policy = \"never\""))
        assertTrue(updated.contains("[profiles.fast]"))
        assertTrue(updated.contains("model = \"o4-mini\""))
        assertTrue(updated.contains("[mcp_servers.demo]"))
    }

    @Test
    fun officialRemovesManagedRouteButKeepsUserTables() {
        val withRoute = """
            model = "gpt-5"
            model_provider = "eta"
            openai_base_url = "https://relay.example/v1"

            [model_providers.eta]
            name = "Eta"
            base_url = "https://relay.example/v1"
            experimental_bearer_token = "sk-test"

            [profiles.fast]
            model = "o4-mini"
        """.trimIndent()

        val updated = CodexConfigEditor.apply(withRoute, CodexRoute.Official)

        assertFalse(updated.contains("model_provider"))
        assertFalse(updated.contains("openai_base_url"))
        assertFalse(updated.contains("[model_providers.eta]"))
        assertFalse(updated.contains("experimental_bearer_token"))
        assertFalse(updated.contains("model = \"gpt-5\""))
        assertTrue(updated.contains("[profiles.fast]"))
        assertTrue(updated.contains("model = \"o4-mini\""))
    }

    @Test
    fun apiKeyModeRemovesStaleThirdPartyTable() {
        val withRoute = """
            model_provider = "eta"

            [model_providers.eta]
            name = "Eta"
            base_url = "https://relay.example/v1"
            experimental_bearer_token = "sk-test"
        """.trimIndent()

        val updated = CodexConfigEditor.apply(withRoute, CodexRoute.ApiKey("gpt-5"))

        assertFalse(updated.contains("model_provider"))
        assertFalse(updated.contains("[model_providers.eta]"))
        assertFalse(updated.contains("experimental_bearer_token"))
        assertTrue(updated.contains("model = \"gpt-5\""))
    }

    @Test
    fun profileModelIsNotTreatedAsRootModel() {
        val onlyProfile = """
            [profiles.fast]
            model = "o4-mini"
        """.trimIndent()

        val updated = CodexConfigEditor.apply(onlyProfile, CodexRoute.ApiKey("gpt-5"))

        assertTrue(updated.contains("model = \"gpt-5\""))
        assertTrue(updated.contains("model = \"o4-mini\""))
    }

    @Test
    fun claudeMergePreservesUnknownSettingsAndReplacesManagedEnv() {
        val existing = """
            {
              "hooks": {"Stop": [{"hooks": [{"type": "command", "command": "echo ok"}]}]},
              "env": {"ANTHROPIC_API_KEY": "old", "KEEP_ME": "1"},
              "model": "claude-sonnet"
            }
        """.trimIndent()

        val updated = ClaudeSettingsEditor.merge(
            existing,
            CliAccountKind.THIRD_PARTY,
            apiKey = "sk-new",
            baseUrl = "https://relay.example/v1/",
            model = "claude-custom",
        )
        val json = JSONObject(updated)
        val env = json.getJSONObject("env")

        assertEquals("https://relay.example/v1", env.getString("ANTHROPIC_BASE_URL"))
        assertEquals("sk-new", env.getString("ANTHROPIC_AUTH_TOKEN"))
        assertEquals("claude-custom", env.getString("ANTHROPIC_MODEL"))
        assertEquals("1", env.getString("KEEP_ME"))
        assertFalse(env.has("ANTHROPIC_API_KEY"))
        assertTrue(json.has("hooks"))
        assertEquals("claude-sonnet", json.getString("model"))
    }

    @Test
    fun claudeOfficialStripOnlyRemovesManagedEnv() {
        val existing = """
            {"env": {"ANTHROPIC_BASE_URL": "https://relay", "KEEP_ME": "1"}, "hooks": {}}
        """.trimIndent()

        val json = JSONObject(ClaudeSettingsEditor.stripManaged(existing))
        val env = json.getJSONObject("env")

        assertFalse(env.has("ANTHROPIC_BASE_URL"))
        assertEquals("1", env.getString("KEEP_ME"))
        assertTrue(json.has("hooks"))
    }

    @Test
    fun claudeInvalidJsonFailsInsteadOfBeingReplaced() {
        try {
            ClaudeSettingsEditor.merge(
                "{not-json",
                CliAccountKind.API_KEY,
                apiKey = "sk",
                baseUrl = "",
                model = "",
            )
            throw AssertionError("expected JSONException")
        } catch (_: JSONException) {
            // expected
        }
    }

    @Test
    fun activeProfileRouteOverrideIsRejected() {
        val existing = """
            profile = "fast"

            [profiles.fast]
            model_provider = "other"
        """.trimIndent()

        try {
            CodexConfigEditor.apply(
                existing,
                CodexRoute.ThirdParty("https://relay.example/v1", "sk", "gpt-5"),
            )
            throw AssertionError("expected CodexProfileOverrideException")
        } catch (_: CodexProfileOverrideException) {
            // expected
        }
    }

    @Test
    fun officialSwitchRejectsProfilesReferencingManagedRoute() {
        val existing = """
            [profiles.fast]
            model_provider = "eta"
        """.trimIndent()

        try {
            CodexConfigEditor.apply(existing, CodexRoute.Official)
            throw AssertionError("expected CodexProfileOverrideException")
        } catch (_: CodexProfileOverrideException) {
            // expected
        }
    }

    @Test
    fun officialAuthValidationRejectsApiKeySnapshots() {
        assertTrue(CliAuthEditor.isOfficialCodexAuth("""{"tokens":{"access_token":"a"}}"""))
        assertTrue(CliAuthEditor.isOfficialCodexAuth("""{"tokens":{"refresh_token":"r"}}"""))
        assertFalse(CliAuthEditor.isOfficialCodexAuth("""{"auth_mode":"chatgpt"}"""))
        assertFalse(CliAuthEditor.isOfficialCodexAuth("""{"OPENAI_API_KEY":"sk"}"""))
        assertTrue(CliAuthEditor.isOfficialClaudeCredentials("""{"claudeAiOauth":{"accessToken":"a"}}"""))
        assertFalse(CliAuthEditor.isOfficialClaudeCredentials("""{}"""))
    }
}
