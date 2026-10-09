import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import javax.swing.AbstractButton
import javax.swing.JToggleButton
import javax.swing.plaf.basic.BasicToggleButtonUI

/** Keep state colors visible even when the native theme overrides button backgrounds. */
class StateToggleButton(state: FeatureState) : JToggleButton(
    when (state) {
        FeatureState.ENABLED -> "Enabled"
        FeatureState.DISABLED -> "Disabled"
        FeatureState.MIXED -> "Mixed"
    },
    state == FeatureState.ENABLED
) {
    init {
        setUI(object : BasicToggleButtonUI() {
            override fun paintButtonPressed(graphics: Graphics, button: AbstractButton) {
                graphics.color = if (button.model.isPressed && button.model.isArmed) {
                    button.background.darker()
                } else button.background
                graphics.fillRect(0, 0, button.width, button.height)
            }
        })
        background = when (state) {
            FeatureState.ENABLED -> Color(0x91D6A0)
            FeatureState.DISABLED -> Color(0xF1A0A0)
            FeatureState.MIXED -> Color(0xF4BE6A)
        }
        foreground = Color.BLACK
        isOpaque = true
        isContentAreaFilled = true
        margin = Insets(6, 12, 6, 12)
    }
    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        val metrics = getFontMetrics(font)
        val widestLabel = listOf("Enabled", "Disabled", "Mixed").maxOf { metrics.stringWidth(it) }
        size.width += widestLabel - metrics.stringWidth(text ?: "")
        return size
    }

    override fun getMinimumSize(): Dimension = preferredSize
}
