package com.github.epsilon.gui.panel.popup

import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.Mth
import java.awt.Color
import java.util.Locale

class ColorPickerPopup(
    private val bounds: UiRect,
    private val anchorBounds: UiRect,
    private val setting: ColorSetting,
) : PanelPopupHost.Popup {

    private enum class Channel(val shortLabel: String, val accent: Color) {
        RED("R", Color(239, 83, 80)),
        GREEN("G", Color(102, 187, 106)),
        BLUE("B", Color(66, 165, 245)),
        ALPHA("A", MD3Theme.PRIMARY)
    }

    private val textRenderer: TextRenderer = TextRenderer.create()
    private val openAnimation = Animation(Easing.EASE_OUT_CUBIC, 160L)
    private val indicatorAnimations = arrayOf(
        Animation(Easing.EASE_OUT_CUBIC, 120L),
        Animation(Easing.EASE_OUT_CUBIC, 120L),
        Animation(Easing.EASE_OUT_CUBIC, 120L),
        Animation(Easing.EASE_OUT_CUBIC, 120L)
    )
    private var draggingChannel: Channel? = null
    private var focusedChannel: Channel? = null
    private var inputBuffer: String? = null
    private var cursorIndex = 0
    private var pendingValue: Color? = null

    init {
        this.openAnimation.startValue = 0.0f
        for (animation in indicatorAnimations) {
            animation.startValue = 0.0f
        }
    }

    override fun getBounds(): UiRect {
        return bounds
    }

    override fun extractGui(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        val tree = UiTree.build { scope ->
            val progress = scope.animate(openAnimation, 1.0f)
            val popupY = bounds.y - (1.0f - progress) * 6.0f
            val popupBounds = UiRect(bounds.x, popupY, bounds.width, bounds.height)
            scope.pushAbsolute(popupBounds) { popup ->
                popup.popupCard(
                    popupBounds.atOrigin(),
                    MD3Theme.CARD_RADIUS.toFloat(),
                    MD3Theme.POPUP_SHADOW_BLUR,
                    MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                    MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
                )
                popup.pushAbsolute(anchorBounds) { anchor ->
                    anchor.roundRect(
                        0.0f, 0.0f, anchorBounds.width, anchorBounds.height,
                        MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 255)
                    )
                }

                val previewBounds = getPreviewBounds(popupY)
                val swatchBounds = getPreviewSwatchBounds(popupY)
                val localPreviewBounds = previewBounds.relativeTo(popupBounds)
                val localSwatchBounds = swatchBounds.relativeTo(popupBounds)
                val previewSurface = if (MD3Theme.isLightTheme()) MD3Theme.SURFACE_CONTAINER_HIGH else MD3Theme.SURFACE_CONTAINER
                val swatchSurface =
                    if (MD3Theme.isLightTheme()) MD3Theme.SURFACE_CONTAINER else MD3Theme.SURFACE_CONTAINER_HIGHEST
                popup.roundRect(
                    localPreviewBounds.x, localPreviewBounds.y, localPreviewBounds.width, localPreviewBounds.height,
                    10.0f, MD3Theme.withAlpha(previewSurface, 255)
                )
                popup.roundRect(
                    localSwatchBounds.x, localSwatchBounds.y, localSwatchBounds.width, localSwatchBounds.height,
                    8.0f, MD3Theme.withAlpha(swatchSurface, 255)
                )
                if (setting.isAllowAlpha()) {
                    buildCheckerboard(popup, localSwatchBounds, 255)
                }
                popup.roundRect(
                    localSwatchBounds.x, localSwatchBounds.y, localSwatchBounds.width, localSwatchBounds.height,
                    8.0f, getVisibleColor()
                )
                popup.roundRect(
                    localSwatchBounds.x, localSwatchBounds.y, localSwatchBounds.width, localSwatchBounds.height,
                    8.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE_SOFT, (72 * progress).toInt())
                )
                popup.text(
                    formatHex(getVisibleColor()), localPreviewBounds.x + 40.0f, localPreviewBounds.y + 8.0f,
                    0.64f, MD3Theme.TEXT_PRIMARY
                )
                popup.text(
                    if (setting.isAllowAlpha()) "RGBA" else "RGB",
                    localPreviewBounds.x + 40.0f, localPreviewBounds.y + 19.0f, 0.52f, MD3Theme.TEXT_SECONDARY
                )

                val channels = getChannels()
                for (i in channels.indices) {
                    val channel = channels[i]
                    val rowBounds = getChannelRowBounds(popupY, i)
                    val localRowBounds = rowBounds.relativeTo(popupBounds)
                    val trackBounds = getTrackBounds(rowBounds)
                    val localTrackBounds = trackBounds.relativeTo(popupBounds)
                    val valueBounds = getValueBounds(rowBounds)
                    val localValueBounds = valueBounds.relativeTo(popupBounds)
                    val hovered = rowBounds.contains(mouseX.toDouble(), mouseY.toDouble())
                    val channelProgress = getChannelValue(channel) / 255.0f
                    val handleX = localTrackBounds.x + localTrackBounds.width * channelProgress - 2.0f
                    val indicatorProgress = scope.animate(
                        indicatorAnimations[i], hovered || draggingChannel === channel
                    )
                    val rowSurface: Color = if (hovered) {
                        if (MD3Theme.isLightTheme()) MD3Theme.SURFACE_CONTAINER_HIGHEST else MD3Theme.SURFACE_CONTAINER_HIGH
                    } else {
                        MD3Theme.SURFACE_CONTAINER
                    }

                    popup.text(
                        channel.shortLabel, localRowBounds.x + 8.0f, localRowBounds.y + 6.5f, 0.58f,
                        if (MD3Theme.isLightTheme()) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_SECONDARY
                    )
                    popup.roundRect(
                        localRowBounds.x, localRowBounds.y, localRowBounds.width, localRowBounds.height,
                        8.0f, MD3Theme.withAlpha(rowSurface, 255)
                    )
                    popup.slider(
                        localTrackBounds, channelProgress, 2.5f,
                        MD3Theme.withAlpha(
                            if (MD3Theme.isLightTheme()) MD3Theme.SURFACE_CONTAINER_HIGH
                            else MD3Theme.SURFACE_CONTAINER_HIGHEST,
                            255
                        ),
                        0.0f, 3.0f, MD3Theme.withAlpha(channel.accent, 255),
                        4.0f, 12.0f, 2.0f, MD3Theme.withAlpha(MD3Theme.ON_PRIMARY_CONTAINER, 255)
                    )

                    val valueText =
                        if (focusedChannel === channel) getDisplayBuffer() else getChannelValue(channel).toString()
                    buildValueBox(popup, localValueBounds, valueText, focusedChannel === channel, 255)
                    if (indicatorProgress > 0.01f) {
                        buildValueIndicator(
                            popup, handleX + 2.0f, localRowBounds.y - 4.0f,
                            getChannelValue(channel).toString(), indicatorProgress
                        )
                    }
                }
            }
        }
        renderBatch.render(tree)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            return false
        }
        val channels = getChannels()
        for (i in channels.indices) {
            val rowBounds = getChannelRowBounds(bounds.y, i)
            val trackBounds = getTrackBounds(rowBounds)
            val valueBounds = getValueBounds(rowBounds)
            if (valueBounds.contains(event.x(), event.y())) {
                draggingChannel = null
                focusedChannel = channels[i]
                inputBuffer = getChannelValue(focusedChannel!!).toString()
                cursorIndex = getCursorIndex(event.x(), valueBounds, inputBuffer!!)
                return true
            }
            val interactiveBounds = UiRect(
                trackBounds.x, trackBounds.y - 5.0f, trackBounds.width, trackBounds.height + 10.0f
            )
            if (interactiveBounds.contains(event.x(), event.y())) {
                draggingChannel = channels[i]
                focusedChannel = null
                inputBuffer = null
                updateChannelFromMouse(draggingChannel!!, event.x(), trackBounds)
                return true
            }
        }
        focusedChannel = null
        inputBuffer = null
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && draggingChannel != null) {
            commitPendingColor()
        }
        draggingChannel = null
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, mouseX: Double, mouseY: Double): Boolean {
        val dragging = draggingChannel
        if (dragging == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false
        }
        val index = getChannelIndex(dragging)
        val trackBounds = getTrackBounds(getChannelRowBounds(bounds.y, index))
        updateChannelFromMouse(dragging, event.x(), trackBounds)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val focused = focusedChannel ?: return false
        return when (event.key()) {
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                commitInput()
                focusedChannel = null
                inputBuffer = null
                true
            }

            InputConstants.KEY_ESCAPE -> {
                focusedChannel = null
                inputBuffer = null
                true
            }

            InputConstants.KEY_BACKSPACE -> {
                val buffer = inputBuffer
                if (buffer != null && cursorIndex > 0) {
                    inputBuffer = buffer.substring(0, cursorIndex - 1) + buffer.substring(cursorIndex)
                    cursorIndex--
                }
                true
            }

            InputConstants.KEY_DELETE -> {
                val buffer = inputBuffer
                if (buffer != null && cursorIndex < buffer.length) {
                    inputBuffer = buffer.substring(0, cursorIndex) + buffer.substring(cursorIndex + 1)
                }
                true
            }

            InputConstants.KEY_LEFT -> {
                cursorIndex = Math.max(0, cursorIndex - 1)
                true
            }

            InputConstants.KEY_RIGHT -> {
                cursorIndex = Math.min(getDisplayBuffer().length, cursorIndex + 1)
                true
            }

            else -> false
        }
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (focusedChannel == null) {
            return false
        }
        val typed = event.codepointAsString()
        if (!typed.matches("[0-9]".toRegex())) {
            return false
        }
        val current = getDisplayBuffer()
        if (current.length >= 3) {
            return true
        }
        inputBuffer = current.substring(0, cursorIndex) + typed + current.substring(cursorIndex)
        cursorIndex++
        return true
    }

    private fun getPreviewBounds(popupY: Float): UiRect {
        return UiRect(bounds.x + 8.0f, popupY + 8.0f, bounds.width - 16.0f, 34.0f)
    }

    private fun getPreviewSwatchBounds(popupY: Float): UiRect {
        return UiRect(bounds.x + 14.0f, popupY + 13.0f, 24.0f, 24.0f)
    }

    private fun getChannelRowBounds(popupY: Float, index: Int): UiRect {
        val rowY = popupY + 50.0f + index * 24.0f
        return UiRect(bounds.x + 8.0f, rowY, bounds.width - 16.0f, 20.0f)
    }

    private fun getTrackBounds(rowBounds: UiRect): UiRect {
        return UiRect(rowBounds.x + 18.0f, rowBounds.y + 8.0f, rowBounds.width - 48.0f, 4.0f)
    }

    private fun getValueBounds(rowBounds: UiRect): UiRect {
        return UiRect(rowBounds.right() - 24.0f, rowBounds.y + 2.0f, 18.0f, 16.0f)
    }

    private fun getChannels(): Array<Channel> {
        return if (setting.isAllowAlpha()) {
            arrayOf(Channel.RED, Channel.GREEN, Channel.BLUE, Channel.ALPHA)
        } else {
            arrayOf(Channel.RED, Channel.GREEN, Channel.BLUE)
        }
    }

    private fun getChannelIndex(channel: Channel): Int {
        val channels = getChannels()
        for (i in channels.indices) {
            if (channels[i] === channel) {
                return i
            }
        }
        return 0
    }

    private fun getChannelValue(channel: Channel): Int {
        val color = getVisibleColor()
        return when (channel) {
            Channel.RED -> color.red
            Channel.GREEN -> color.green
            Channel.BLUE -> color.blue
            Channel.ALPHA -> color.alpha
        }
    }

    private fun updateChannelFromMouse(channel: Channel, mouseX: Double, trackBounds: UiRect) {
        var progress = (mouseX - trackBounds.x) / trackBounds.width
        progress = Mth.clamp(progress, 0.0, 1.0)
        val value = Mth.clamp(Math.round(progress * 255.0).toInt(), 0, 255)
        val current = getVisibleColor()
        val updated = when (channel) {
            Channel.RED -> Color(value, current.green, current.blue, current.alpha)
            Channel.GREEN -> Color(current.red, value, current.blue, current.alpha)
            Channel.BLUE -> Color(current.red, current.green, value, current.alpha)
            Channel.ALPHA -> Color(current.red, current.green, current.blue, value)
        }
        previewColor(updated)
    }

    private fun formatHex(color: Color): String {
        if (setting.isAllowAlpha()) {
            return String.format(
                "#%02X%02X%02X%02X", color.red, color.green, color.blue, color.alpha
            )
        }
        return String.format("#%02X%02X%02X", color.red, color.green, color.blue)
    }

    private fun buildValueIndicator(
        scope: UiTree.Scope, anchorX: Float, rowTop: Float, valueText: String, progress: Float,
    ) {
        val textScale = 0.54f
        val bubbleWidth = textRenderer.getWidth(valueText, textScale) + 12.0f
        val bubbleHeight = 16.0f
        val bubbleX = anchorX - bubbleWidth / 2.0f
        val bubbleY = rowTop - 14.0f - (1.0f - progress) * 4.0f
        val bubbleAlpha = (255 * progress).toInt()
        scope.roundRect(
            bubbleX, bubbleY, bubbleWidth, bubbleHeight, 8.0f,
            MD3Theme.withAlpha(MD3Theme.INVERSE_SURFACE, bubbleAlpha)
        )
        val textY = bubbleY + (bubbleHeight - textRenderer.getHeight(textScale)) / 2.0f
        scope.text(
            valueText, bubbleX + (bubbleWidth - textRenderer.getWidth(valueText, textScale)) / 2.0f, textY,
            textScale, MD3Theme.withAlpha(MD3Theme.INVERSE_ON_SURFACE, bubbleAlpha)
        )
    }

    private fun buildValueBox(scope: UiTree.Scope, bounds: UiRect, valueText: String, focused: Boolean, alpha: Int) {
        val boxBase = if (MD3Theme.isLightTheme()) MD3Theme.SURFACE_CONTAINER else MD3Theme.SURFACE_CONTAINER_HIGH
        val boxHover = MD3Theme.SURFACE_CONTAINER_HIGHEST
        val boxColor: Color =
            if (focused) MD3Theme.INVERSE_SURFACE else MD3Theme.withAlpha(MD3Theme.lerp(boxBase, boxHover, 0.85f), alpha)
        val textColor: Color =
            if (focused) MD3Theme.INVERSE_ON_SURFACE else MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, alpha)
        scope.pushRelative(bounds) { box ->
            box.roundRect(0.0f, 0.0f, bounds.width, bounds.height, 6.0f, boxColor)
            val textScale = 0.52f
            val textWidth = textRenderer.getWidth(valueText, textScale)
            val textHeight = textRenderer.getHeight(textScale)
            val textX = (bounds.width - textWidth) / 2.0f
            val textY = (bounds.height - textHeight) / 2.0f
            box.text(valueText, textX, textY, textScale, textColor)
            if (focused) {
                val caretX = textX + textRenderer.getWidth(
                    valueText.substring(0, Math.min(cursorIndex, valueText.length)), textScale
                )
                box.rect(caretX, 3.0f, 1.0f, bounds.height - 6.0f, MD3Theme.INVERSE_ON_SURFACE)
            }
        }
    }

    private fun buildCheckerboard(scope: UiTree.Scope, bounds: UiRect, alpha: Int) {
        val inset = 2.0f
        val cell = 4.0f
        val dark = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, alpha)
        val light = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGH, alpha)
        var y = bounds.y + inset
        while (y < bounds.bottom() - inset) {
            var x = bounds.x + inset
            while (x < bounds.right() - inset) {
                val ix = Math.floor(((x - bounds.x) / cell).toDouble()).toInt()
                val iy = Math.floor(((y - bounds.y) / cell).toDouble()).toInt()
                val color = if ((ix + iy) and 1 == 0) light else dark
                val width = Math.min(cell, bounds.right() - inset - x)
                val height = Math.min(cell, bounds.bottom() - inset - y)
                scope.roundRect(x, y, width, height, 0.0f, color)
                x += cell
            }
            y += cell
        }
    }

    private fun commitInput() {
        val focused = focusedChannel ?: return
        val buffer = inputBuffer
        if (buffer == null || buffer.isBlank()) {
            return
        }
        var value: Int
        try {
            value = Mth.clamp(buffer.toInt(), 0, 255)
        } catch (ignored: NumberFormatException) {
            value = getChannelValue(focused)
        }
        val current = getVisibleColor()
        val updated = when (focused) {
            Channel.RED -> Color(value, current.green, current.blue, current.alpha)
            Channel.GREEN -> Color(current.red, value, current.blue, current.alpha)
            Channel.BLUE -> Color(current.red, current.green, value, current.alpha)
            Channel.ALPHA -> Color(current.red, current.green, current.blue, value)
        }
        applyColorNow(updated)
        inputBuffer = value.toString()
        cursorIndex = inputBuffer!!.length
    }

    private fun getDisplayBuffer(): String {
        val focused = focusedChannel
        return inputBuffer ?: (if (focused != null) getChannelValue(focused).toString() else "0")
    }

    private fun getCursorIndex(mouseX: Double, fieldBounds: UiRect, text: String): Int {
        val scale = 0.52f
        val textWidth = textRenderer.getWidth(text, scale)
        val textStart = fieldBounds.x + (fieldBounds.width - textWidth) / 2.0f
        for (i in 0..text.length) {
            val width = textRenderer.getWidth(text.substring(0, i), scale)
            if (mouseX <= textStart + width) {
                return i
            }
        }
        return text.length
    }

    private fun getVisibleColor(): Color {
        return pendingValue ?: setting.value
    }

    private fun previewColor(color: Color) {
        if (setting.isApplyWhenRelease) {
            pendingValue = color
        } else {
            setting.value = color
        }
    }

    private fun applyColorNow(color: Color) {
        pendingValue = null
        setting.value = color
    }

    private fun commitPendingColor() {
        val color = pendingValue ?: return
        applyColorNow(color)
    }

    override fun close() {
        textRenderer.close()
    }

}
