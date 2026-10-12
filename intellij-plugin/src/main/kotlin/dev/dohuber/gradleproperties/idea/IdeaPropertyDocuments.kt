package dev.dohuber.gradleproperties.idea

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import dev.dohuber.gradleproperties.core.Feature
import dev.dohuber.gradleproperties.core.FeatureState
import dev.dohuber.gradleproperties.core.PropertiesEditor

/** Called on the EDT. Edits the IDE document rather than replacing a file under the editor. */
object IdeaPropertyDocuments {
    fun toggle(project: Project, file: VirtualFile, document: Document, expected: String, feature: Feature) {
        check(!ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(file).hasReadonlyFiles()) {
            "The properties file is read-only: ${file.path}"
        }
        WriteCommandAction.writeCommandAction(project).withName("Toggle ${feature.label}").run<RuntimeException> {
            check(document.text == expected) { "Properties changed. Refresh before applying." }
            val values = if (PropertiesEditor.state(expected, feature) == FeatureState.ENABLED) feature.disabled else feature.enabled
            val updated = PropertiesEditor.update(expected, values)
            document.setText(updated)
        }
        FileDocumentManager.getInstance().saveDocument(document)
        check(!FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
            "The document was updated, but could not be saved. Use the editor to save it or Undo the change."
        }
    }
}
