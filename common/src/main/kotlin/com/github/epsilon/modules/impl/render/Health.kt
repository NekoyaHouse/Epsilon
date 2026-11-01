package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.render.ColorUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import java.awt.Color
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

object Health : Module("Health", Category.RENDER) {

    private const val HEART_CHARACTER = "\u2764"
    private val HEART_COLOR: Int = 0xFFFF5555.toInt() // §c
    private val ABSORPTION_COLOR: Int = 0xFFFFFF55.toInt() // §e
    private val ABSORPTION_HEART_COLOR: Int = 0xFFFFAA00.toInt() // §6
    private const val HEART_SIZE = 9
    private const val HEART_SPACING = 8
    private const val HEARTS_PER_ROW = 10
    private const val ROW_HEIGHT = 10

    private val HEALTH_COLOR_FRACTIONS = floatArrayOf(0.0f, 0.5f, 1.0f)
    private val HEALTH_COLORS = arrayOf(
        Color(255, 37, 0),
        Color.YELLOW,
        Color.GREEN
    )

    private val decimalFormat = DecimalFormat("0.#", DecimalFormatSymbols(Locale.ENGLISH))
    private val random = Random()

    private var healthStateInitialized = false
    private var lastHealth = 0
    private var displayHealth = 0
    private var blinkUntilTick = -1

    init {
        onEnable {
            resetHealthState()
        }

        onDisable {
            resetHealthState()
        }

        safeEventHandler<Render2DEvent.HUD> { event ->
            runSafe {
                renderHealth(event.guiGraphics)
            }
        }
    }

    private fun renderHealth(graphics: GuiGraphicsExtractor) {
        val player = mc.player ?: return
        val health = player.health
        val absorption = player.absorptionAmount
        val maxHealth = player.maxHealth

        updateHealthState(player)

        val centerX = graphics.guiWidth() / 2
        val centerY = graphics.guiHeight() / 2
        val offsetY = getScreenOffsetY(mc.gui.screen())

        drawHealthText(graphics, health, absorption, maxHealth, centerX, centerY + 25 + offsetY)
        drawHearts(graphics, player, health, maxHealth, centerX, centerY + 15 + offsetY)
    }

    private fun updateHealthState(player: Player) {
        val currentHealth = Mth.ceil(player.health)

        if (!healthStateInitialized) {
            lastHealth = currentHealth
            displayHealth = currentHealth
            healthStateInitialized = true
            return
        }

        if (currentHealth != lastHealth && player.invulnerableTime > 0) {
            blinkUntilTick = player.tickCount + (if (currentHealth < lastHealth) 20 else 10)
        }

        lastHealth = currentHealth

        if (player.tickCount >= blinkUntilTick) {
            displayHealth = currentHealth
        }
    }

    private fun resetHealthState() {
        healthStateInitialized = false
        lastHealth = 0
        displayHealth = 0
        blinkUntilTick = -1
    }

    private fun drawHealthText(
        graphics: GuiGraphicsExtractor, health: Float, absorption: Float, maxHealth: Float, centerX: Int, y: Int,
    ) {
        val x = centerX - 6 - (if (absorption > 0.0f) 12 else 0)
        var cursorX = x

        val healthText = decimalFormat.format(health / 2.0f)
        val healthColor = blendColors(
            HEALTH_COLOR_FRACTIONS, HEALTH_COLORS, (health / maxHealth).coerceIn(0.0f, 1.0f)
        )

        graphics.text(mc.font, healthText, cursorX, y, healthColor.rgb, true)
        cursorX += mc.font.width(healthText)

        graphics.text(mc.font, HEART_CHARACTER, cursorX, y, HEART_COLOR, true)
        cursorX += mc.font.width(HEART_CHARACTER) + mc.font.width(" ")

        if (absorption > 0.0f) {
            val absorptionText = decimalFormat.format(absorption / 2.0f)
            graphics.text(mc.font, absorptionText, cursorX, y, ABSORPTION_COLOR, true)
            cursorX += mc.font.width(absorptionText)
            graphics.text(mc.font, HEART_CHARACTER, cursorX, y, ABSORPTION_HEART_COLOR, true)
        }
    }

    private fun drawHearts(
        graphics: GuiGraphicsExtractor, player: Player, health: Float, maxHealth: Float, centerX: Int, baseY: Int,
    ) {
        val currentHealth = Mth.ceil(health)
        val containerCount = Mth.ceil(maxHealth / 2.0f)
        if (containerCount <= 0) return

        val heartType = HeartType.forPlayer(player)
        val hardcore = player.level().levelData.isHardcore
        val blink = player.tickCount < blinkUntilTick && (blinkUntilTick - player.tickCount) / 3 % 2 == 1

        this.random.setSeed(player.tickCount.toLong() * 312871L)
        val regenOffsetIndex = if (player.hasEffect(MobEffects.REGENERATION)) {
            player.tickCount % Mth.ceil(maxHealth + 5.0f)
        } else {
            -1
        }

        for (i in containerCount - 1 downTo 0) {
            val row = i / HEARTS_PER_ROW
            val column = i % HEARTS_PER_ROW
            val columnsInRow = minOf(HEARTS_PER_ROW, containerCount - row * HEARTS_PER_ROW)
            val rowX = centerX - (columnsInRow * HEART_SPACING + 1) / 2.0f
            var x = rowX + column * HEART_SPACING
            var y = (baseY - row * ROW_HEIGHT).toFloat()

            if (currentHealth <= 4) {
                y += this.random.nextInt(2)
            }

            if (i == regenOffsetIndex) {
                y -= 2.0f
            }

            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                HeartType.CONTAINER.getSprite(hardcore, false, blink),
                x.toInt(), y.toInt(), HEART_SIZE, HEART_SIZE
            )

            val halves = i * 2
            if (blink && halves < displayHealth) {
                val halfHeart = halves + 1 == displayHealth
                graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    heartType.getSprite(hardcore, halfHeart, true),
                    x.toInt(), y.toInt(), HEART_SIZE, HEART_SIZE
                )
            }

