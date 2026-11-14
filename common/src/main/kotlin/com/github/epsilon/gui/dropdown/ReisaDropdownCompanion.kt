package com.github.epsilon.gui.dropdown

import com.github.epsilon.gui.lib.Layer
import com.github.epsilon.gui.lib.Texture
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.scene.LayerOffset
import com.github.epsilon.gui.lib.state.LuminAnimatable
import com.github.epsilon.kernel.render.animation.Easing
import com.github.epsilon.managers.AssetManager
import com.github.epsilon.modules.impl.ClientSetting
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.util.Util
import java.awt.Color
import kotlin.math.sin

class ReisaDropdownCompanion {

    /** 入场缩放由帧循环推进；立绘整体绘制在立即模式中，因此这里也可以安全地读 [LuminAnimatable.value]。 */
    private val entranceAnim: LuminAnimatable = LuminAnimatable(0.0f, Easing.EASE_OUT_BACK, 520L)

    private var shownAction: Action = Action.IDLE
    private var activeAction: Action? = null
    private var actionStartedMs: Long = 0L
    private var actionEndsMs: Long = 0L
    private var nextBlinkMs: Long = 0L
    private var blinkEndsMs: Long = 0L
    private var reactionRevision: Long = 0L
    private var texturesPrewarmed: Boolean = false
    private var sessionId: Int = -1

    fun open(newSessionId: Int) {
        if (sessionId == newSessionId) {
            return
        }

        sessionId = newSessionId
        shownAction = Action.IDLE
        activeAction = null
        texturesPrewarmed = false
        entranceAnim.snapTo(if (ClientSetting.dropdownEntranceAnimation == ClientSetting.DropdownEntranceAnimation.Classic) 0.0f else 1.0f)
        entranceAnim.target = 1.0f
        entranceAnim.reset()

        val now = Util.getMillis()
        nextBlinkMs = now + 2_800L + (newSessionId * 347L).mod(1_300L)
        blinkEndsMs = 0L
        react(Action.PANEL_OPEN)
    }

    fun react(action: Action?) {
        if (action == null || !action.isTransient()) {
            return
        }

        val now = Util.getMillis()
        val current = activeAction
        if (current != null
            && now - actionStartedMs < 32L
            && action.priority() < current.priority()
        ) {
            return
        }

        activeAction = action
        actionStartedMs = now
        actionEndsMs = now + action.durationMs()
        reactionRevision++
    }

    fun getReactionRevision(): Long {
        return reactionRevision
    }

    fun draw(
        scope: UiScope, screenWidth: Float, screenHeight: Float,
        mouseX: Int, mouseY: Int, mouseBlockedByGui: Boolean,
    ) {
        val now = Util.getMillis()
        val layout = resolveLayout(screenWidth, screenHeight)
        if (shownAction.texture() == null) {
            return
        }
        val desiredAction = resolveAction(now, layout, mouseX, mouseY, mouseBlockedByGui)
        switchExpression(desiredAction)
        val texture = shownAction.texture() ?: return
        if (!texturesPrewarmed) {
            prewarmTextures(scope)
            texturesPrewarmed = true
        }

        entranceAnim.target = 1.0f

        val entrance = entranceAnim.value.coerceIn(0.0f, 1.0f)
        val pulse = resolveActionPulse(now)
        val bob = sin((now % 3_896L) / 620.0).toFloat() * 1.15f
        val motionX = resolveMotionX(now, pulse)
        val motionY = resolveMotionY(pulse)
        val actionScale = resolveActionScale(pulse)

        val drawWidth = layout.width * actionScale
        val drawHeight = layout.height * actionScale
        val drawX = layout.x - (drawWidth - layout.width) * 0.5f + (1.0f - entrance) * 34.0f + motionX
        val drawY = layout.y - (drawHeight - layout.height) + bob + motionY
        val alpha = entrance * 0.94f

        with(scope) {
            Layer(ReisaDropdownZ.Shadow) {
                Texture(
                    texture, drawX + 2.0f, drawY + 3.0f, drawWidth, drawHeight,
                    0.0f, 0.0f, 1.0f, 1.0f, withAlpha(Color.BLACK, alpha * 0.15f), true
                )
            }
            Layer(ReisaDropdownZ.Body) {
                Texture(
                    texture, drawX, drawY, drawWidth, drawHeight,
                    0.0f, 0.0f, 1.0f, 1.0f, withAlpha(Color.WHITE, alpha), true
                )
            }
        }
    }

    fun getLeftEdge(screenWidth: Float, screenHeight: Float): Float {
        return resolveLayout(screenWidth, screenHeight).x
    }

    private fun resolveAction(now: Long, layout: Layout, mouseX: Int, mouseY: Int, mouseBlockedByGui: Boolean): Action {
        val current = activeAction
        if (current != null && now >= actionEndsMs) {
            activeAction = null
        }
        val active = activeAction
        if (active != null) {
            return active
        }

        if (!mouseBlockedByGui && isHeadHovered(layout, mouseX, mouseY)) {
            return Action.HEAD_HOVER
        }

        if (now >= nextBlinkMs) {
            blinkEndsMs = now + 145L
            nextBlinkMs = now + 3_200L + (now).mod(1_500L)
        }
        return if (now < blinkEndsMs) Action.BLINK else Action.IDLE
    }

    private fun switchExpression(desiredAction: Action) {
        shownAction = desiredAction
    }

