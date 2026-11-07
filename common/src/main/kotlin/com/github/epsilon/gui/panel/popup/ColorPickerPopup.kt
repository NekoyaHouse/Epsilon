package com.github.epsilon.gui.panel.popup

import com.github.epsilon.kernel.state.*
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.LuminUIItem
import com.github.epsilon.gui.lib.Interaction
import com.github.epsilon.gui.lib.LuminUIScene
import com.github.epsilon.gui.lib.Positioned
import com.github.epsilon.gui.lib.PopupCard
import com.github.epsilon.gui.lib.Rect
import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.Slider
import com.github.epsilon.gui.lib.Text
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.render.UiRenderBatch
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.utils.render.animation.Easing
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import java.awt.Color
import kotlin.math.floor
import kotlin.math.roundToInt

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
    private val openAnimation = LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 160L)
    private val indicatorAnimations = Array(4) { LuminAnimatable(0.0f, Easing.EASE_OUT_CUBIC, 120L) }
    private var draggingChannel: Channel? by mutableStateOf(null)
    private var focusedChannel: Channel? by mutableStateOf<Channel?>(null)
    private var inputBuffer: String? by mutableStateOf<String?>(null)
    private var cursorIndex by mutableStateOf(0)
    private var pendingValue: Color? by mutableStateOf<Color?>(null)

    override fun getBounds(): UiRect {
        return bounds
    }

    override val inputView = com.github.epsilon.gui.lib.state.LuminView()

    override fun extractGui(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        inputView.render(mouseX, mouseY) { renderContent(guiGraphics, renderBatch, mouseX, mouseY, partialTick) }
    }

    private fun renderContent(
        guiGraphics: GuiGraphicsExtractor, renderBatch: UiRenderBatch,
        mouseX: Int, mouseY: Int, partialTick: Float,
    ) {
        // 推式动画：目标在命令阶段写入，绘制块内只读取 value。
        val popupY = bounds.y - (1.0f - openAnimation.value) * 6.0f
        val popupBounds = UiRect(bounds.x, popupY, bounds.width, bounds.height)
        val channels = getChannels()
        for (i in channels.indices) {
            val rowBounds = getChannelRowBounds(popupY, i)
            val hovered = rowBounds.contains(mouseX.toDouble(), mouseY.toDouble())
            indicatorAnimations[i].target = if (hovered || draggingChannel === channels[i]) 1.0f else 0.0f
        }
        openAnimation.target = 1.0f

        val visibleColor = getVisibleColor()
        // 颜色、输入框焦点/缓冲、鼠标与主题决定绘制结果，任一变化都重建整层。

        renderBatch.LuminUIScene(popupBounds, key = this) {
            Interaction(key = "surface") {
                onClick {
                    focusedChannel = null
                    inputBuffer = null
                    true
                }
            }
            val progress = openAnimation.value
            PopupCard(
                size,
                MD3Theme.CARD_RADIUS.toFloat(),
                MD3Theme.POPUP_SHADOW_BLUR,
                MD3Theme.withAlpha(MD3Theme.SHADOW, (MD3Theme.POPUP_SHADOW_ALPHA * progress).toInt()),
                MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255)
            )
            val localAnchorBounds = anchorBounds.relativeTo(popupBounds)
            Positioned(localAnchorBounds.x, localAnchorBounds.y, localAnchorBounds.width, localAnchorBounds.height) {
                RoundRect(
                    0.0f, 0.0f, anchorBounds.width, anchorBounds.height,
                    MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.withAlpha(MD3Theme.SECONDARY_CONTAINER, 255)
                )
            }

            val previewBounds = getPreviewBounds(popupY)
            val swatchBounds = getPreviewSwatchBounds(popupY)
            val localPreviewBounds = previewBounds.relativeTo(popupBounds)
            val localSwatchBounds = swatchBounds.relativeTo(popupBounds)
            val previewSurface =
                if (MD3Theme.isLightTheme()) MD3Theme.SURFACE_CONTAINER_HIGH else MD3Theme.SURFACE_CONTAINER
            val swatchSurface =
                if (MD3Theme.isLightTheme()) MD3Theme.SURFACE_CONTAINER else MD3Theme.SURFACE_CONTAINER_HIGHEST
            RoundRect(
                localPreviewBounds.x, localPreviewBounds.y, localPreviewBounds.width, localPreviewBounds.height,
                10.0f, MD3Theme.withAlpha(previewSurface, 255)
            )
            RoundRect(
                localSwatchBounds.x, localSwatchBounds.y, localSwatchBounds.width, localSwatchBounds.height,
                8.0f, MD3Theme.withAlpha(swatchSurface, 255)
            )
            if (setting.isAllowAlpha()) {
                buildCheckerboard(localSwatchBounds, 255)
            }
            RoundRect(
                localSwatchBounds.x, localSwatchBounds.y, localSwatchBounds.width, localSwatchBounds.height,
                8.0f, getVisibleColor()
            )
            RoundRect(
                localSwatchBounds.x, localSwatchBounds.y, localSwatchBounds.width, localSwatchBounds.height,
                8.0f, MD3Theme.withAlpha(MD3Theme.OUTLINE_SOFT, (72 * progress).toInt())
            )
            Text(
                formatHex(getVisibleColor()), localPreviewBounds.x + 40.0f, localPreviewBounds.y + 8.0f,
                0.64f, MD3Theme.TEXT_PRIMARY
            )
            Text(
                if (setting.isAllowAlpha()) "RGBA" else "RGB",
                localPreviewBounds.x + 40.0f, localPreviewBounds.y + 19.0f, 0.52f, MD3Theme.TEXT_SECONDARY
            )

            val rowChannels = getChannels()
            for (i in rowChannels.indices) {
                val channel = rowChannels[i]
                val rowBounds = getChannelRowBounds(popupY, i)
                val localRowBounds = rowBounds.relativeTo(popupBounds)
                val trackBounds = getTrackBounds(rowBounds)
                val localTrackBounds = trackBounds.relativeTo(popupBounds)
                val valueBounds = getValueBounds(rowBounds)
                val localValueBounds = valueBounds.relativeTo(popupBounds)
                val hovered = rowBounds.contains(mouseX.toDouble(), mouseY.toDouble())
                val indicatorProgress = indicatorAnimations[i].value
                // 逐项缓存：颜色、焦点、输入缓冲、悬停与指示器动画决定该行节点是否重建。

                LuminUIItem(setting to channel) {
                    Interaction(key = "value", bounds = localValueBounds) {
                        onClick {
                            focusedChannel = channel
                            inputBuffer = getChannelValue(channel).toString()
                            cursorIndex = getCursorIndex(it.x, valueBounds, inputBuffer!!)
                            true
                        }
                        onFocusChanged { focused ->
                            if (!focused && focusedChannel === channel) {
                                focusedChannel = null
                                inputBuffer = null
                            }
                        }
                        onKeyPressed(::handleKeyPressed)
                        onTextInput(::handleTextInput)
                    }
                    Interaction(
                        key = "track",
                        bounds = UiRect(localTrackBounds.x, localTrackBounds.y - 5f,
                            localTrackBounds.width, localTrackBounds.height + 10f),
                    ) {
                        onDrag(
                            onStart = {
                                draggingChannel = channel
                                focusedChannel = null
                                inputBuffer = null
                                updateChannelFromMouse(channel, it.x, trackBounds)
                                true
                            },
                            onEnd = {
                                updateChannelFromMouse(channel, it.x, trackBounds)
                                commitPendingColor()
                                draggingChannel = null
                            },
                            onCancel = {
                                pendingValue = null
                                draggingChannel = null
                            },
                        ) { updateChannelFromMouse(channel, it.x, trackBounds) }
                    }
                    val channelProgress = getChannelValue(channel) / 255.0f
                    val handleX = localTrackBounds.x + localTrackBounds.width * channelProgress - 2.0f
                    val rowSurface: Color = if (hovered) {
                        if (MD3Theme.isLightTheme()) MD3Theme.SURFACE_CONTAINER_HIGHEST
                        else MD3Theme.SURFACE_CONTAINER_HIGH
                    } else {
                        MD3Theme.SURFACE_CONTAINER
                    }

                    Text(
                        channel.shortLabel, localRowBounds.x + 8.0f, localRowBounds.y + 6.5f, 0.58f,
                        if (MD3Theme.isLightTheme()) MD3Theme.TEXT_MUTED else MD3Theme.TEXT_SECONDARY
                    )
                    RoundRect(
                        localRowBounds.x, localRowBounds.y, localRowBounds.width, localRowBounds.height,
                        8.0f, MD3Theme.withAlpha(rowSurface, 255)
                    )
                    Slider(
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
                    buildValueBox(localValueBounds, valueText, focusedChannel === channel, 255)
                    if (indicatorProgress > 0.01f) {
                        buildValueIndicator(
                            handleX + 2.0f, localRowBounds.y - 4.0f,
                            getChannelValue(channel).toString(), indicatorProgress
                        )
                    }
                }
            }
        }
    }


    private fun handleKeyPressed(event: KeyEvent): Boolean {
        val focused = focusedChannel ?: return false
        return when (event.key()) {
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                commitInput()
                focusedChannel = null
                inputBuffer = null
                inputView.input.clearFocus()
                true
            }

            InputConstants.KEY_ESCAPE -> {
                focusedChannel = null
                inputBuffer = null
                inputView.input.clearFocus()
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
                cursorIndex = maxOf(0, cursorIndex - 1)
                true
            }

            InputConstants.KEY_RIGHT -> {
                cursorIndex = minOf(getDisplayBuffer().length, cursorIndex + 1)
                true
            }

            else -> false
        }
    }

    private fun handleTextInput(event: CharacterEvent): Boolean {
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
        progress = progress.coerceIn(0.0, 1.0)
        val value = (progress * 255.0).roundToInt().coerceIn(0, 255)
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

    private fun UiScope.buildValueIndicator(
        anchorX: Float, rowTop: Float, valueText: String, progress: Float,
    ) {
        val textScale = 0.54f
        val bubbleWidth = metrics.textWidth(valueText, textScale) + 12.0f
        val bubbleHeight = 16.0f
        val bubbleX = anchorX - bubbleWidth / 2.0f
        val bubbleY = rowTop - 14.0f - (1.0f - progress) * 4.0f
        val bubbleAlpha = (255 * progress).toInt()
        RoundRect(
            bubbleX, bubbleY, bubbleWidth, bubbleHeight, 8.0f,
            MD3Theme.withAlpha(MD3Theme.INVERSE_SURFACE, bubbleAlpha)
        )
        val textY = bubbleY + (bubbleHeight - metrics.textHeight(textScale)) / 2.0f
        Text(
            valueText, bubbleX + (bubbleWidth - metrics.textWidth(valueText, textScale)) / 2.0f, textY,
            textScale, MD3Theme.withAlpha(MD3Theme.INVERSE_ON_SURFACE, bubbleAlpha)
        )
    }

    private fun UiScope.buildValueBox(bounds: UiRect, valueText: String, focused: Boolean, alpha: Int) {
        val boxBase = if (MD3Theme.isLightTheme()) MD3Theme.SURFACE_CONTAINER else MD3Theme.SURFACE_CONTAINER_HIGH
        val boxHover = MD3Theme.SURFACE_CONTAINER_HIGHEST
        val boxColor: Color =
            if (focused) MD3Theme.INVERSE_SURFACE else MD3Theme.withAlpha(
                MD3Theme.lerp(boxBase, boxHover, 0.85f),
                alpha
            )
        val textColor: Color =
            if (focused) MD3Theme.INVERSE_ON_SURFACE else MD3Theme.withAlpha(MD3Theme.TEXT_PRIMARY, alpha)
        RoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 6.0f, boxColor)
        val textScale = 0.52f
        val textWidth = metrics.textWidth(valueText, textScale)
        val textHeight = metrics.textHeight(textScale)
        val textX = bounds.x + (bounds.width - textWidth) / 2.0f
        val textY = bounds.y + (bounds.height - textHeight) / 2.0f
        Text(valueText, textX, textY, textScale, textColor)
        if (focused) {
            val caretX = textX + metrics.textWidth(
                valueText.substring(0, minOf(cursorIndex, valueText.length)), textScale
            )
            Rect(caretX, bounds.y + 3.0f, 1.0f, bounds.height - 6.0f, MD3Theme.INVERSE_ON_SURFACE)
            com.github.epsilon.gui.panel.utils.IMEFocusHelper.updateCursorPos(origin.x + caretX, origin.y + textY)
        }
    }

    private fun UiScope.buildCheckerboard(bounds: UiRect, alpha: Int) {
        val inset = 2.0f
        val cell = 4.0f
        val dark = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER, alpha)
        val light = MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_HIGH, alpha)
        var y = bounds.y + inset
        while (y < bounds.bottom() - inset) {
            var x = bounds.x + inset
            while (x < bounds.right() - inset) {
                val ix = floor(((x - bounds.x) / cell).toDouble()).toInt()
                val iy = floor(((y - bounds.y) / cell).toDouble()).toInt()
                val color = if ((ix + iy) and 1 == 0) light else dark
                val width = minOf(cell, bounds.right() - inset - x)
                val height = minOf(cell, bounds.bottom() - inset - y)
                RoundRect(x, y, width, height, 0.0f, color)
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
            value = buffer.toInt().coerceIn(0, 255)
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
        pendingValue = color
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
        inputView.close()
        textRenderer.close()
    }

    }
