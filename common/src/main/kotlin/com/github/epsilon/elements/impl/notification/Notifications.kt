package com.github.epsilon.elements.impl.notification

import com.github.epsilon.assets.i18n.EpsilonTranslations
import com.github.epsilon.elements.HudModule
import com.github.epsilon.elements.impl.notification.style.AkarinNotification
import com.github.epsilon.elements.impl.notification.style.NVIDIANotification
import com.github.epsilon.elements.impl.notification.style.QuantumNotification
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.hudeditor.HudEditorScreen
import com.github.epsilon.managers.NotificationManager
import com.google.common.base.Suppliers
import net.minecraft.client.DeltaTracker
import java.util.function.Supplier

object Notifications : HudModule("Notifications", 3.2f, 3.2f, 120.0f, 30.0f) {

    enum class Style {
        NVIDIA,
        Quantum,
        Akarin,
    }

    private val style by enumSetting("Style", Style.Quantum, onChanged = { _: Style -> clearQuantum() })
    private val scale by doubleSetting("Scale", 1.0, 0.5..2.0, 0.05)
    private val fontScale by doubleSetting("Font Scale", 0.8, 0.5..2.0, 0.05)

    private val nvidia = NVIDIANotification(this)
    private val quantum = QuantumNotification(this)
    private val akarin = AkarinNotification(this)

    private val textRendererSupplier: Supplier<TextRenderer> =
        Suppliers.memoize(com.google.common.base.Supplier { TextRenderer.create() })

    override fun render(deltaTracker: DeltaTracker) {
        NotificationManager.update()

        var preview: Notification? = null
        if (mc.gui.screen() is HudEditorScreen) {
            preview = Notification.preview(
                EpsilonTranslations.Notifications.PREVIEW_TITLE.getTranslatedName(),
                EpsilonTranslations.Notifications.PREVIEW_MESSAGE.getTranslatedName(),
                if (isStyle(Style.Quantum)) NotificationMode.Info else NotificationMode.Success
            )
        }
        val previewNotification = preview

        if (NotificationManager.isEmpty() && previewNotification == null) return

        val metrics = textRendererSupplier.get()
        val scope = renderScope()
        if (isStyle(Style.Quantum)) {
            val scale = getScale()
            val entries: MutableList<Notification> = ArrayList(NotificationManager.getNotifications())
            entries.removeAll { notification -> !quantum.isVisible(notification) }
            entries.reverse()
            if (entries.isEmpty() && previewNotification != null) entries.add(previewNotification)
            if (entries.isEmpty()) return
            setNotificationBounds(quantum.width(scale), quantum.totalHeight(entries.size, scale))
            quantum.render(scope, metrics, entries, x, y, scale, getFontScale(), horizontalAnchor, verticalAnchor)
        } else if (isStyle(Style.Akarin)) {
            val scale = getScale()
            val entries: MutableList<Notification> = ArrayList(NotificationManager.getNotifications())
            entries.removeAll { notification -> !akarin.isVisible(notification) }
            entries.reverse()
            if (entries.isEmpty() && previewNotification != null) entries.add(previewNotification)
            if (entries.isEmpty()) return
            val boundsWidth = akarin.width(metrics, entries, scale, getFontScale())
            setNotificationBounds(boundsWidth, akarin.totalHeight(entries.size, scale))
            akarin.render(
                scope,
                metrics,
                entries,
                x,
                y,
                boundsWidth,
                scale,
                getFontScale(),
                horizontalAnchor,
                verticalAnchor
            )
        } else {
            nvidia.render(scope, metrics, previewNotification)
        }
    }

    fun getDisplayTime(): Int {
        return if (isStyle(Style.Quantum)) quantum.getDisplayTime()
        else if (isStyle(Style.Akarin)) akarin.getDisplayTime() else nvidia.getDisplayTime()
    }

    fun getScale(): Float {
        return scale.toFloat()
    }

    fun getFontScale(): Float {
        return fontScale.toFloat()
    }

    fun isStyle(expected: Style): Boolean {
        return style == expected
    }

    fun setNotificationBounds(width: Float, height: Float) {
        setBounds(width, height)
    }

    private fun clearQuantum() {
        quantum.clear()
    }

}
