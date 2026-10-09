import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*

class SettingsStoreTest {
    @TempDir lateinit var directory: Path
    private fun store() = SettingsStore(directory.resolve("config/settings.json"))
    private fun configFile(): Path = directory.resolve("features.json").also {
        Files.writeString(it, """{"features":[{"id":"parallel","label":"Parallel","enabled":{"org.gradle.parallel":"true"},"disabled":{"org.gradle.parallel":"false"}}]}""")
    }
    @Test fun `uses absolute XDG config home and falls back for unset empty or relative values`() {
        assertEquals(Path.of("/custom/gradle-property-helper/settings.json"), SettingsStore.defaultPath("/custom", "/home/test"))
        for (xdg in listOf(null, "", "relative")) {
            assertEquals(Path.of("/home/test/.config/gradle-property-helper/settings.json"), SettingsStore.defaultPath(xdg, "/home/test"))
        }
    }
    @Test fun `missing settings use defaults and round trip survives a new store instance`() {
        assertEquals(AppSettings(), store().load())
        val settings = AppSettings(directory.resolve("project ü").toString(), directory.resolve("features.json").toString())
        store().save(settings)
        assertEquals(settings, store().load())
        store().save(settings.copy(featureConfigPath = null))
        assertEquals(settings.copy(featureConfigPath = null), store().load())
        Files.list(directory.resolve("config")).use { assertEquals(1, it.count()) }
    }
    @Test fun `restores both selections and reads current properties rather than saved toggle state`() {
        val project = Files.createDirectory(directory.resolve("project"))
        val properties = project.resolve("gradle.properties")
        Files.writeString(properties, "org.gradle.parallel=true\n")
        store().save(AppSettings(project.toString(), configFile().toString()))
        Files.writeString(properties, "org.gradle.parallel=false\n")
        val restored = store().restore()
        assertEquals(properties, restored.propertiesFile)
        assertEquals(FeatureState.DISABLED, PropertiesEditor.state(restored.propertiesText, restored.featureConfig!!.features.single()))
        assertTrue(restored.warnings.isEmpty())
    }
    @Test fun `missing properties are supported but missing project does not discard valid config`() {
        store().save(AppSettings(directory.toString(), configFile().toString()))
        assertEquals("", store().restore().propertiesText)
        assertTrue(store().restore().warnings.isEmpty())
        val missing = directory.resolve("missing").toString()
        store().save(store().load().copy(projectDirectory = missing))
        val restored = store().restore()
        assertNull(restored.propertiesFile)
        assertNotNull(restored.featureConfig)
        assertEquals(missing, restored.settings.projectDirectory)
        assertEquals(1, restored.warnings.size)
    }
    @Test fun `invalid config does not prevent restoring project`() {
        val config = configFile()
        Files.writeString(config, "not JSON")
        store().save(AppSettings(directory.toString(), config.toString()))
        val restored = store().restore()
        assertNotNull(restored.propertiesFile)
        assertNull(restored.featureConfig)
        assertEquals(1, restored.warnings.size)
    }
    @Test fun `corrupt settings warn without rewriting original file`() {
        Files.createDirectories(store().path.parent)
        Files.writeString(store().path, "broken")
        val restored = store().restore()
        assertEquals(AppSettings(), restored.settings)
        assertEquals(1, restored.warnings.size)
        assertEquals("broken", Files.readString(store().path))
    }
    @Test fun `unknown settings fields are tolerated`() {
        Files.createDirectories(store().path.parent)
        Files.writeString(store().path, """{"futureOption":true}""")
        assertEquals(AppSettings(), store().load())
    }
}
