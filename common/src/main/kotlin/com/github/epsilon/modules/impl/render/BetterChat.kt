package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.schedulers.render2d.Render2DScheduler
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.interfaces.ChatComponentAccessor
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.google.common.base.Suppliers
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.screens.ChatScreen
import java.awt.Color
import java.util.function.Supplier
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

object BetterChat : Module("Better Chat", Category.RENDER) {

    private val backgroundColor by colorSetting("Background Color", Color(16, 17, 20, 150))
    private val inputBackgroundColor by colorSetting("Input Background Color", Color(16, 17, 20, 185))
    private var chatRadius by doubleSetting("Chat Radius", 4.5, 0.0..12.0, 0.5)
    private var inputRadius by doubleSetting("Input Radius", 3.5, 0.0..12.0, 0.5)
    private val blurStrength by doubleSetting("Blur Strength", 8.0, 1.0..20.0, 0.5)
    private val dropShadow by boolSetting("Drop Shadow", true)
    private val shadowBlur by doubleSetting("Shadow Blur", 10.0, 2.0..24.0, 1.0, { dropShadow })
    private val shadowColor by colorSetting("Shadow Color", Color(255, 255, 255, 110), true, { dropShadow })

    private const val CHAT_BOTTOM_OFFSET = 40.0f
    private const val CHAT_HORIZONTAL_PADDING = 4.0f
    private const val CHAT_VERTICAL_PADDING = 3.0f
    private const val INPUT_X = 2.0f
    private const val INPUT_BOTTOM_OFFSET = 2.0f
    private const val INPUT_HEIGHT = 12.0f
    private const val CHAT_SLIDE_DISTANCE = 3.0f
    private const val INPUT_SLIDE_DISTANCE = 2.0f
    private const val CHAT_VISIBILITY_DURATION_MS = 180L
    private const val CHAT_RESIZE_DURATION_MS = 210L
    private const val INPUT_VISIBILITY_DURATION_MS = 150L

    private val chatVisibilityAnimation = Animation(Easing.EASE_OUT_CUBIC, CHAT_VISIBILITY_DURATION_MS)
    private val chatHeightAnimation = Animation(Easing.EASE_OUT_CUBIC, CHAT_RESIZE_DURATION_MS)
    private val inputVisibilityAnimation = Animation(Easing.EASE_OUT_CUBIC, INPUT_VISIBILITY_DURATION_MS)

    private val scheduler: Supplier<Render2DScheduler> = Suppliers.memoize { Render2DScheduler() }

    private var lastChatBottom = 0.0f
    private var lastPanelOpacity = 1.0f
    private var chatTextScissor: ScreenRectangle? = null

    init {
        onEnable {
            resetAnimations()
        }

        onDisable {
            resetAnimations()
        }

        setDefaultEnabled(true)
    }

    init {
        handler<Render2DEvent.HUD> { onRender2D(it) }
    }

