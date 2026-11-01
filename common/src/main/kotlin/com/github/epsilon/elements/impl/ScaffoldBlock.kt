package com.github.epsilon.elements.impl

import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.gui.hudeditor.HudEditorScreen
import com.github.epsilon.gui.lib.UiRect
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.modules.impl.movement.Scaffold
import com.github.epsilon.utils.render.animation.Easing
import com.google.common.base.Suppliers
import net.minecraft.client.DeltaTracker
import net.minecraft.util.Mth
import java.awt.Color
import java.util.function.Supplier
import kotlin.math.pow

object ScaffoldBlock : HudModule("Scaffold Block", 0f, 0f, 84f, 28f) {

    private val scale by doubleSetting("Scale", 0.7, 0.5..2.0, 0.1)
    private val cornerRadius by doubleSetting("Corner Radius", 5.0, 0.0..20.0, 0.5)
    private val backgroundColor by colorSetting("Background Color", Color(15, 15, 15, 50))
    private val textColor by colorSetting("Text Color", Color(248, 249, 252, 245))
    private val textSecondary by colorSetting("Text Secondary", Color(210, 214, 225, 170))
    private val drawShadow by boolSetting("Drop Shadow", true)
    private val shadowBlur by doubleSetting("Shadow Blur", 9.0, 2.0..32.0, 1.0, { drawShadow })
    private val shadowColor by colorSetting("Shadow Color", Color(255, 255, 255, 90), true, { drawShadow })
    private val backgroundBlur by boolSetting("Background Blur", true)
    private val blurStrength by intSetting("Blur Strength", 5, 1..16, 1)
    private val smoothNumber by boolSetting("Smooth Number", true)
    private val numberDelay by doubleSetting("Number Delay", 0.15, 0.0..0.5, 0.01, { smoothNumber })

    private var initialized: Boolean = false
    private var previousCountText: String = "0"
    private var targetCountText: String = "0"
    private var pendingCountText: String = "0"
    private var numberAnimProgress: Float = 1.0f
    private var delayTimer: Float = 0f
    private var visibilityProgress: Float = 0f
    private var lastVisibilityUpdateMs: Long = 0L

    private val textRendererSupplier: Supplier<TextRenderer> =
        Suppliers.memoize(com.google.common.base.Supplier { TextRenderer.create() })

    init {
        onEnable {
            resetAnimation()
        }

        onDisable {
            resetAnimation()
        }
    }

    override fun render(deltaTracker: DeltaTracker) {
        val blockCount = getDisplayBlockCount()
        val shouldShow = shouldShowHud(blockCount)
        updateVisibilityAnimation(shouldShow)

        val animation = createAnimationState()
        if (!shouldShow && animation.panelProgress <= 0.01f) {
            resetAnimation()
            return
        }

        if (shouldShow) {
            syncNumberAnimation(blockCount, deltaTracker)
        } else if (!initialized) {
            return
        }

        val textRenderer = textRendererSupplier.get()
        val scope: UiTree.Scope = renderScope()

        val layout = createLayout(textRenderer)
        drawBackground(scope, layout, animation)
        drawText(scope, textRenderer, layout, animation)
        setBounds(layout.totalWidth, layout.height)
    }

    private fun shouldShowHud(liveBlockCount: Int): Boolean {
        val preview = mc.gui.screen() is HudEditorScreen
        return preview || (Scaffold.isEnabled && liveBlockCount > 0)
    }

    private fun getDisplayBlockCount(): Int {
        val liveBlockCount = maxOf(0, Scaffold.getBlockCount())
        if (mc.gui.screen() is HudEditorScreen) {
            return maxOf(64, liveBlockCount)
        }
        return liveBlockCount
    }

    private fun createAnimationState(): AnimationState {
        val rawVisibility = visibilityProgress.coerceIn(0.0f, 1.0f)
        val easedVisibility = (rawVisibility.toDouble()).pow(3.0).toFloat()
        val panelProgress = Easing.EASE_OUT_SINE.function.apply(easedVisibility)
        val contentProgress =
            Easing.EASE_OUT_SINE.function.apply(((rawVisibility - 0.08f) / 0.92f).coerceIn(0.0f, 1.0f))
        val contentAlpha = contentProgress * contentProgress * (3.0f - 2.0f * contentProgress)
        return AnimationState(panelProgress, contentProgress, contentAlpha)
    }

