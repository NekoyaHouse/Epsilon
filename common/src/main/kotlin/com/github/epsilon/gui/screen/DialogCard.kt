package com.github.epsilon.gui.screen

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.*
import com.github.epsilon.gui.lib.scene.UiLayer
import com.github.epsilon.gui.lib.scene.UiScene
import com.github.epsilon.gui.theme.DialogZ
import com.github.epsilon.gui.theme.EpsilonUiTheme
import com.github.epsilon.gui.theme.MD3Theme
import com.github.epsilon.kernel.MinecraftWrapper.mc
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.awt.Color

/**
 * Epsilon 模态对话框共用的卡片渲染和输入处理。
 *
 * 每次打开时创建独立的内容。若父界面也是对话框，关闭子对话框时会恢复父对话框的内容。
 */
internal object DialogCard {

    const val CARD_PADDING = 14.0f
    const val BODY_SCALE = 0.62f
    const val BUTTON_HEIGHT = 26.0f

    private const val TITLE_SCALE = 0.95f
    private const val BUTTON_GAP = 6.0f
    private const val BUTTON_MIN_WIDTH = 78.0f
    private val SCRIM = Color(0, 0, 0, 150)

    /**
     * 对话框按钮描述。
     *
     * @param label   按钮文案
     * @param primary 是否使用主色（否则使用次级容器色）
     * @param action  点击回调
     */
    data class DialogButton(val label: String, val primary: Boolean, val action: Runnable)

    abstract class Content(val screenTitle: Component? = null) {
        abstract fun titleText(): String
        abstract fun cardWidth(): Float
        abstract fun cardHeight(): Float

        /** 绘制卡片正文，坐标以卡片左上角为原点。 */
        abstract fun buildBody(scope: UiTree.Scope, contentTop: Float)

        /** 当前帧的按钮列表，按从左到右的顺序返回。 */
        abstract fun buttons(): List<DialogButton>

        /** 用户主动关闭对话框时调用。 */
        open fun onClose() {}
    }

    private data class Session(
        val screen: Screen,
        val content: Content,
        val parent: Screen?,
        val previous: Session?,
    )

    private data class ButtonHit(val bounds: UiRect, val button: DialogButton)

    private val scene by lazy { UiScene(EpsilonUiTheme) }

    private var renderTarget: LuminRenderSystem.LuminRenderTarget? = null
    private var cardArea: UiRect = UiRect(0.0f, 0.0f, 0.0f, 0.0f)
    private var current: Session? = null
    private var pending: Session? = null

    fun show(screen: Screen, parent: Screen?, content: Content) {
        val previous = current?.takeIf { it.screen === parent }
        if (screen === parent || screen === mc.gui.screen() ||
            generateSequence(previous) { it.previous }.any { it.screen === screen }
        ) return
        switchTo(screen, Session(screen, content, parent, previous))
    }

    private fun switchTo(screen: Screen?, session: Session?) {
        val previousPending = pending
        pending = session
        try {
            mc.gui.setScreen(screen)
        } finally {
            pending = previousPending
        }
    }

    // Gui.setScreen 可能被取消，等 added() 确认切换后再采用待显示的内容。
    fun added(screen: Screen) {
        current = pending?.takeIf { it.screen === screen }
    }

    fun isShowing(screen: Screen): Boolean = current?.screen === screen

    fun title(screen: Screen): Component? = current?.takeIf { it.screen === screen }?.content?.screenTitle

    fun close(screen: Screen, action: Runnable? = null) {
        val session = current?.takeIf { it.screen === screen } ?: return
        action?.run()
        if (current !== session) return
        session.content.onClose()
        switchTo(session.parent, session.previous)
    }

    fun extractRenderState(screen: Screen, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val content = current?.takeIf { it.screen === screen }?.content ?: return
        val window = mc.window
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

        val cardW = minOf(content.cardWidth(), width - 20.0f)
        val cardH = minOf(content.cardHeight(), height - 20.0f)
        cardArea = UiRect((width - cardW) * 0.5f, (height - cardH) * 0.5f, cardW, cardH)
        val hits = layoutButtons(content.buttons())

        scene.beginFrame()
        val tree =
            scene.LuminUITree(UiRect(0f, 0f, width.toFloat(), height.toFloat()), "dialog", immediate = true) { scope ->
                scope.rect(0.0f, 0.0f, width.toFloat(), height.toFloat(), SCRIM)
                scope.layer(DialogZ.Card) { card ->
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
                scope.pushAbsolute(cardArea) { body ->
                    body.text(content.titleText(), CARD_PADDING, CARD_PADDING, TITLE_SCALE, MD3Theme.TEXT_PRIMARY)
                    content.buildBody(body, CARD_PADDING + 22.0f)
                }
                scope.layer(DialogZ.Content) { content ->
                    for ((index, hit) in hits.withIndex()) {
                        content.asUiScope(textMetrics()).Interaction(index, hit.bounds) {
                            onClick { hit.button.action.run(); true }
                        }
                        val hovered = hit.bounds.contains(epsilonMouseX.toDouble(), epsilonMouseY.toDouble())
                        val background = if (hit.button.primary) {
                            MD3Theme.PRIMARY_CONTAINER
                        } else {
                            if (hovered) MD3Theme.SURFACE_CONTAINER_HIGHEST else MD3Theme.SURFACE_CONTAINER_HIGH
                        }
                        val foreground =
                            if (hit.button.primary) MD3Theme.ON_PRIMARY_CONTAINER else MD3Theme.TEXT_PRIMARY
                        content.button(
                            hit.bounds,
                            MD3Theme.CONTROL_RADIUS,
                            background,
                            hit.button.label,
                            0.62f,
                            foreground
                        )
                    }
                }
            }
        scene.submit(UiLayer.CONTENT, tree)
        scene.endFrame()

        LuminRenderSystem.setActiveTarget(null)
        graphics.blit(
            renderTarget!!.getIdentifier(), 0, 0, window.guiScaledWidth, window.guiScaledHeight, 0.0f, 1.0f, 1.0f, 0.0f
        )
    }

    fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        scene.input.mouseClicked(LuminRenderSystem.toEpsilonMouseEvent(event), doubleClick)
        return true
    }

    fun removed() {
        current = null
        scene.resetComposition()
        renderTarget?.close()
        renderTarget = null
    }

    fun cardBounds(): UiRect {
        return cardArea
    }

    /**
     * 供卡片正文测量文本使用的渲染器。
     */
    fun textMetrics(): TextRenderer {
        return scene.scheduler().textMetrics()
    }

    private fun layoutButtons(buttons: List<DialogButton>): List<ButtonHit> {
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
        val hits = ArrayList<ButtonHit>(buttons.size)
        for (i in buttons.indices) {
            val bounds = UiRect(x, y, widths[i], BUTTON_HEIGHT)
            hits.add(ButtonHit(bounds, buttons[i]))
            x += widths[i] + BUTTON_GAP
        }
        return hits
    }

}
