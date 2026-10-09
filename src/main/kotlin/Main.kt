import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.*

fun main() {
    SwingUtilities.invokeLater {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        HelperWindow().isVisible = true
    }
}

class HelperWindow : JFrame("Gradle Property Helper") {
    private var file: Path? = null
    private var config: FeatureConfig? = null
    private var snapshot = ""
    private val rows = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val status = JLabel("Choose a project directory and a feature configuration.")
    private val projectLabel = JLabel("No project selected")
    private val configLabel = JLabel("No configuration selected")
    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(620, 360)
        val controls = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        fun addControl(label: JLabel, title: String, action: () -> Unit) {
            controls.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JButton(title).apply { addActionListener { guarded(action) } }); add(label)
            })
        }
        addControl(projectLabel, "Choose project…") {
            val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                val selected = chooser.selectedFile.toPath().resolve("gradle.properties")
                val content = PropertiesEditor.read(selected)
                file = selected; snapshot = content; projectLabel.text = selected.toString(); render()
            }
        }
        addControl(configLabel, "Load feature JSON…") {
            val chooser = JFileChooser().apply { fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JSON configuration", "json") }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                config = FeatureConfig.load(chooser.selectedFile.toPath())
                configLabel.text = chooser.selectedFile.name; render()
            }
        }
        controls.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Refresh").apply { addActionListener { guarded {
                file?.let { snapshot = PropertiesEditor.read(it) }; render(); status.text = "Refreshed from disk."
            } } })
        })
        add(controls, BorderLayout.NORTH)
        add(JScrollPane(rows), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        pack(); setLocationRelativeTo(null)
    }
    private fun guarded(action: () -> Unit) {
        try { action() } catch (e: Exception) {
            status.text = "No change applied: ${e.message}"
            JOptionPane.showMessageDialog(this, e.message ?: e.javaClass.simpleName, "Unable to complete action", JOptionPane.ERROR_MESSAGE)
        }
    }
    private fun render() {
        rows.removeAll()
        if (file != null) config?.features?.forEach { feature ->
            val state = PropertiesEditor.state(snapshot, feature)
            rows.add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(JLabel("${feature.label}: ${state.name.lowercase()}"))
                fun button(title: String, values: Map<String, String?>, active: Boolean) {
                    add(JButton(title).apply {
                        isEnabled = !active
                        toolTipText = values.entries.joinToString("; ") { "${it.key} = ${it.value ?: "(removed)"}" }
                        addActionListener { guarded {
                            snapshot = PropertiesEditor.apply(file!!, snapshot, values)
                            render(); status.text = "Saved ${feature.label}. Previous file backed up as gradle.properties.bak."
                        } }
                    })
                }
                button("Enable", feature.enabled, state == FeatureState.ENABLED)
                button("Disable", feature.disabled, state == FeatureState.DISABLED)
            })
        }
        rows.revalidate(); rows.repaint()
    }
}