    private fun createLayout(textRenderer: TextRenderer): Layout {
        val scaled = scale.toFloat()
        val height = BASE_HEIGHT * scaled
        val radius = cornerRadius.toFloat() * scaled
        val padX = BASE_PAD_X * scaled
        val labelScale = 0.62f * scaled
        val labelGap = BASE_LABEL_GAP * scaled
        val numberColumnWidth = getNumberColumnWidth(textRenderer, scaled)
        val labelWidth = textRenderer.getWidth(LABEL, labelScale)
        val totalWidth = padX * 2.0f + numberColumnWidth + labelGap + labelWidth
        return Layout(height, radius, padX, scaled, labelScale, labelGap, numberColumnWidth, labelWidth, totalWidth, x)
    }

    private fun drawBackground(scope: UiTree.Scope, layout: Layout, animation: AnimationState) {
        val animatedWidth = layout.totalWidth * animation.panelProgress
        val animatedX = Mth.lerp(animation.panelProgress, layout.centerX, layout.renderX)
        val animatedRadius = minOf(layout.radius, animatedWidth / 2.0f)

        if (backgroundBlur) {
            BlurShader.render(animatedX, y, animatedWidth, layout.height, animatedRadius, blurStrength.toFloat())
        }
        if (drawShadow) {
            scope.shadow(
                animatedX, y, animatedWidth, layout.height, animatedRadius,
                shadowBlur.toFloat(), withAlpha(shadowColor, animation.contentAlpha)
            )
        }

        scope.roundRect(
            animatedX, y, animatedWidth, layout.height, animatedRadius,
            withAlpha(backgroundColor, animation.contentAlpha)
        )
    }

    private fun drawText(scope: UiTree.Scope, textRenderer: TextRenderer, layout: Layout, animation: AnimationState) {
        val numberColumnX = layout.renderX + layout.padX
        val numberY = y + (layout.height - textRenderer.getHeight(layout.numberScale)) / 2.0f
        val animatedNumberColumnX = Mth.lerp(
            animation.contentProgress, layout.centerX - layout.numberColumnWidth / 2.0f, numberColumnX
        )
        val animatedWidth = layout.totalWidth * animation.panelProgress
        val animatedX = Mth.lerp(animation.panelProgress, layout.centerX, layout.renderX)

        val requiresScissor = animation.panelProgress < 1.0f || smoothNumber && numberAnimProgress < 1.0f
        scope.scissorIf(requiresScissor, UiRect(animatedX, y, animatedWidth, layout.height)) { clipped ->
            if (smoothNumber) {
                drawRollingNumber(
                    clipped, textRenderer, layout.numberScale, animatedNumberColumnX,
                    layout.numberColumnWidth, numberY, animation.contentAlpha
                )
            } else {
                clipped.text(
                    targetCountText, animatedNumberColumnX, numberY, layout.numberScale,
                    withAlpha(textColor, animation.contentAlpha)
                )
            }

            val labelX = numberColumnX + layout.numberColumnWidth + layout.labelGap
            val animatedLabelX = Mth.lerp(animation.contentProgress, layout.centerX - layout.labelWidth / 2.0f, labelX)
            val labelY = y + (layout.height - textRenderer.getHeight(layout.labelScale)) / 2.0f
            clipped.text(
                LABEL,
                animatedLabelX,
                labelY,
                layout.labelScale,
                withAlpha(textSecondary, animation.contentAlpha)
            )
        }
    }

    private fun syncNumberAnimation(blockCount: Int, deltaTracker: DeltaTracker) {
        val nextText = blockCount.toString()
        if (!initialized) {
            previousCountText = nextText
            targetCountText = nextText
            pendingCountText = nextText
            numberAnimProgress = 1.0f
            delayTimer = 0.0f
            initialized = true
            return
        }

        if (!smoothNumber) {
            previousCountText = nextText
            targetCountText = nextText
            pendingCountText = nextText
            numberAnimProgress = 1.0f
            delayTimer = 0.0f
            return
        }

        val frameTime = deltaTracker.gameTimeDeltaTicks / 20.0f
        if (nextText != targetCountText) {
            pendingCountText = nextText
            delayTimer += frameTime
            if (delayTimer >= numberDelay.toFloat()) {
                previousCountText = targetCountText
                targetCountText = pendingCountText
                numberAnimProgress = 0.0f
                delayTimer = 0.0f
            }
        } else {
            delayTimer = 0.0f
        }

        if (numberAnimProgress < 1.0f) {
            numberAnimProgress = minOf(1.0f, numberAnimProgress + frameTime * 3.0f)
            if (numberAnimProgress >= 1.0f) {
                previousCountText = targetCountText
            }
        }
    }

