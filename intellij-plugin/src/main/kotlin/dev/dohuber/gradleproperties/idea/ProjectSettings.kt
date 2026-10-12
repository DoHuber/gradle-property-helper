package dev.dohuber.gradleproperties.idea

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import java.nio.file.Path

@Service(Service.Level.PROJECT)
@State(name = "GradlePropertyHelper", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ProjectSettings : PersistentStateComponent<ProjectSettings.Values> {
    class Values {
        var propertiesPath: String = "gradle.properties"
        var featureConfigPath: String = ""
    }
    private var values = Values()
    override fun getState() = values
    override fun loadState(state: Values) { values = state }

    companion object {
        fun get(project: Project): ProjectSettings = project.getService(ProjectSettings::class.java)
        fun resolve(project: Project, stored: String): Path? {
            if (stored.isBlank()) return null
            val path = Path.of(stored)
            return if (path.isAbsolute) path.normalize() else project.basePath?.let { Path.of(it).resolve(path).normalize() }
        }
        fun store(project: Project, absolute: Path): String {
            val path = absolute.toAbsolutePath().normalize()
            val base = project.basePath?.let { Path.of(it).toAbsolutePath().normalize() }
            return if (base != null && path.startsWith(base)) base.relativize(path).toString() else path.toString()
        }
    }
}
