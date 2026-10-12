package dev.dohuber.gradleproperties.core

import kotlin.test.*
import org.junit.jupiter.api.Test

class CoreTest {
    private val feature = Feature("backend", "Backend", mapOf("url" to "local", "auth" to "false"), mapOf("url" to "remote", "auth" to "true"))
    @Test fun `same JSON schema works from an IDE document string`() {
        val parsed = FeatureConfig.parse("""{"features":[{"id":"parallel","label":"Parallel","enabled":{"org.gradle.parallel":"true"},"disabled":{"org.gradle.parallel":"false"}}]}""")
        val text = "# keep\norg.gradle.parallel=false\n"
        val enabled = PropertiesEditor.update(text, parsed.features.single().enabled)
        assertEquals(FeatureState.ENABLED, PropertiesEditor.state(enabled, parsed.features.single()))
        assertTrue(enabled.startsWith("# keep\n"))
    }
    @Test fun `group editing preserves unrelated unsaved content`() {
        val text = "# Unsaved editor comment\nurl=remote\nauth=true\nlocal.note=unsaved\n"
        val updated = PropertiesEditor.update(text, feature.enabled)
        assertEquals(FeatureState.ENABLED, PropertiesEditor.state(updated, feature))
        assertTrue(updated.contains("local.note=unsaved\n"))
        assertTrue(updated.startsWith("# Unsaved editor comment\n"))
    }
    @Test fun `unicode document text can be matched and new values escaped`() {
        val group = Feature("unicode", "Unicode", mapOf("name" to "ü"), mapOf("name" to null))
        assertEquals(FeatureState.ENABLED, PropertiesEditor.state("name=ü\n", group))
        assertEquals(FeatureState.ENABLED, PropertiesEditor.state(PropertiesEditor.update("", group.enabled), group))
        assertEquals("", PropertiesEditor.update("name=ü\n", group.disabled))
    }
    @Test fun `invalid feature definitions are rejected before editing`() {
        assertFailsWith<IllegalArgumentException> { FeatureConfig(listOf(feature, feature.copy(id = "other"))).validate() }
        assertFailsWith<IllegalArgumentException> { FeatureConfig(listOf(feature.copy(disabled = mapOf("url" to "remote")))).validate() }
        assertFailsWith<IllegalArgumentException> { FeatureConfig(emptyList()).validate() }
    }
}
