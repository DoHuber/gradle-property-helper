package dev.dohuber.gradleproperties.idea

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import dev.dohuber.gradleproperties.core.Feature
import dev.dohuber.gradleproperties.core.FeatureConfig
import dev.dohuber.gradleproperties.core.FeatureState
import dev.dohuber.gradleproperties.core.PropertiesEditor
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JPanel
import javax.swing.JButton

class PropertyToolWindowPanel(private val project: Project) : JBPanel<PropertyToolWindowPanel>(BorderLayout()), Disposable {
    private data class Snapshot(
        val file: VirtualFile,
        val document: Document,
        val text: String,
        val configDocument: Document,
        val configText: String,
        val config: FeatureConfig
    )
    private val rows = JPanel(GridBagLayout())
    private val status = JBLabel("Select feature JSON in Settings.")
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private var snapshot: Snapshot? = null
    private var selectedPaths = emptySet<String>()
    private var generation = 0
    private var saving = false
    @Volatile private var disposed = false

    init {
        val actions = DefaultActionGroup().apply {
            add(object : DumbAwareAction("Refresh", "Reload properties and feature JSON", AllIcons.Actions.Refresh) {
                override fun actionPerformed(event: AnActionEvent) { refresh() }
            })
            add(object : DumbAwareAction("Edit Properties", "Open gradle.properties in the editor", AllIcons.Actions.Edit) {
                override fun actionPerformed(event: AnActionEvent) {
                    snapshot?.file?.let { FileEditorManager.getInstance(project).openFile(it, true) }
                }
            })
            add(object : DumbAwareAction("Settings", "Select properties and feature JSON for this project", AllIcons.General.Settings) {
                override fun actionPerformed(event: AnActionEvent) {
                    if (ProjectSettingsDialog(project).showAndGet()) refresh()
                }
            })
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("GradlePropertyHelper", actions, true)
        toolbar.targetComponent = this
        add(toolbar.component, BorderLayout.NORTH)
        add(JBScrollPane(JPanel(BorderLayout()).apply { add(rows, BorderLayout.NORTH) }), BorderLayout.CENTER)
        status.border = JBUI.Borders.empty(6)
        add(status, BorderLayout.SOUTH)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val path = FileDocumentManager.getInstance().getFile(event.document)?.path ?: return
                if (!saving && path in selectedPaths) scheduleRefresh()
            }
        }, this)
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (!saving && events.any { event -> selectedPaths.any { it == event.path || it.startsWith(event.path + "/") } }) scheduleRefresh()
            }
        })
        refresh()
    }
    override fun dispose() { disposed = true }
    internal fun onUi(action: () -> Unit) {
        // These callbacks include document writes. `any()` explicitly marks
        // callbacks write-unsafe, even when no dialog is currently open.
        ApplicationManager.getApplication().invokeLater({
            if (!disposed && !project.isDisposed) action()
        }, ModalityState.nonModal())
    }
    private fun scheduleRefresh() = onUi {
        alarm.cancelAllRequests()
        alarm.addRequest({ if (!saving) refresh() }, 150)
    }

    private fun refresh() {
        if (disposed || saving) return
        val request = ++generation
        val settings = ProjectSettings.get(project).getState()
        val paths = try {
            ProjectSettings.resolve(project, settings.propertiesPath) to ProjectSettings.resolve(project, settings.featureConfigPath)
        } catch (error: Exception) { showLoadError(error); return }
        selectedPaths = listOfNotNull(paths.first, paths.second).map { it.toString().replace('\\', '/') }.toSet()
        snapshot = null
        render()
        if (paths.second == null) { status.text = "Select feature JSON in Settings."; return }
        status.text = "Loading project settings…"
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val propertiesFile = findFile(paths.first, "properties")
                val configFile = findFile(paths.second, "feature JSON")
                val loaded = ReadAction.compute<Snapshot, RuntimeException> {
                    val manager = FileDocumentManager.getInstance()
                    val document = checkNotNull(manager.getDocument(propertiesFile)) { "Cannot open properties document." }
                    val configDocument = checkNotNull(manager.getDocument(configFile)) { "Cannot open feature JSON document." }
                    val text = document.text
                    val configText = configDocument.text
                    val config = FeatureConfig.parse(configText)
                    // Validate the property syntax even when no feature happens to use a malformed entry.
                    config.features.forEach { PropertiesEditor.state(text, it) }
                    Snapshot(propertiesFile, document, text, configDocument, configText, config)
                }
                onUi {
                    if (request == generation) {
                        snapshot = loaded
                        render()
                        status.text = loaded.file.path
                        status.toolTipText = "Current file: ${loaded.file.path}. Editor changes update these states automatically."
                    }
                }
            } catch (error: Exception) {
                if (error is com.intellij.openapi.progress.ProcessCanceledException) throw error
                onUi { if (request == generation) showLoadError(error) }
            }
        }
    }
    private fun findFile(path: Path?, label: String): VirtualFile {
        require(path != null) { "Select a $label file in Settings." }
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString().replace('\\', '/'))
        require(file != null && !file.isDirectory) { "Cannot find $label file: $path" }
        return file
    }
    private fun showLoadError(error: Exception) {
        snapshot = null; render()
        status.text = "Could not load: ${error.message ?: error.javaClass.simpleName}"
        status.toolTipText = status.text
    }
    private fun render() {
        rows.removeAll()
        val loaded = snapshot
        loaded?.config?.features?.forEachIndexed { index, feature ->
            val state = PropertiesEditor.state(loaded.text, feature)
            fun cell(column: Int) = GridBagConstraints().apply {
                gridx = column; gridy = index; anchor = GridBagConstraints.WEST
                weightx = if (column == 2) 1.0 else 0.0
                insets = JBUI.insets(3, 6)
            }
            val details = PropertiesEditor.stateDetails(loaded.text, feature)
            rows.add(JBLabel(feature.label).apply { toolTipText = details }, cell(0))
            rows.add(JBLabel("●").apply {
                foreground = when (state) {
                    FeatureState.ENABLED -> JBColor(Color(0x287A3F), Color(0x79BF8A))
                    FeatureState.DISABLED -> JBColor(Color(0xAB3333), Color(0xEC8585))
                    FeatureState.MIXED -> JBColor(Color(0xA66500), Color(0xE9AD55))
                }
                toolTipText = state.name.lowercase()
            }, cell(1))
            val label = if (state == FeatureState.ENABLED) "Disable" else "Enable"
            rows.add(JButton(label).apply {
                val size = preferredSize
                val metrics = getFontMetrics(font)
                size.width += listOf("Enable", "Disable").maxOf { metrics.stringWidth(it) } - metrics.stringWidth(label)
                preferredSize = size
                isEnabled = !saving
                toolTipText = details + "; Click to " + (if (state == FeatureState.ENABLED) "disable" else "enable") + " this group."
                accessibleContext.accessibleName = "${feature.label}: $label"
                addActionListener {
                    applyFeature(loaded, feature)
                }
            }, cell(2))
        }
        rows.revalidate(); rows.repaint()
    }
    private fun applyFeature(loaded: Snapshot, feature: Feature) {
        if (saving) return
        val settings = ProjectSettings.get(project).getState()
        val selection = settings.propertiesPath to settings.featureConfigPath
        saving = true; ++generation
        render()
        status.text = "Saving ${feature.label}…"
        ApplicationManager.getApplication().executeOnPooledThread {
            var backup: Path? = null
            try {
                backup = Files.createTempFile("gradle-property-helper-", ".gradle.properties.bak")
                Files.writeString(backup, loaded.text)
                val savedBackup = backup
                onUi {
                    try {
                        val current = ProjectSettings.get(project).getState()
                        check(selection == (current.propertiesPath to current.featureConfigPath)) { "Selected files changed. Refresh before applying." }
                        check(loaded.configDocument.text == loaded.configText) { "Feature JSON changed. Refresh before applying." }
                        IdeaPropertyDocuments.toggle(project, loaded.file, loaded.document, loaded.text, feature)
                        status.text = "Saved ${feature.label}. Backup: $savedBackup"
                        status.toolTipText = savedBackup.toString()
                        snapshot = loaded.copy(text = loaded.document.text)
                    } catch (error: Exception) {
                        Messages.showErrorDialog(project, "${error.javaClass.simpleName}: ${error.message}\nBackup: $savedBackup", "Gradle Property Helper")
                        status.text = "Save failed. Use Refresh to reload the current state."
                    } finally { saving = false; render(); scheduleRefreshAfterSave() }
                }
            } catch (error: Exception) {
                backup?.let { runCatching { Files.deleteIfExists(it) } }
                onUi {
                    saving = false; render()
                    Messages.showErrorDialog(project, "Could not create a temp backup: ${error.message}", "Gradle Property Helper")
                    status.text = "Save failed; properties were not changed."
                }
            }
        }
    }
    private fun scheduleRefreshAfterSave() {
        // Listener-triggered updates pick up Undo/Redo and subsequent manual edits.
        // Do not immediately replace the save status (which contains the backup location).
        alarm.cancelAllRequests()
    }
}
