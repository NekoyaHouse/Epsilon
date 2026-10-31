package com.github.epsilon.elements.impl.island.instance.impl

import com.github.epsilon.elements.impl.island.IslandPalette
import com.github.epsilon.elements.impl.island.instance.LandInstance
import com.github.epsilon.elements.impl.island.pattern.LCPattern
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.TextRenderer
import com.github.epsilon.gui.lib.UiTree
import com.github.epsilon.managers.HealthManager
import com.github.epsilon.utils.safety.*
import com.github.epsilon.utils.timer.TimerUtils
import kotlin.math.abs
import kotlin.math.sign
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import java.awt.Color
import java.util.Locale
import java.util.function.Supplier

class TargetInstance(
    val target: LivingEntity,
    private val textRendererSupplier: Supplier<TextRenderer>,
    pattern: LCPattern,
) : LandInstance(pattern, 3) {

    private val timer = TimerUtils()

    private var healthWidth: Float = 0f
    private var healthInitialized: Boolean = false

    override fun update() {
        if (!checkPattern()) {
            timer.reset()
        }

        targetRadius = 0.5f

        val textRenderer = textRendererSupplier.get()
        val nameWidth = textRenderer.getWidth(target.name.string, NAME_SCALE)
        val desired = PADDING + HEAD_SIZE + HEAD_GAP + nameWidth + NAME_ICON_GAP + ICON_SIZE + ICON_RIGHT_PADDING
        val screenMaxWidth = LuminRenderSystem.getScaledWidthInt() - 50f
        targetWidth = Mth.clamp(desired, MIN_WIDTH, maxOf(MIN_WIDTH, screenMaxWidth))
        val maxHealth = maxOf(1.0f, target.maxHealth + maxOf(0.0f, target.absorptionAmount))
        val health = HealthManager.getHealth(target)
        val healthRowHeight = maxOf(BAR_HEIGHT, textRenderer.getHeight(HEALTH_SCALE))
        targetHeight = PADDING * 2f + HEAD_SIZE + HEALTH_ROW_GAP + healthRowHeight

        val healthTextWidth = textRenderer.getWidth(formatHealth(health), HEALTH_SCALE)
        val maxHealthTextWidth = textRenderer.getWidth(formatHealth(maxHealth), HEALTH_SCALE)
        val maxBarWidth = maxOf(1f, targetWidth - HEALTH_TEXT_INSET * 2f
                    - healthTextWidth - maxHealthTextWidth - HEALTH_BAR_GAP * 2f)
        val currentBarWidth = maxOf(1f, maxBarWidth * (health / maxHealth))

        if (!healthInitialized) {
            healthWidth = currentBarWidth
            healthInitialized = true
        } else {
            healthWidth = moveTowards(healthWidth, currentBarWidth, maxBarWidth * 0.04f)
        }
    }

    override fun draw(scope: UiTree.Scope, translateX: Float, translateY: Float, width: Float, height: Float) {
        val textRenderer = textRendererSupplier.get()

        val headX = PADDING
        val headY = PADDING
        val headRadius = HEAD_SIZE * 0.22f

        scope.roundRect(headX, headY, HEAD_SIZE, HEAD_SIZE, headRadius, fade(Color(0, 0, 0, 48)))

        val target = this.target
        if (target is AbstractClientPlayer) {
            val skin: Identifier = target.skin.body().texturePath()
            scope.playerHead(skin, headX, headY, HEAD_SIZE, headRadius, fade(Color.WHITE))
        }

        val textX = headX + HEAD_SIZE + HEAD_GAP
        val iconX = width - ICON_SIZE - ICON_RIGHT_PADDING
        val name = target.name.string
        scope.text(name, textX, headY + 2f, NAME_SCALE, fade(IslandPalette.TEXT_PRIMARY))

        val self = NonNullContext.current?.player
        val distanceText = if (self == null) "" else String.format(Locale.ROOT, "distance: %.1f", self.distanceTo(target))
        val nameHeight = textRenderer.getHeight(NAME_SCALE)
        scope.text(distanceText, textX, headY + 2f + nameHeight + 3f, INFO_SCALE, fade(IslandPalette.TEXT_MUTED))

        drawTargetIcon(scope, iconX + ICON_SIZE * 0.5f, headY + ICON_TOP_OFFSET + ICON_SIZE * 0.5f)

        val maxHealth = maxOf(1.0f, target.maxHealth + maxOf(0.0f, target.absorptionAmount))
        val health = HealthManager.getHealth(target)

        val healthText = formatHealth(health)
        val maxHealthText = formatHealth(maxHealth)
        val healthTextHeight = textRenderer.getHeight(HEALTH_SCALE)
        val healthTextWidth = textRenderer.getWidth(healthText, HEALTH_SCALE)
        val maxHealthTextWidth = textRenderer.getWidth(maxHealthText, HEALTH_SCALE)
        val healthRowHeight = maxOf(BAR_HEIGHT, healthTextHeight)
        val healthRowY = headY + HEAD_SIZE + HEALTH_ROW_GAP
        val healthY = healthRowY + (healthRowHeight - healthTextHeight) * 0.5f

        val barX = HEALTH_TEXT_INSET + healthTextWidth + HEALTH_BAR_GAP
        val barRight = width - HEALTH_TEXT_INSET - maxHealthTextWidth - HEALTH_BAR_GAP
        val barWidth = maxOf(1f, barRight - barX)
        val barY = healthRowY + (healthRowHeight - BAR_HEIGHT) * 0.5f
        val barRadius = BAR_HEIGHT * 0.5f

        scope.roundRect(barX, barY, barWidth, BAR_HEIGHT, barRadius, fade(IslandPalette.TRACK))

        val palette = arrayOf(
            IslandPalette.ACCENT,
            IslandPalette.ACCENT_ALT,
            IslandPalette.SUCCESS
        )
        val filled = Mth.clamp(barWidth, 0f, healthWidth)
        if (filled > 0f) {
            scope.roundRectHorizontalGradient(
                barX, barY, filled, BAR_HEIGHT, barRadius,
                fade(palette[0]), fade(palette[palette.size - 1])
            )
        }

        scope.text(healthText, HEALTH_TEXT_INSET, healthY, HEALTH_SCALE, fade(IslandPalette.TEXT_SECONDARY))
        scope.text(maxHealthText, width - HEALTH_TEXT_INSET - maxHealthTextWidth, healthY, HEALTH_SCALE, fade(IslandPalette.TEXT_SECONDARY))
    }

    private fun drawTargetIcon(scope: UiTree.Scope, centerX: Float, centerY: Float) {
        val radius = ICON_SIZE * 0.34f
        val color = fade(IslandPalette.ACCENT)
        scope.circle(centerX, centerY, radius, 1.4f, color)
        scope.roundRect(centerX - 1f, centerY - 1f, 2f, 2f, 1f, color)

        val tick = radius + 2.4f
        scope.rect(centerX - 0.6f, centerY - tick, 1.2f, 2.2f, color)
        scope.rect(centerX - 0.6f, centerY + tick - 2.2f, 1.2f, 2.2f, color)
        scope.rect(centerX - tick, centerY - 0.6f, 2.2f, 1.2f, color)
        scope.rect(centerX + tick - 2.2f, centerY - 0.6f, 2.2f, 1.2f, color)
    }

    override val isClosed: Boolean
        get() = checkPattern() && timer.passedMillise(1200L)

    private companion object {

        private const val PADDING = 8f
        private const val MIN_WIDTH = 160f
        private const val HEAD_SIZE = 36f
        private const val HEAD_GAP = 6f
        private const val ICON_SIZE = 16f
        private const val ICON_RIGHT_PADDING = 12f
        private const val ICON_TOP_OFFSET = 4f
        private const val NAME_ICON_GAP = 4f
        private const val NAME_SCALE = 1.25f
        private const val INFO_SCALE = 1.0f
        private const val HEALTH_SCALE = 0.9f
        private const val BAR_HEIGHT = 4f
        private const val HEALTH_ROW_GAP = 6f
        private const val HEALTH_TEXT_INSET = 10f
        private const val HEALTH_BAR_GAP = 5f

        private fun formatHealth(health: Float): String {
            return String.format(Locale.ROOT, "%.1f", health)
        }

        private fun moveTowards(current: Float, target: Float, speed: Float): Float {
            val difference = target - current
            if (abs(difference) <= speed) {
                return target
            }
            return current + sign(difference) * speed
        }

    }

}
