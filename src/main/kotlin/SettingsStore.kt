import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.*

@Serializable
data class AppSettings(val projectDirectory: String? = null, val featureConfigPath: String? = null)

data class RestoredSettings(
    val settings: AppSettings,
    val propertiesFile: Path?,
    val propertiesText: String,
    val featureConfig: FeatureConfig?,
    val warnings: List<String>
)

class SettingsStore(val path: Path = defaultPath()) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    fun load(): AppSettings = if (Files.notExists(path)) AppSettings() else json.decodeFromString(Files.readString(path))
    fun save(settings: AppSettings) {
        val target = path.toAbsolutePath()
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".settings-", ".tmp")
        try {
            Files.writeString(temp, json.encodeToString(settings))
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
    fun restore(): RestoredSettings {
        val warnings = mutableListOf<String>()
        val settings = try { load() } catch (e: Exception) {
            warnings.add("Could not read settings at $path: ${e.message}")
            AppSettings()
        }
        var propertiesFile: Path? = null
        var text = ""
        settings.projectDirectory?.let { saved ->
            try {
                val directory = Path.of(saved)
                require(directory.isAbsolute && Files.isDirectory(directory)) { "Project directory is unavailable: $saved" }
                val candidate = directory.resolve("gradle.properties")
                val content = PropertiesEditor.read(candidate)
                java.util.Properties().load(content.reader())
                propertiesFile = candidate; text = content
            } catch (e: Exception) { warnings.add("Could not restore project: ${e.message}") }
        }
        val config = settings.featureConfigPath?.let { saved ->
            try {
                val candidate = Path.of(saved)
                require(candidate.isAbsolute) { "Configuration path must be absolute." }
                FeatureConfig.load(candidate)
            } catch (e: Exception) { warnings.add("Could not restore feature JSON: ${e.message}"); null }
        }
        return RestoredSettings(settings, propertiesFile, text, config, warnings)
    }
    companion object {
        fun defaultPath(
            xdgConfigHome: String? = System.getenv("XDG_CONFIG_HOME"),
            userHome: String = System.getProperty("user.home")
        ): Path {
            val xdg = xdgConfigHome?.takeIf { it.isNotBlank() }?.let { Path.of(it) }?.takeIf { it.isAbsolute }
            return (xdg ?: Path.of(userHome).resolve(".config")).resolve("gradle-property-helper/settings.json")
        }
    }
}