    private fun onRender2D(event: Render2DEvent.HUD) {
        val focused = mc.gui.screen() is ChatScreen
        val screenWidth = event.guiGraphics.guiWidth().toFloat()
        val screenHeight = event.guiGraphics.guiHeight().toFloat()
        if (screenWidth <= 4.0f || screenHeight <= CHAT_BOTTOM_OFFSET) {
            chatTextScissor = null
            return
        }

        val chatScale = mc.options.chatScale().get().toFloat()
        val contentWidth = ChatComponent.getWidth(mc.options.chatWidth().get())
        val chatBottom = screenHeight - CHAT_BOTTOM_OFFSET
        val visibility = (mc.gui.hud.chat as ChatComponentAccessor)
            .`epsilon$getVisibility`(mc.gui.hud.guiTicks, focused)
        val hasQueue = mc.gui.chatListener().queueSize() > 0L
        val drawChatPanel = visibility.lineCount > 0 || hasQueue

        val lineHeight = (9.0 * (mc.options.chatLineSpacing().get() + 1.0)).toInt() * chatScale
        val contentHeight = visibility.heightFactor * lineHeight
        val contentBelowChat = if (hasQueue) 9.0f * chatScale else 0.0f
        val panelOpacity = if (hasQueue) 1.0f else visibility.opacity
        val verticalPadding = CHAT_VERTICAL_PADDING * (if (focused || hasQueue) 1.0f else panelOpacity)

        val chatX = INPUT_X
        val chatWidth = minOf(screenWidth - INPUT_X * 2.0f, contentWidth + CHAT_HORIZONTAL_PADDING * 2.0f * chatScale)
        val targetChatY = maxOf(0.0f, chatBottom - contentHeight - verticalPadding)
        val targetChatHeight =
            minOf(screenHeight - targetChatY, contentHeight + contentBelowChat + verticalPadding * 2.0f)
        if (drawChatPanel) {
            lastChatBottom = targetChatY + targetChatHeight
            lastPanelOpacity = panelOpacity
        } else if (lastChatBottom == 0.0f) {
            lastChatBottom = chatBottom + CHAT_VERTICAL_PADDING
        }

        chatVisibilityAnimation.run(if (drawChatPanel) 1.0f else 0.0f)
        chatHeightAnimation.run(if (drawChatPanel) targetChatHeight else 0.0f)
        inputVisibilityAnimation.run(if (focused) 1.0f else 0.0f)

        val chatProgress = chatVisibilityAnimation.value
        val inputProgress = inputVisibilityAnimation.value
        val animatedChatHeight = chatHeightAnimation.value
        val renderChatPanel = chatProgress > 0.01f && animatedChatHeight > 0.01f
        val renderInputPanel = inputProgress > 0.01f
        if (!renderChatPanel && !renderInputPanel) {
            chatTextScissor = null
            return
        }

        val chatY = lastChatBottom - animatedChatHeight + (1.0f - chatProgress) * CHAT_SLIDE_DISTANCE
        val animatedPanelOpacity = chatProgress * (if (drawChatPanel) panelOpacity else lastPanelOpacity)
        updateChatTextScissor(
            renderChatPanel, chatX, chatY, chatWidth, animatedChatHeight, screenWidth, screenHeight
        )

        val inputY = screenHeight - INPUT_BOTTOM_OFFSET - INPUT_HEIGHT + (1.0f - inputProgress) * INPUT_SLIDE_DISTANCE
        val inputWidth = screenWidth - INPUT_X * 2.0f

        val chatRadius = minOf(this.chatRadius.toFloat(), animatedChatHeight * 0.5f)
        val inputRadius = this.inputRadius.toFloat()
        val blur = blurStrength.toFloat()
        val coordinateScale = (mc.window.guiScale / LuminRenderSystem.getGuiScale()).toFloat()
        val renderChatX = chatX * coordinateScale
        val renderChatY = chatY * coordinateScale
        val renderChatWidth = chatWidth * coordinateScale
        val renderChatHeight = animatedChatHeight * coordinateScale
        val renderChatRadius = chatRadius * coordinateScale
        val renderInputX = INPUT_X * coordinateScale
        val renderInputY = inputY * coordinateScale
        val renderInputWidth = inputWidth * coordinateScale
        val renderInputHeight = INPUT_HEIGHT * coordinateScale
        val renderInputRadius = inputRadius * coordinateScale

        // 模糊必须先读取尚未叠加聊天面板的主渲染目标。
        if (renderChatPanel) {
            BlurShader.render(
                renderChatX, renderChatY, renderChatWidth, renderChatHeight,
                renderChatRadius, blur * animatedPanelOpacity
            )
        }
        if (renderInputPanel) {
            BlurShader.render(
                renderInputX, renderInputY, renderInputWidth, renderInputHeight,
                renderInputRadius, blur * inputProgress
            )
        }

        val renderScheduler = scheduler.get()
        renderScheduler.clear()
        val layer = renderScheduler.layer(0)

        if (dropShadow) {
            val shadowRadius = shadowBlur.toFloat() * coordinateScale
            if (renderChatPanel) {
                layer.addShadow(
                    renderChatX, renderChatY, renderChatWidth, renderChatHeight,
                    renderChatRadius, shadowRadius, withOpacity(shadowColor, animatedPanelOpacity)
                )
            }
            if (renderInputPanel) {
                layer.addShadow(
                    renderInputX, renderInputY, renderInputWidth, renderInputHeight,
                    renderInputRadius, shadowRadius, withOpacity(shadowColor, inputProgress)
                )
            }
        }

        if (renderChatPanel) {
            layer.addRoundRect(
                renderChatX, renderChatY, renderChatWidth, renderChatHeight,
                renderChatRadius, withOpacity(backgroundColor, animatedPanelOpacity)
            )
        }
        if (renderInputPanel) {
            layer.addRoundRect(
                renderInputX, renderInputY, renderInputWidth, renderInputHeight,
                renderInputRadius, withOpacity(inputBackgroundColor, inputProgress)
            )
        }
        renderScheduler.flushAndClear()
    }

    private fun updateChatTextScissor(
        visible: Boolean, x: Float, y: Float, width: Float, height: Float,
        screenWidth: Float, screenHeight: Float,
    ) {
        if (!visible) {
            chatTextScissor = null
            return
        }

        val left = maxOf(0, floor(x.toDouble()).toInt())
        val top = maxOf(0, floor(y.toDouble()).toInt())
        val right = minOf(screenWidth.toInt(), ceil((x + width).toDouble()).toInt())
        val bottom = minOf(screenHeight.toInt(), ceil((y + height).toDouble()).toInt())
        chatTextScissor =
            if (right > left && bottom > top) ScreenRectangle(left, top, right - left, bottom - top) else null
    }

    fun getChatContentAlpha(): Float = if (isEnabled) chatVisibilityAnimation.value else 1.0f

    fun getChatTextScissor(): ScreenRectangle? = if (isEnabled) chatTextScissor else null

    private fun resetAnimations() {
        resetAnimation(chatVisibilityAnimation)
        resetAnimation(chatHeightAnimation)
        resetAnimation(inputVisibilityAnimation)
        lastChatBottom = 0.0f
        lastPanelOpacity = 1.0f
        chatTextScissor = null
    }

    private fun resetAnimation(animation: Animation) {
        animation.startValue = 0.0f
        animation.reset()
    }

    private fun withOpacity(color: Color, opacity: Float): Color =
        Color(color.red, color.green, color.blue, (color.alpha * opacity).roundToInt())

}
