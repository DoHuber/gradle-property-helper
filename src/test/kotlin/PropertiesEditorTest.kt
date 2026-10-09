import kotlin.test.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*

class PropertiesEditorTest {
    @TempDir lateinit var directory: Path
    private val feature = Feature("backend", "Backend", mapOf("url" to "local", "auth" to "false"), mapOf("url" to "remote", "auth" to "true"))
    @Test fun `group state detects mixed and complete states`() {
        assertEquals(FeatureState.MIXED, PropertiesEditor.state("url=local\nauth=true\n", feature))
        assertEquals(FeatureState.ENABLED, PropertiesEditor.state(PropertiesEditor.update("", feature.enabled), feature))
        assertEquals(FeatureState.DISABLED, PropertiesEditor.state(PropertiesEditor.update("", feature.disabled), feature))
    }
    @Test fun `preserves comments unrelated content and CRLF while removing duplicates`() {
        val input = "# Comment\r\nurl : old\r\nother = unchanged\r\nurl=duplicate\r\n"
        assertEquals("# Comment\r\nurl=local\r\nother = unchanged\r\n", PropertiesEditor.update(input, mapOf("url" to "local")))
    }
    @Test fun `handles escaped keys continuations removal and unicode values`() {
        val output = PropertiesEditor.update("u\\rl=old\\\n value\n# keep\\\nother=x\n", mapOf("url" to "a\nü \\", "other" to null))
        val p = java.util.Properties().apply { load(output.reader()) }
        assertEquals("a\nü \\" , p.getProperty("url"))
        assertNull(p.getProperty("other"))
        assertTrue(output.contains("# keep\\"))
    }
    @Test fun `atomic save creates backup and rejects stale snapshot`() {
        val path = directory.resolve("gradle.properties")
        Files.writeString(path, "url=old\n")
        val updated = PropertiesEditor.apply(path, "url=old\n", feature.enabled)
        assertEquals(updated.text, PropertiesEditor.read(path))
        val backup = assertNotNull(updated.backupPath)
        try {
            assertEquals(Path.of(System.getProperty("java.io.tmpdir")).toRealPath(), backup.parent.toRealPath())
            assertEquals("url=old\n", Files.readString(backup))
            assertFalse(Files.exists(directory.resolve("gradle.properties.bak")))
        } finally { Files.deleteIfExists(backup) }
        Files.writeString(path, "external=true\n")
        assertFailsWith<IllegalStateException> { PropertiesEditor.apply(path, updated.text, feature.disabled) }
        assertEquals("external=true\n", Files.readString(path))
    }
    @Test fun `creates missing file and configuration rejects conflicting keys`() {
        val path = directory.resolve("gradle.properties")
        val saved = PropertiesEditor.apply(path, "", feature.enabled)
        assertNull(saved.backupPath)
        assertEquals(FeatureState.ENABLED, PropertiesEditor.state(PropertiesEditor.read(path), feature))
        assertFailsWith<IllegalArgumentException> { FeatureConfig(listOf(feature, feature.copy(id = "duplicate"))).validate() }
    }
    @Test fun `missing project is an error while missing properties in existing project are supported`() {
        assertEquals("", PropertiesEditor.read(directory.resolve("gradle.properties")))
        assertFailsWith<NoSuchFileException> { PropertiesEditor.read(directory.resolve("missing/gradle.properties")) }
    }
    @Test fun `unreadable properties are not mistaken for an empty file`() {
        val path = directory.resolve("gradle.properties")
        Files.writeString(path, "url=local")
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileStore(path).supportsFileAttributeView("posix"))
        val permissions = Files.getPosixFilePermissions(path)
        try {
            Files.setPosixFilePermissions(path, emptySet())
            org.junit.jupiter.api.Assumptions.assumeFalse(Files.isReadable(path), "Privileged users can bypass file permissions")
            assertFailsWith<AccessDeniedException> { PropertiesEditor.read(path) }
        } finally { Files.setPosixFilePermissions(path, permissions) }
    }
    @Test fun `mixed state diagnostics distinguish absent keys from mismatched values`() {
        val details = PropertiesEditor.stateDetails("url=local\nauth=unexpected\n", feature)
        assertTrue(details.contains("url: matches enabled"))
        assertTrue(details.contains("auth: value matches neither state"))
        assertTrue(PropertiesEditor.stateDetails("", feature).contains("url: missing"))
    }
    @Test fun `successive saves keep separate temp backups and leave project sidecars untouched`() {
        val path = directory.resolve("gradle.properties")
        val sidecar = directory.resolve("gradle.properties.bak")
        Files.writeString(path, "url=old\n")
        Files.writeString(sidecar, "existing user backup")
        val backups = mutableListOf<Path>()
        try {
            val first = PropertiesEditor.apply(path, "url=old\n", feature.enabled)
            backups.add(assertNotNull(first.backupPath))
            val second = PropertiesEditor.apply(path, first.text, feature.disabled)
            backups.add(assertNotNull(second.backupPath))
            assertNotEquals(backups[0], backups[1])
            assertEquals("url=old\n", Files.readString(backups[0]))
            assertEquals(first.text, Files.readString(backups[1]))
            assertEquals("existing user backup", Files.readString(sidecar))
            Files.list(directory).use { files ->
                assertEquals(setOf("gradle.properties", "gradle.properties.bak"), files.map { it.fileName.toString() }.toList().toSet())
            }
        } finally { backups.forEach { Files.deleteIfExists(it) } }
    }
}