            if (halves < currentHealth) {
                val halfHeart = halves + 1 == currentHealth
                graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    heartType.getSprite(hardcore, halfHeart, false),
                    x.toInt(), y.toInt(), HEART_SIZE, HEART_SIZE
                )
            }
        }
    }

    private fun getScreenOffsetY(screen: Screen?): Int {
        if (screen is InventoryScreen) {
            return 70
        }
        if (screen is CreativeModeInventoryScreen) {
            return 80
        }
        if (screen is ContainerScreen) {
            // 现代 ContainerScreen 的高度为 114 + 行数 * 18，等价于旧版 GuiChest 的 ySize。
            val imageHeight = 114 + screen.menu.rowCount * 18
            return imageHeight / 2 - 15
        }
        return 0
    }

    private fun heartSprite(name: String): Identifier =
        Identifier.withDefaultNamespace("hud/heart/$name")

    private fun blendColors(fractions: FloatArray, colors: Array<Color>, progressValue: Float): Color {
        if (fractions.isEmpty() || fractions.size != colors.size) {
            return Color.WHITE
        }

        val progress = progressValue.coerceIn(0.0f, 1.0f)

        if (progress <= fractions[0]) {
            return colors[0]
        }
        if (progress >= fractions[fractions.size - 1]) {
            return colors[colors.size - 1]
        }

        for (i in 0 until fractions.size - 1) {
            val start = fractions[i]
            val end = fractions[i + 1]
            if (progress in start..end) {
                val weight = (progress - start) / (end - start)
                return ColorUtils.interpolateColor(colors[i], colors[i + 1], weight)
            }
        }

        return colors[colors.size - 1]
    }

    private enum class HeartType(
        val full: Identifier,
        val fullBlinking: Identifier,
        val half: Identifier,
        val halfBlinking: Identifier,
        val hardcoreFull: Identifier,
        val hardcoreFullBlinking: Identifier,
        val hardcoreHalf: Identifier,
        val hardcoreHalfBlinking: Identifier,
    ) {
        CONTAINER(
            heartSprite("container"), heartSprite("container_blinking"), heartSprite("container"),
            heartSprite("container_blinking"),
            heartSprite("container_hardcore"),
            heartSprite("container_hardcore_blinking"),
            heartSprite("container_hardcore"),
            heartSprite("container_hardcore_blinking")
        ),
        NORMAL(
            heartSprite("full"), heartSprite("full_blinking"), heartSprite("half"),
            heartSprite("half_blinking"),
            heartSprite("hardcore_full"), heartSprite("hardcore_full_blinking"),
            heartSprite("hardcore_half"), heartSprite("hardcore_half_blinking")
        ),
        POISONED(
            heartSprite("poisoned_full"), heartSprite("poisoned_full_blinking"),
            heartSprite("poisoned_half"),
            heartSprite("poisoned_half_blinking"),
            heartSprite("poisoned_hardcore_full"),
            heartSprite("poisoned_hardcore_full_blinking"),
            heartSprite("poisoned_hardcore_half"),
            heartSprite("poisoned_hardcore_half_blinking")
        ),
        WITHERED(
            heartSprite("withered_full"), heartSprite("withered_full_blinking"),
            heartSprite("withered_half"),
            heartSprite("withered_half_blinking"),
            heartSprite("withered_hardcore_full"),
            heartSprite("withered_hardcore_full_blinking"),
            heartSprite("withered_hardcore_half"),
            heartSprite("withered_hardcore_half_blinking")
        ),
        ABSORBING(
            heartSprite("absorbing_full"), heartSprite("absorbing_full_blinking"),
            heartSprite("absorbing_half"),
            heartSprite("absorbing_half_blinking"),
            heartSprite("absorbing_hardcore_full"),
            heartSprite("absorbing_hardcore_full_blinking"),
            heartSprite("absorbing_hardcore_half"),
            heartSprite("absorbing_hardcore_half_blinking")
        ),
        FROZEN(
            heartSprite("frozen_full"), heartSprite("frozen_full_blinking"), heartSprite("frozen_half"),
            heartSprite("frozen_half_blinking"),
            heartSprite("frozen_hardcore_full"),
            heartSprite("frozen_hardcore_full_blinking"),
            heartSprite("frozen_hardcore_half"),
            heartSprite("frozen_hardcore_half_blinking")
        );

        fun getSprite(isHardcore: Boolean, isHalf: Boolean, isBlinking: Boolean): Identifier {
            if (!isHardcore) {
                if (isHalf) {
                    return if (isBlinking) halfBlinking else half
                }
                return if (isBlinking) fullBlinking else full
            }

            if (isHalf) {
                return if (isBlinking) hardcoreHalfBlinking else hardcoreHalf
            }
            return if (isBlinking) hardcoreFullBlinking else hardcoreFull
        }

        companion object {

            fun forPlayer(player: Player): HeartType {
                if (player.hasEffect(MobEffects.POISON)) {
                    return POISONED
                }
                if (player.hasEffect(MobEffects.WITHER)) {
                    return WITHERED
                }
                if (player.isFullyFrozen) {
                    return FROZEN
                }
                return NORMAL
            }

        }
    }

}