    private fun prewarmTextures(scope: UiScope) {
        val transparent = withAlpha(Color.WHITE, 0.0f)
        with(scope) {
            Layer(ReisaDropdownZ.Prewarm) {
                for (action in Action.values()) {
                    val texture = action.texture() ?: continue
                    Texture(
                        texture, -1.0f, -1.0f, 1.0f, 1.0f,
                        0.0f, 0.0f, 1.0f, 1.0f, transparent, true
                    )
                }
            }
        }
    }

    private fun resolveActionPulse(now: Long): Float {
        val active = activeAction ?: return 0.0f
        if (active.durationMs() <= 0L) {
            return 0.0f
        }
        val progress = ((now - actionStartedMs) / active.durationMs().toFloat()).coerceIn(0.0f, 1.0f)
        return Mth.sin((progress * Mth.PI).toDouble())
    }

    private fun resolveMotionX(now: Long, pulse: Float): Float {
        if (activeAction === Action.DRAG) {
            return sin((now % 226L) / 36.0).toFloat() * 1.8f
        }
        if (activeAction === Action.SECONDARY_CLICK || activeAction === Action.KEY_BIND) {
            return -2.2f * pulse
        }
        return 0.0f
    }

    private fun resolveMotionY(pulse: Float): Float {
        if (activeAction === Action.SCROLL_UP) {
            return -4.0f * pulse
        }
        if (activeAction === Action.SCROLL_DOWN) {
            return 4.0f * pulse
        }
        if (activeAction === Action.CANCEL || activeAction === Action.PANEL_CLOSE) {
            return 2.5f * pulse
        }
        return -1.5f * pulse
    }

    private fun resolveActionScale(pulse: Float): Float {
        if (activeAction === Action.BUTTON_ACTION
            || activeAction === Action.CONFIRM
            || activeAction === Action.TOGGLE_ON
        ) {
            return 1.0f + 0.018f * pulse
        }
        if (activeAction === Action.DRAG || activeAction === Action.CANCEL) {
            return 1.0f - 0.012f * pulse
        }
        return 1.0f
    }

    private fun isHeadHovered(layout: Layout, mouseX: Int, mouseY: Int): Boolean {
        val headX = layout.x + layout.width * 0.20f
        val headY = layout.y + layout.height * 0.035f
        val headWidth = layout.width * 0.48f
        val headHeight = layout.height * 0.27f
        return mouseX >= headX && mouseX <= headX + headWidth
                && mouseY >= headY && mouseY <= headY + headHeight
    }

    private fun resolveLayout(screenWidth: Float, screenHeight: Float): Layout {
        var imageHeight = (screenHeight * 0.60f).coerceIn(150.0f, 520.0f)
        val maxWidth = maxOf(90.0f, screenWidth * 0.24f)
        imageHeight = minOf(imageHeight, maxWidth / IMAGE_ASPECT_RATIO)
        imageHeight = minOf(imageHeight, screenHeight * 0.82f)
        val imageWidth = imageHeight * IMAGE_ASPECT_RATIO
        val imageX = screenWidth - imageWidth - maxOf(2.0f, DropdownTheme.PANEL_MARGIN_X * 0.45f)
        val imageY = screenHeight - imageHeight
        return Layout(imageX, imageY, imageWidth, imageHeight)
    }

    private fun withAlpha(color: Color, alpha: Float): Color {
        val value =
            ((255.0f * alpha.coerceIn(0.0f, 1.0f)).toInt().toLong()).coerceIn((0).toLong(), (255).toLong()).toInt()
        return Color(color.red, color.green, color.blue, value)
    }

    private data class Layout(val x: Float, val y: Float, val width: Float, val height: Float)

    enum class Action(
        private val textureSuffix: String,
        private val durationMs: Long,
        private val priority: Int,
    ) {
        PANEL_OPEN("00", 720L, 60),
        COLOR_PICK("01", 760L, 80),
        SECONDARY_CLICK("02", 440L, 30),
        PRIMARY_CLICK("03", 380L, 20),
        PANEL_CLOSE("04", 620L, 65),
        MODULE_HIDDEN("05", 720L, 75),
        CANCEL("06", 760L, 90),
        TOGGLE_ON("07", 660L, 80),
        TOGGLE_OFF("08", 660L, 80),
        IDLE("09", 0L, 0),
        SLIDER_ADJUST("10", 520L, 70),
        CONFIRM("11", 620L, 85),
        TYPING("12", 480L, 70),
        SCROLL_UP("13", 340L, 55),
        KEY_BIND("14", 1_600L, 90),
        BUTTON_ACTION("15", 720L, 90),
        DRAG("16", 360L, 80),
        SCROLL_DOWN("17", 340L, 55),
        HEAD_HOVER("18", 0L, 0),
        BLINK("99", 0L, 0);

        /**
         * 返回玲纱立绘纹理，资源尚未下载时返回 `null`。
         */
        fun texture(): Identifier? {
            return AssetManager.reisaTexture(textureSuffix)
        }

        fun durationMs(): Long {
            return durationMs
        }

        fun priority(): Int {
            return priority
        }

        fun isTransient(): Boolean {
            return durationMs > 0L
        }
    }

    companion object {
        private const val IMAGE_ASPECT_RATIO = 710.0f / 1280.0f
    }

}

/**
 * Reisa 侧边立绘的层叠语义。
 *
 * 负值表示位于 dropdown 内容之下。
 */
object ReisaDropdownZ {

    /** 透明贴图预热。 */
    val Prewarm: LayerOffset = LayerOffset(-3)

    /** 投影贴图。 */
    val Shadow: LayerOffset = LayerOffset(-2)

    /** 立绘本体。 */
    val Body: LayerOffset = LayerOffset(-1)

}