package io.github.mangi.eta.agent.extension

import io.github.mangi.eta.data.model.Model
import io.github.mangi.eta.data.model.ModelReasoningCapabilities
import io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
import io.github.mangi.eta.data.model.ReasoningEffort
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

    @Test
    fun etaProvidersMapContextVisionToolsAndReasoning() {
        val provider = OpenAiCompatibleProviderSetting(
            id = "wb",
            name = "WorkBuddy",
            baseUrl = "https://example.com/v1",
            apiKey = "sk-test",
            models = listOf(
                Model(
                    id = "m1",
                    modelId = "deepseek-v4-flash",
                    displayName = "DeepSeek V4 Flash",
                    contextWindow = 1_000_000,
                    inputModalities = listOf(Model.TEXT_MODALITY, Model.IMAGE_MODALITY),
                    toolCall = true,
                    reasoning = true,
                    reasoningCapabilities = ModelReasoningCapabilities(
                        supportedEfforts = listOf(
                            ReasoningEffort.LOW,
                            ReasoningEffort.HIGH,
                            ReasoningEffort.MAX,
                        ),
                        canDisable = true,
                    ),
                ),
            ),
        )
        val merged = OpenCodeProviderConfig.mergeEtaProviders(null, listOf(provider))
        val root = JSONObject(merged).getJSONObject("providers").getJSONObject("wb")
        assertEquals("aisdk:@ai-sdk/openai-compatible", root.getString("package"))
        assertEquals("https://example.com/v1", root.getJSONObject("settings").getString("baseURL"))
        val model = root.getJSONObject("models").getJSONObject("deepseek-v4-flash")
        assertEquals(1_000_000, model.getJSONObject("limit").getInt("context"))
        assertEquals(true, model.getJSONObject("capabilities").getBoolean("tools"))
        assertEquals(
            listOf("text", "image"),
            model.getJSONObject("capabilities").getJSONArray("input").let { array ->
                (0 until array.length()).map(array::getString)
            },
        )
        val variants = model.getJSONArray("variants")
        assertEquals(
            listOf("off", "low", "high", "max"),
            (0 until variants.length()).map { variants.getJSONObject(it).getString("id") },
        )
    }
}
