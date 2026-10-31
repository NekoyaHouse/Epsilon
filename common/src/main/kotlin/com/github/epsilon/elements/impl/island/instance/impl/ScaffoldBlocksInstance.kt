package com.github.epsilon.elements.impl.island.instance.impl

import com.github.epsilon.Constants
import com.github.epsilon.elements.impl.island.IslandPalette
import com.github.epsilon.elements.impl.island.instance.LandInstance
import com.github.epsilon.elements.impl.island.pattern.impl.CheckPattern
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.render.animation.Animation
import com.github.epsilon.utils.render.animation.Easing
import com.github.epsilon.utils.timer.TimerUtils
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import net.minecraft.client.color.block.BlockTintSource
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.util.Mth
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import java.awt.Color
import java.util.function.Supplier

class ScaffoldBlocksInstance(
    private val countSupplier: Supplier<Int>,
    private val iconSupplier: Supplier<ItemStack>,
    private val textRendererSupplier: Supplier<TextRenderer>,
    pattern: CheckPattern,
) : LandInstance(pattern, 4) {

    private val max: Int = maxOf(1, countSupplier.get())

    private val progress = Animation(Easing.DECELERATE, 100)
    private val countAnim = Animation(Easing.DECELERATE, 560)

    private val iconRefreshTimer = TimerUtils()
    private var iconSprite: TextureAtlasSprite? = null
    private var iconTint: Color = Color.WHITE

    private var lastUpdateNs: Long = System.nanoTime()
    private var spinAngleDeg: Float = 0f
    private var startCount: Int
    private var targetCount: Int

    init {
        val initial = maxOf(0, countSupplier.get())
        this.startCount = initial
        this.targetCount = initial
        this.countAnim.startValue = initial.toFloat()
    }

    override fun update() {
        targetRadius = 1.0f
        targetWidth = PADDING * 2f + ICON_SIZE + GAP + RING_SIZE
        targetHeight = PADDING * 2f + maxOf(ICON_SIZE, RING_SIZE)

        val count = maxOf(0, countSupplier.get())
        progress.run(Mth.clamp(count / max.toFloat(), 0f, 1f))

        if (count != targetCount) {
            startCount = (countAnim.value).roundToInt()
            targetCount = count
        }
        countAnim.run(count.toFloat())
        if (startCount != targetCount && (countAnim.value).roundToInt() == targetCount) {
            startCount = targetCount
        }

        val nowNs = System.nanoTime()
        val deltaSeconds = Mth.clamp((nowNs - lastUpdateNs) / 1_000_000_000f, 0f, 0.05f)
        lastUpdateNs = nowNs
        spinAngleDeg = (spinAngleDeg + SPIN_DEGREES_PER_SECOND * deltaSeconds) % 360f

        if (iconSprite == null || iconRefreshTimer.every(350L)) {
            updateIconTexture(iconSupplier.get())
        }
    }

    override fun draw(scope: UiTree.Scope, translateX: Float, translateY: Float, width: Float, height: Float) {
        drawBlockTexture(scope, height)

        val ringRadius = (RING_SIZE - RING_STROKE) / 2f
        val centerX = width - PADDING - RING_SIZE / 2f
        val centerY = height / 2f

        scope.circle(centerX, centerY, ringRadius, RING_STROKE, fade(Color(255, 255, 255, 60)))

        val progressValue = Mth.clamp(progress.value, 0f, 1f)
        if (progressValue > 0f) {
            scope.gradientArc(
                centerX, centerY, ringRadius, RING_STROKE,
                -90f, progressValue * 359f, true, spinAngleDeg,
                fade(IslandPalette.ACCENT),
                fade(IslandPalette.ACCENT_ALT),
                fade(IslandPalette.SUCCESS)
            )
        }

        drawRollingCount(scope, centerX, centerY)
    }

    private fun updateIconTexture(stack: ItemStack) {
        iconSprite = null
        iconTint = Color.WHITE
        if (stack.isEmpty) return
        val blockItem = stack.item as? BlockItem ?: return

        val state: BlockState = blockItem.block.defaultBlockState()
        iconSprite = Constants.client.modelManager.blockStateModelSet.getParticleMaterial(state).sprite()

        val tintSource: BlockTintSource = Constants.client.blockColors.getTintSource(state, 0) ?: return
        val nn = NonNullContext.current ?: return

        val tint = context(nn) { tintSource.colorAsTerrainParticle(state, level, player.blockPosition()) }
        iconTint = Color(tint, true)
    }

    private fun drawBlockTexture(scope: UiTree.Scope, height: Float) {
        val sprite = iconSprite ?: return

        val iconY = (height - ICON_SIZE) / 2f
        scope.roundedTexture(
            sprite.atlasLocation(),
            PADDING, iconY, ICON_SIZE, ICON_SIZE, ICON_RADIUS,
            sprite.u0, sprite.v0, sprite.u1, sprite.v1,
            fade(iconTint)
        )
    }

    private fun drawRollingCount(scope: UiTree.Scope, centerX: Float, centerY: Float) {
        val textRenderer = textRendererSupplier.get()
        val textHeight = textRenderer.getHeight(TEXT_SCALE)
        val baseY = centerY - textHeight / 2f

        if (startCount == targetCount) {
            drawCentered(scope, maxOf(0, targetCount).toString(), centerX, baseY, fade(Color.WHITE))
            return
        }

        val direction = if (targetCount > startCount) 1 else -1
        val distance = abs(targetCount - startCount)
        if (distance <= 0) {
            drawCentered(scope, maxOf(0, targetCount).toString(), centerX, baseY, fade(Color.WHITE))
            return
        }

        val progressed = abs(countAnim.value - startCount)
        val step = minOf(floor(progressed.toDouble()).toInt(), distance)
        if (step >= distance) {
            drawCentered(scope, maxOf(0, targetCount).toString(), centerX, baseY, fade(Color.WHITE))
            return
        }

        val localProgress = Mth.clamp(progressed - step, 0f, 1f)
        val current = startCount + direction * Mth.clamp(distance - 1, 0, step)
        drawDigitRoll(scope, textRenderer, centerX, baseY, current, current + direction, direction, localProgress)
    }

    private fun drawDigitRoll(
        scope: UiTree.Scope,
        textRenderer: TextRenderer,
        centerX: Float,
        baseY: Float,
        current: Int,
        next: Int,
        direction: Int,
        localProgress: Float,
    ) {
        val currentText = maxOf(0, current).toString()
        val nextText = maxOf(0, next).toString()
        val length = maxOf(currentText.length, nextText.length)

        val cellWidths = FloatArray(length)
        var totalWidth = 0f
        val zeroWidth = textRenderer.getWidth("0", TEXT_SCALE)
        for (i in 0 until length) {
            val a = paddedChar(currentText, length, i)
            val b = paddedChar(nextText, length, i)
            val cell = maxOf(maxOf(charWidth(textRenderer, a), charWidth(textRenderer, b)), zeroWidth)
            cellWidths[i] = cell
            totalWidth += cell
        }

        val textHeight = textRenderer.getHeight(TEXT_SCALE)
        val eased = Easing.EASE_IN_OUT_CUBIC.function.apply(localProgress)
        val clip = UiRect(centerX - totalWidth / 2f - 2f, baseY - textHeight * 0.6f, totalWidth + 4f, textHeight * 2.2f)

        val finalTotalWidth = totalWidth
        scope.scissor(clip) { inner ->
            var cellX = centerX - finalTotalWidth / 2f
            for (i in 0 until length) {
                val cell = cellWidths[i]
                val cellCenterX = cellX + cell / 2f

                val currentChar = paddedChar(currentText, length, i)
                val nextChar = paddedChar(nextText, length, i)

                if (currentChar == nextChar) {
                    if (currentChar != ' ') {
                        drawCentered(inner, currentChar.toString(), cellCenterX, baseY, fade(Color.WHITE))
                    }
                } else {
                    val currentY = baseY + (if (direction > 0) -eased else eased) * DIGIT_ROLL_STEP
                    val nextY = baseY + (if (direction > 0) (1f - eased) else -(1f - eased)) * DIGIT_ROLL_STEP

                    if (currentChar != ' ') {
                        drawCentered(
                            inner, currentChar.toString(), cellCenterX, currentY,
                            fade(withAlpha(Color.WHITE, 1f - eased))
                        )
                    }
                    if (nextChar != ' ') {
                        drawCentered(
                            inner, nextChar.toString(), cellCenterX, nextY,
                            fade(withAlpha(Color.WHITE, eased))
                        )
                    }
                }

                cellX += cell
            }
        }
    }

    private fun drawCentered(scope: UiTree.Scope, text: String, centerX: Float, y: Float, color: Color) {
        val width = textRendererSupplier.get().getWidth(text, TEXT_SCALE)
        scope.text(text, centerX - width / 2f, y, TEXT_SCALE, color)
    }

    private fun charWidth(textRenderer: TextRenderer, c: Char): Float {
        if (c == ' ') return 0f
        return textRenderer.getWidth(c.toString(), TEXT_SCALE)
    }

    private companion object {

        private const val PADDING = 6f
        private const val ICON_SIZE = 26f
        private const val ICON_RADIUS = ICON_SIZE * 0.4f
        private const val GAP = 6f
        private const val RING_SIZE = 26f
        private const val RING_STROKE = 2.5f
        private const val TEXT_SCALE = 0.78f
        private const val DIGIT_ROLL_STEP = 9f
        private const val SPIN_DEGREES_PER_SECOND = 240f

        private fun paddedChar(text: String, length: Int, index: Int): Char {
            val pad = length - text.length
            val source = index - pad
            if (source < 0 || source >= text.length) return ' '
            return text[source]
        }

        private fun withAlpha(color: Color, alphaMul: Float): Color {
            val alpha = Mth.clamp((color.alpha * alphaMul).toInt(), 0, 255)
            return Color(color.red, color.green, color.blue, alpha)
        }

    }

}
