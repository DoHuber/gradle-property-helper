import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.Properties

@Serializable
data class FeatureConfig(val features: List<Feature>) {
    fun validate(): FeatureConfig {
        require(features.isNotEmpty()) { "Configuration needs at least one feature." }
        require(features.map { it.id }.distinct().size == features.size) { "Feature IDs must be unique." }
        val owned = mutableSetOf<String>()
        features.forEach { f ->
            require(f.id.isNotBlank() && f.label.isNotBlank()) { "Feature ID and label must not be blank." }
            require(f.enabled.isNotEmpty() && f.enabled.keys == f.disabled.keys) { "${f.id}: both states must define the same properties." }
            require(f.enabled != f.disabled) { "${f.id}: states must differ." }
            f.enabled.keys.forEach { key ->
                require(key.matches(Regex("[A-Za-z0-9_.-]+"))) { "Unsupported property key: $key" }
                require(owned.add(key)) { "Property belongs to multiple features: $key" }
            }
        }
        return this
    }
    companion object {
        fun load(path: Path) = Json.decodeFromString<FeatureConfig>(Files.readString(path)).validate()
    }
}
@Serializable
data class Feature(val id: String, val label: String, val enabled: Map<String, String?>, val disabled: Map<String, String?>)
enum class FeatureState { ENABLED, DISABLED, MIXED }

object PropertiesEditor {
    private fun parse(text: String) = Properties().apply { load(StringReader(text)) }
    fun state(text: String, feature: Feature): FeatureState {
        val p = parse(text)
        fun matches(values: Map<String, String?>) = values.all { (k, v) -> p.getProperty(k) == v }
        return when { matches(feature.enabled) -> FeatureState.ENABLED; matches(feature.disabled) -> FeatureState.DISABLED; else -> FeatureState.MIXED }
    }
    private fun escape(value: String): String = buildString {
        value.forEach { c -> append(when (c) {
            '\\' -> "\\\\"; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"; ' ' -> "\\ ";
            else -> if (c.code > 126 || c.code < 32) "\\u%04x".format(c.code) else c.toString()
        }) }
    }
    fun update(text: String, values: Map<String, String?>): String {
        val newline = if (text.contains("\r\n")) "\r\n" else "\n"
        val lines = Regex("[^\\r\\n]*(?:\\r\\n|\\n|\\r|$)").findAll(text).map { it.value }.filter { it.isNotEmpty() }.toList()
        val result = StringBuilder()
        val seen = mutableSetOf<String>()
        var i = 0
        while (i < lines.size) {
            val block = StringBuilder(lines[i++])
            fun continues(s: String): Boolean {
                val body = s.trimEnd('\r', '\n')
                return body.takeLastWhile { it == '\\' }.length % 2 == 1
            }
            val comment = block.toString().trimStart().let { it.startsWith('#') || it.startsWith('!') }
            while (!comment && continues(block.toString()) && i < lines.size) block.append(lines[i++])
            val keys = parse(block.toString()).stringPropertyNames()
            val key = keys.singleOrNull()
            if (key != null && values.containsKey(key)) {
                if (seen.add(key)) values[key]?.let { result.append(key).append('=').append(escape(it)).append(newline) }
            } else result.append(block)
        }
        values.filterKeys { it !in seen }.forEach { (k, v) ->
            if (v != null) {
                if (result.isNotEmpty() && result.last() != '\n' && result.last() != '\r') result.append(newline)
                result.append(k).append('=').append(escape(v)).append(newline)
            }
        }
        return result.toString()
    }
    fun read(path: Path): String = if (Files.exists(path)) String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1) else ""
    fun apply(path: Path, expected: String, values: Map<String, String?>): String {
        require(!Files.isSymbolicLink(path)) { "Symbolic links are not supported." }
        check(read(path) == expected) { "File changed externally. Refresh before applying." }
        val updated = update(expected, values)
        val temp = Files.createTempFile(path.toAbsolutePath().parent, ".properties-helper-", ".tmp")
        try {
            Files.write(temp, updated.toByteArray(StandardCharsets.ISO_8859_1))
            if (Files.exists(path)) {
                runCatching { Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(path)) }
                Files.copy(path, path.resolveSibling("${path.fileName}.bak"), StandardCopyOption.REPLACE_EXISTING)
            }
            check(read(path) == expected) { "File changed externally. Refresh before applying." }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
        return updated
    }
}
