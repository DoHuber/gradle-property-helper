import dev.dohuber.gradleproperties.core.PropertiesEditor
import java.nio.charset.StandardCharsets
import java.nio.file.*

data class PropertySaveResult(val text: String, val backupPath: Path?)

object PropertyFiles {
    fun read(path: Path): String = try {
        String(Files.readAllBytes(path), StandardCharsets.ISO_8859_1)
    } catch (e: NoSuchFileException) {
        // A new properties file is supported, but a missing project is an error.
        // Do not use Files.exists: it also returns false when access is denied.
        if (!Files.isDirectory(path.toAbsolutePath().parent) || Files.isSymbolicLink(path)) throw e
        ""
    }
    fun apply(path: Path, expected: String, values: Map<String, String?>): PropertySaveResult {
        require(!Files.isSymbolicLink(path)) { "Symbolic links are not supported." }
        check(read(path) == expected) { "File changed externally. Refresh before applying." }
        val updated = PropertiesEditor.update(expected, values)
        val temp = Files.createTempFile(path.toAbsolutePath().parent, ".properties-helper-", ".tmp")
        var backup: Path? = null
        try {
            Files.write(temp, updated.toByteArray(StandardCharsets.ISO_8859_1))
            if (Files.exists(path)) {
                runCatching { Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(path)) }
                val candidate = Files.createTempFile("gradle-property-helper-", ".gradle.properties.bak")
                try {
                    // Back up the verified snapshot; preserve the private permissions
                    // created by createTempFile instead of copying source permissions.
                    Files.write(candidate, expected.toByteArray(StandardCharsets.ISO_8859_1))
                    backup = candidate
                } catch (error: Exception) {
                    try { Files.deleteIfExists(candidate) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                    throw error
                }
            }
            check(read(path) == expected) { "File changed externally. Refresh before applying." }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
        return PropertySaveResult(updated, backup)
    }
}
