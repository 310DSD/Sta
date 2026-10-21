package io.github.mangi.eta.agent.extension

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenCodeProviderConfigTest {
    @Test
    fun mergePreservesExistingProvidersAndAddsCustomProvider() {
        val existing = """
            {
              "${'$'}schema": "https://opencode.ai/config.json",
              "providers": {
                "existing": {
                  "name": "Existing",
                  "models": { "m1": {} }
                }
              }
            }
        """.trimIndent()
        val merged = OpenCodeProviderConfig.merge(
            existing,
            OpenCodeProviderDraft(
                id = "local",
                name = "Local",
                baseUrl = "http://127.0.0.1:8080/v1",
                apiKey = "sk-test",
                modelIds = listOf("model-a", "model-b"),
            ),
        )
        val root = JSONObject(merged)
        val providers = root.getJSONObject("providers")
        assertTrue(providers.has("existing"))
        val local = providers.getJSONObject("local")
        assertEquals("Local", local.getString("name"))
        assertEquals(OpenCodeProviderConfig.PACKAGE, local.getString("package"))
        assertEquals("http://127.0.0.1:8080/v1", local.getJSONObject("settings").getString("baseURL"))
        assertEquals("sk-test", local.getJSONObject("settings").getString("apiKey"))
        assertEquals(setOf("model-a", "model-b"), local.getJSONObject("models").keys().asSequence().toSet())
    }

    @Test
    fun mergeCreatesConfigWhenMissing() {
        val merged = OpenCodeProviderConfig.merge(
            null,
            OpenCodeProviderDraft(
                id = "local",
                name = "Local",
                baseUrl = "https://example.com/v1",
                apiKey = "",
                modelIds = listOf("m"),
            ),
        )
        val root = JSONObject(merged)
        assertEquals("https://opencode.ai/config.json", root.getString("${'$'}schema"))
        assertTrue(root.getJSONObject("providers").has("local"))
    }

    @Test
    fun mergeAcceptsJsoncCommentsWithoutEatingUrlSlashes() {
        val existing = """
            {
              // keep the user's existing provider
              "${'$'}schema": "https://opencode.ai/config.json",
              "providers": {
                "existing": {
                  "name": "Existing",
                  "settings": { "baseURL": "https://example.com/v1" }
                }
              }
            }
        """.trimIndent()
        val merged = OpenCodeProviderConfig.merge(
            existing,
            OpenCodeProviderDraft("local", "Local", "http://127.0.0.1:8080/v1", "", listOf("m")),
        )
        val settings = JSONObject(merged).getJSONObject("providers")
            .getJSONObject("existing").getJSONObject("settings")
        assertEquals("https://example.com/v1", settings.getString("baseURL"))
    }

    @Test
    fun parseModelsSplitsAndDeduplicates() {
        assertEquals(
            listOf("a", "b", "c"),
            OpenCodeProviderConfig.parseModels("a, b\nc\r\na"),
        )
    }
}
