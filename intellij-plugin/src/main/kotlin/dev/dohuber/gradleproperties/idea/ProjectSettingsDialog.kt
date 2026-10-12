package dev.dohuber.gradleproperties.idea

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent

class ProjectSettingsDialog(private val project: Project) : DialogWrapper(project) {
    private val properties = TextFieldWithBrowseButton()
    private val features = TextFieldWithBrowseButton()
    init {
        title = "Gradle Property Helper Settings"
        val values = ProjectSettings.get(project).getState()
        properties.text = ProjectSettings.resolve(project, values.propertiesPath)?.toString() ?: ""
        features.text = ProjectSettings.resolve(project, values.featureConfigPath)?.toString() ?: ""
        fun browse(field: TextFieldWithBrowseButton, title: String) {
            field.addActionListener {
                val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle(title)
                FileChooser.chooseFile(descriptor, project, null) { file -> field.text = file.path }
            }
        }
        browse(properties, "Select gradle.properties")
        browse(features, "Select Feature JSON")
        init()
    }
    override fun createCenterPanel(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Properties file:", properties)
        .addLabeledComponent("Feature JSON:", features)
        .addComponent(JBLabel("Selections are saved for this project. The JSON format matches the desktop app."))
        .panel

    override fun doValidate(): ValidationInfo? {
        for ((field, isProperties) in listOf(properties to true, features to false)) {
            try {
                val path = Path.of(field.text.trim())
                if (!path.isAbsolute || !Files.isRegularFile(path)) return ValidationInfo("Select an existing file using its absolute path.", field)
                if (isProperties && path.fileName.toString() != "gradle.properties") return ValidationInfo("Select a gradle.properties file.", field)
            } catch (_: Exception) { return ValidationInfo("Invalid file path.", field) }
        }
        // Feature JSON is validated on refresh through the IDE document, including unsaved edits.
        return null
    }
    override fun doOKAction() {
        val values = ProjectSettings.get(project).getState()
        values.propertiesPath = ProjectSettings.store(project, Path.of(properties.text.trim()))
        values.featureConfigPath = ProjectSettings.store(project, Path.of(features.text.trim()))
        super.doOKAction()
    }
}