    private fun updateVisibilityAnimation(shouldShow: Boolean) {
        val now = System.currentTimeMillis()
        if (lastVisibilityUpdateMs == 0L) {
            lastVisibilityUpdateMs = now
        }

        val delta = ((now - lastVisibilityUpdateMs) / 220.0f).coerceIn(0.0f, 1.0f)
        lastVisibilityUpdateMs = now

        if (shouldShow) {
            visibilityProgress = minOf(1.0f, visibilityProgress + delta)
        } else {
            visibilityProgress = maxOf(0.0f, visibilityProgress - delta)
        }
    }

    private fun getNumberColumnWidth(textRenderer: TextRenderer, numberScale: Float): Float {
        val width = maxOf(
            textRenderer.getWidth(previousCountText, numberScale),
            textRenderer.getWidth(targetCountText, numberScale)
        )
        return width + 4.0f * scale.toFloat()
    }

    private fun drawRollingNumber(
        scope: UiTree.Scope,
        textRenderer: TextRenderer,
        numberScale: Float,
        columnX: Float,
        columnWidth: Float,
        textY: Float,
        alphaMul: Float,
    ) {
        val previous = previousCountText
        val target = targetCountText
        val maxLen = maxOf(previous.length, target.length)
        val contentWidth =
            maxOf(textRenderer.getWidth(previous, numberScale), textRenderer.getWidth(target, numberScale))
        var charX = columnX + maxOf(0.0f, (columnWidth - contentWidth) / 2.0f)
        val slideOffset = 4.0f * scale.toFloat()

        for (i in 0 until maxLen) {
            val previousChar = if (i < previous.length) previous[i] else '\u0000'
            val targetChar = if (i < target.length) target[i] else '\u0000'

            var slotWidth = 0.0f
            if (previousChar != '\u0000') {
                slotWidth = maxOf(slotWidth, textRenderer.getWidth(previousChar.toString(), numberScale))
            }
            if (targetChar != '\u0000') {
                slotWidth = maxOf(slotWidth, textRenderer.getWidth(targetChar.toString(), numberScale))
            }

            if (previousChar == targetChar) {
                if (targetChar != '\u0000') {
                    scope.text(
                        targetChar.toString(), charX, textY, numberScale,
                        withAlpha(textColor, alphaMul)
                    )
                }
            } else {
                val oldAlpha = 1.0f - numberAnimProgress
                val newAlpha = numberAnimProgress

                if (previousChar != '\u0000' && oldAlpha > 0.01f) {
                    scope.text(
                        previousChar.toString(), charX, textY - numberAnimProgress * slideOffset, numberScale,
                        withAlpha(textColor, oldAlpha * alphaMul)
                    )
                }
                if (targetChar != '\u0000' && newAlpha > 0.01f) {
                    scope.text(
                        targetChar.toString(), charX, textY + (1.0f - numberAnimProgress) * slideOffset, numberScale,
                        withAlpha(textColor, newAlpha * alphaMul)
                    )
                }
            }

            charX += slotWidth
        }
    }

    private fun resetAnimation() {
        initialized = false
        previousCountText = "0"
        targetCountText = "0"
        pendingCountText = "0"
        numberAnimProgress = 1.0f
        delayTimer = 0.0f
        visibilityProgress = 0.0f
        lastVisibilityUpdateMs = 0L
    }

    private data class AnimationState(
        val panelProgress: Float,
        val contentProgress: Float,
        val contentAlpha: Float,
    )

    private data class Layout(
        val height: Float,
        val radius: Float,
        val padX: Float,
        val numberScale: Float,
        val labelScale: Float,
        val labelGap: Float,
        val numberColumnWidth: Float,
        val labelWidth: Float,
        val totalWidth: Float,
        val renderX: Float,
    ) {
        val centerX: Float
            get() = renderX + totalWidth / 2.0f
    }


    private const val LABEL = "Blocks"
    private const val BASE_HEIGHT = 24.0f
    private const val BASE_PAD_X = 7.0f
    private const val BASE_LABEL_GAP = 2.6f

    private fun withAlpha(color: Color, alphaMul: Float): Color {
        val alpha = (color.alpha * alphaMul).toInt().coerceIn(0, 255)
        return Color(color.red, color.green, color.blue, alpha)
    }

}
