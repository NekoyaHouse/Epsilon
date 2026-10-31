package com.github.epsilon.gui.screen

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.awt.Color

/**
 * Epsilon 风格的模态卡片对话框基类。
 *
 * 统一负责离屏渲染目标、遮罩、卡片外壳、标题、按钮布局与点击路由，子类只需要描述卡片
 * 尺寸、正文内容和按钮列表即可。
 */
abstract class EpsilonDialogScreen protected constructor(
    protected val parent: Screen?,
    title: Component,
) : Screen(title) {

    /**
     * 对话框按钮描述。
     *
     * @param label   按钮文案
     * @param primary 是否使用主色（否则使用次级容器色）
     * @param action  点击回调
     */
    protected data class DialogButton(val label: String, val primary: Boolean, val action: Runnable)

    private data class ButtonHit(val bounds: UiRect, val button: DialogButton)

    private val scene = UiScene(EpsilonUiTheme)
    private val buttonHits: MutableList<ButtonHit> = ArrayList()

    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var cardArea: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)

    protected abstract fun titleText(): String

    protected abstract fun cardWidth(): Float

    protected abstract fun cardHeight(): Float

    /**
     * 绘制卡片正文；坐标以卡片左上角为原点。
     */
    protected abstract fun buildBody(scope: UiTree.Scope, contentTop: Float, mouseX: Int, mouseY: Int)

    /**
     * 当前帧的按钮列表，按从左到右的顺序返回。
     */
    protected abstract fun buttons(): List<DialogButton>

    /**
     * 对话框被关闭（ESC、关闭按钮或父界面切换）时回调。
     */
    protected open fun onDialogClosed() {
    }

    override fun isPauseScreen(): Boolean {
        return false
    }

    override fun onClose() {
        onDialogClosed()
        minecraft.gui?.setScreen(parent)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val minecraft = this.minecraft
        val window = minecraft.window
        if (renderTarget == null) {
            renderTarget = LuminRenderSystem.LuminRenderTarget.create("epsilon-dialog", window.width, window.height)
        }
        renderTarget!!.resize(window.width, window.height)
        renderTarget!!.clear()
        LuminRenderSystem.setActiveTarget(renderTarget)

        val width = LuminRenderSystem.getScaledWidthInt()
        val height = LuminRenderSystem.getScaledHeightInt()
        val epsilonMouseX = LuminRenderSystem.toEpsilonMouseX(mouseX)
        val epsilonMouseY = LuminRenderSystem.toEpsilonMouseY(mouseY)

        val cardW = minOf(cardWidth(), width - 20.0f)
        val cardH = minOf(cardHeight(), height - 20.0f)
        cardArea = UiRect((width - cardW) * 0.5f, (height - cardH) * 0.5f, cardW, cardH)
        val hits = layoutButtons()

        scene.beginFrame()
        val tree = UiTree.build { scope ->
            scope.rect(0.0f, 0.0f, width.toFloat(), height.toFloat(), SCRIM)
            scope.layer(0) { card ->
                card.shadow(
                    cardArea.x, cardArea.y, cardArea.width, cardArea.height,
                    MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.POPUP_SHADOW_BLUR,
                    MD3Theme.withAlpha(MD3Theme.SHADOW, MD3Theme.POPUP_SHADOW_ALPHA)
                )
                card.roundRect(
                    cardArea.x, cardArea.y, cardArea.width, cardArea.height,
                    MD3Theme.CARD_RADIUS.toFloat(), MD3Theme.SURFACE_CONTAINER_LOW
                )
            }
            scope.pushAbsolute(cardArea) { content ->
                content.text(titleText(), CARD_PADDING, CARD_PADDING, TITLE_SCALE, MD3Theme.TEXT_PRIMARY)
                buildBody(content, CARD_PADDING + 22.0f, epsilonMouseX, epsilonMouseY)
            }
            scope.layer(10) { content ->
                for (hit in hits) {
                    val hovered = hit.bounds.contains(epsilonMouseX.toDouble(), epsilonMouseY.toDouble())
                    val background = if (hit.button.primary) {
                        MD3Theme.PRIMARY_CONTAINER
                    } else {
                        if (hovered) MD3Theme.SURFACE_CONTAINER_HIGHEST else MD3Theme.SURFACE_CONTAINER_HIGH
                    }
                    val foreground = if (hit.button.primary) MD3Theme.ON_PRIMARY_CONTAINER else MD3Theme.TEXT_PRIMARY
                    content.button(
                        hit.bounds,
                        MD3Theme.CONTROL_RADIUS.toFloat(),
                        background,
                        hit.button.label,
                        0.62f,
                        foreground
                    )
                }
            }
        }
        scene.submit(UiLayer.CONTENT, tree)
        scene.flush()
        scene.clear()

        LuminRenderSystem.setActiveTarget(null)
        graphics.blit(
            renderTarget!!.getIdentifier(), 0, 0, window.guiScaledWidth, window.guiScaledHeight, 0.0f, 1.0f, 1.0f, 0.0f
        )
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event)
        if (epsilonEvent.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (hit in buttonHits) {
                if (hit.bounds.contains(epsilonEvent.x(), epsilonEvent.y())) {
                    hit.button.action.run()
                    return true
                }
            }
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        return true
    }

    override fun removed() {
        super.removed()
        if (renderTarget != null) {
            renderTarget!!.close()
            renderTarget = null
        }
    }

    protected fun cardBounds(): UiRect {
        return cardArea
    }

    /**
     * 供子类测量文本使用的渲染器。
     */
    protected fun textMetrics(): TextRenderer {
        return scene.scheduler().textMetrics()
    }

    private fun layoutButtons(): List<ButtonHit> {
        buttonHits.clear()
        val buttons = buttons()
        if (buttons.isEmpty() || cardArea.width <= 0.0f) {
            return emptyList()
        }

        val metrics = scene.scheduler().textMetrics()
        val widths = FloatArray(buttons.size)
        var totalWidth = BUTTON_GAP * (buttons.size - 1)
        for (i in buttons.indices) {
            val textWidth = metrics.getWidth(buttons[i].label, 0.62f)
            widths[i] = maxOf(BUTTON_MIN_WIDTH, textWidth + 26.0f)
            totalWidth += widths[i]
        }

        var x = cardArea.x + cardArea.width - CARD_PADDING - totalWidth
        val y = cardArea.y + cardArea.height - CARD_PADDING - BUTTON_HEIGHT
        for (i in buttons.indices) {
            val bounds = UiRect(x, y, widths[i], BUTTON_HEIGHT)
            buttonHits.add(ButtonHit(bounds, buttons[i]))
            x += widths[i] + BUTTON_GAP
        }
        return buttonHits.toList()
    }

    companion object {

        const val CARD_PADDING: Float = 14.0f
        const val TITLE_SCALE: Float = 0.95f
        const val BODY_SCALE: Float = 0.62f
        const val BUTTON_HEIGHT: Float = 26.0f
        const val BUTTON_GAP: Float = 6.0f
        const val BUTTON_MIN_WIDTH: Float = 78.0f

        private val SCRIM: Color = Color(0, 0, 0, 150)

    }

}
