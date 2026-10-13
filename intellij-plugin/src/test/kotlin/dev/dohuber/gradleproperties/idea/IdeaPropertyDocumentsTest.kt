package dev.dohuber.gradleproperties.idea

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import dev.dohuber.gradleproperties.core.Feature
import dev.dohuber.gradleproperties.core.FeatureState
import dev.dohuber.gradleproperties.core.PropertiesEditor
import org.junit.Assert.assertThrows

class IdeaPropertyDocumentsTest : BasePlatformTestCase() {
    private val feature = Feature("parallel", "Parallel", mapOf("org.gradle.parallel" to "true"), mapOf("org.gradle.parallel" to "false"))

    fun testTogglePreservesUnsavedEditsAndSupportsUndoRedo() {
        val file = myFixture.configureByText("gradle.properties", "# keep\norg.gradle.parallel=false\nother=original\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("other=original", "other=unsaved")) }
        val before = document.text
        IdeaPropertyDocuments.toggle(project, file, document, before, feature)
        assertEquals(FeatureState.ENABLED, PropertiesEditor.state(document.text, feature))
        assertTrue(document.text.contains("other=unsaved"))
        assertTrue(document.text.startsWith("# keep\n"))
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(before, document.text)
        myFixture.performEditorAction(IdeActions.ACTION_REDO)
        assertEquals(FeatureState.ENABLED, PropertiesEditor.state(document.text, feature))
    }
    fun testDeferredToggleUsesWriteSafeUiCallback() {
        val file = myFixture.configureByText("gradle.properties", "org.gradle.parallel=false\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val before = document.text
        val panel = PropertyToolWindowPanel(project)
        Disposer.register(testRootDisposable, panel)
        var completed = false
        panel.onUi {
            IdeaPropertyDocuments.toggle(project, file, document, before, feature)
            completed = true
        }
        UIUtil.dispatchAllInvocationEvents()
        assertTrue("The deferred write callback should execute", completed)
        assertEquals(FeatureState.ENABLED, PropertiesEditor.state(document.text, feature))
    }
    fun testStaleSnapshotIsRejectedWithoutOverwritingEditor() {
        val file = myFixture.configureByText("gradle.properties", "org.gradle.parallel=false\nother=current\n").virtualFile
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val before = document.text
        assertThrows(IllegalStateException::class.java) {
            IdeaPropertyDocuments.toggle(project, file, document, "org.gradle.parallel=false\nother=stale\n", feature)
        }
        assertEquals(before, document.text)
    }
    fun testProjectSettingsUseProjectRelativePathsWhenPossible() {
        val base = java.nio.file.Path.of(project.basePath!!)
        val config = base.resolve("features.json")
        assertEquals("features.json", ProjectSettings.store(project, config))
        assertEquals(config.normalize(), ProjectSettings.resolve(project, "features.json"))
        assertNull(ProjectSettings.resolve(project, ""))
        val settings = ProjectSettings.get(project)
        settings.getState().featureConfigPath = "features.json"
        assertEquals("features.json", ProjectSettings.get(project).getState().featureConfigPath)
    }
}
