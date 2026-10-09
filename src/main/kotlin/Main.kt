import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.Insets
import java.nio.file.Path
import javax.swing.*

fun main() {
    SwingUtilities.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        HelperWindow().isVisible = true
    }
}

class HelperWindow : JFrame("Gradle Property Helper") {
    private val settingsStore = SettingsStore()
    private var settings = AppSettings()
    private var file: Path? = null
    private var config: FeatureConfig? = null
    private var snapshot = ""
    private val rows = JPanel(GridBagLayout())
    private val status = JLabel("Open Settings to choose gradle.properties and feature configuration.")
    private val projectLabel = JLabel("No properties file selected")
    private val configLabel = JLabel("No configuration selected")
    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(620, 360)
        val toolbar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(JButton("Refresh").apply { addActionListener { guarded {
                file?.let { snapshot = PropertiesEditor.read(it) }; render(); status.text = "Refreshed from disk."
            } } }, BorderLayout.WEST)
            add(JButton("⚙ Settings").apply {
                toolTipText = "Choose gradle.properties and feature configuration"
                accessibleContext.accessibleName = "Settings"
                addActionListener { showSettings() }
            }, BorderLayout.EAST)
        }
        add(toolbar, BorderLayout.NORTH)
        val featurePanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(0, 8, 8, 8)
            add(rows, BorderLayout.NORTH)
        }
        add(JScrollPane(featurePanel), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        restoreSettings()
        pack(); setLocationRelativeTo(null)
    }
    private fun restoreSettings() {
        val restored = settingsStore.restore()
        settings = restored.settings
        file = restored.propertiesFile
        snapshot = restored.propertiesText
        config = restored.featureConfig
        projectLabel.text = restored.propertiesFile?.toString() ?: settings.projectDirectory ?: "No properties file selected"
        configLabel.text = settings.featureConfigPath ?: "No configuration selected"
        render()
        status.text = when {
            restored.warnings.isNotEmpty() -> "Some settings could not be restored. Open Settings to select them again."
            file != null && config != null -> "Restored saved project and feature configuration."
            else -> "Open Settings to choose gradle.properties and feature configuration."
        }
        if (restored.warnings.isNotEmpty()) SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(this, restored.warnings.joinToString("\n"), "Settings restoration", JOptionPane.WARNING_MESSAGE)
        }
    }
    private fun showSettings() {
        val dialog = JDialog(this, "Settings", true).apply {
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        }
        val controls = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(12, 12, 12, 12)
        }
        fun addControl(label: JLabel, title: String, action: () -> Unit) {
            controls.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JButton(title).apply { addActionListener { guarded(action) } })
            })
            controls.add(label)
            controls.add(Box.createVerticalStrut(12))
        }
        addControl(projectLabel, "Choose gradle.properties…") {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.FILES_ONLY
                isAcceptAllFileFilterUsed = false
                fileFilter = object : javax.swing.filechooser.FileFilter() {
                    override fun accept(candidate: java.io.File) = candidate.isDirectory || candidate.name == "gradle.properties"
                    override fun getDescription() = "Gradle properties (gradle.properties)"
                }
                file?.let { selectedFile = it.toFile() }
            }
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                val selected = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                require(selected.fileName.toString() == "gradle.properties" && java.nio.file.Files.isRegularFile(selected)) {
                    "Select an existing gradle.properties file: $selected"
                }
                require(!java.nio.file.Files.isSymbolicLink(selected)) { "Symbolic-link property files are not supported: $selected" }
                val content = PropertiesEditor.read(selected)
                java.util.Properties().load(content.reader())
                val updated = settings.copy(projectDirectory = selected.parent.toString())
                settingsStore.save(updated); settings = updated
                file = selected; snapshot = content; projectLabel.text = selected.toString(); render()
                status.text = "Selected properties file: $selected"
                dialog.pack()
            }
        }
        addControl(configLabel, "Load feature JSON…") {
            val chooser = JFileChooser().apply { fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON configuration", "json") }
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                val selected = chooser.selectedFile.toPath().toAbsolutePath().normalize()
                val loaded = FeatureConfig.load(selected)
                val updated = settings.copy(featureConfigPath = selected.toString())
                settingsStore.save(updated); settings = updated
                config = loaded
                configLabel.text = selected.toString(); render()
                dialog.pack()
            }
        }
        val close = JButton("Done").apply { addActionListener { dialog.dispose() } }
        controls.add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply { add(close) })
        dialog.contentPane.add(controls)
        dialog.rootPane.defaultButton = close
        dialog.rootPane.registerKeyboardAction(
            { dialog.dispose() }, KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW
        )
        dialog.minimumSize = Dimension(480, 240)
        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isVisible = true
    }
    private fun guarded(action: () -> Unit) {
        try { action() } catch (e: Exception) {
            val message = ErrorMessages.describe(e)
            status.text = "Action failed: ${message.lineSequence().first()}"
            JOptionPane.showMessageDialog(this, message, "Unable to complete action", JOptionPane.ERROR_MESSAGE)
        }
    }
    private fun render() {
        rows.removeAll()
        if (file != null) config?.features?.forEachIndexed { index, feature ->
            val state = PropertiesEditor.state(snapshot, feature)
            fun cell(column: Int) = GridBagConstraints().apply {
                gridx = column; gridy = index
                anchor = GridBagConstraints.WEST
                weightx = if (column == 1) 1.0 else 0.0
                insets = Insets(3, 8, 3, 8)
            }
            run {
                val nextEnabled = state != FeatureState.ENABLED
                val values = if (nextEnabled) feature.enabled else feature.disabled
                val details = PropertiesEditor.stateDetails(snapshot, feature)
                rows.add(JLabel(feature.label).apply { toolTipText = details }, cell(0))
                rows.add(StateToggleButton(state).apply {
                    toolTipText = PropertiesEditor.stateDetails(snapshot, feature) +
                        "; Click to " + (if (nextEnabled) "enable" else "disable") + " this group."
                    accessibleContext.accessibleName = "${feature.label}: $text"
                    accessibleContext.accessibleDescription = if (state == FeatureState.MIXED) {
                        "Properties are mixed. Activate to enable the whole group."
                    } else "Activate to " + (if (nextEnabled) "enable" else "disable") + " this group."
                    addActionListener {
                        // Swing changes selection before notifying listeners. Show only the
                        // last saved state until the complete group has been written successfully.
                        isSelected = state == FeatureState.ENABLED
                        guarded {
                            snapshot = PropertiesEditor.apply(file!!, snapshot, values)
                            render(); status.text = "Saved ${feature.label}. Previous file backed up as gradle.properties.bak."
                        }
                    }
                }, cell(1))
            }
        }
        rows.revalidate(); rows.repaint()
    }
}
