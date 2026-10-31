package com.github.epsilon.elements.impl

import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.utils.safety.NonNullContext
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import java.awt.Color

/**
 * 3x9 物品栏 HUD。
 */
object Inventory : HudModule("Inventory HUD", 0f, 0f, 180f, 80f) {

    private val scale by doubleSetting("Scale", 1.0, 0.5..2.0, 0.1)
    private val cornerRadius by doubleSetting("Corner Radius", 3.0, 0.0..14.0, 0.5)
    private val backgroundColor by colorSetting("Background Color", Color(15, 15, 15, 90))
    private val slotColor by colorSetting("Slot Color", Color(0, 0, 0, 0))
    private val drawShadow by boolSetting("Drop Shadow", true)
    private val shadowBlur by doubleSetting("Shadow Blur", 9.0, 2.0..32.0, 1.0, { drawShadow })
    private val shadowColor by colorSetting("Shadow Color", Color(255, 255, 255, 110), true, { drawShadow })
    private val backgroundBlur by boolSetting("Background Blur", true)
    private val blurStrength by intSetting("Blur Strength", 5, 1..16, 1)

    private val showCount by boolSetting("Show Count", true)

    private const val SLOT_SIZE = 17.0f
    private const val SLOT_GAP = 1.5f
    private const val PADDING = 4.5f

    override fun render(deltaTracker: DeltaTracker) {
        if (NonNullContext.current == null) return

        val scope = renderScope()

        val scale = this.scale.toFloat()
        val slotSize = SLOT_SIZE * scale
        val gap = SLOT_GAP * scale
        val padding = PADDING * scale
        val radius = cornerRadius.toFloat() * scale
        val slotRadius = 2.0f * scale

        val totalWidth = padding * 2f + 9 * slotSize + (9 - 1) * gap
        val totalHeight = padding * 2f + 3 * slotSize + (3 - 1) * gap

        if (backgroundBlur) {
            BlurShader.render(x, y, totalWidth, totalHeight, radius, blurStrength.toFloat())
        }

        if (drawShadow) {
            scope.shadow(
                x, y, totalWidth, totalHeight, radius,
                shadowBlur.toFloat() * scale, shadowColor,
            )
        }
        scope.roundRect(x, y, totalWidth, totalHeight, radius, backgroundColor)

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotX = x + padding + col * (slotSize + gap)
                val slotY = y + padding + row * (slotSize + gap)
                scope.roundRect(slotX, slotY, slotSize, slotSize, slotRadius, slotColor)
            }
        }

        setBounds(totalWidth, totalHeight)
    }

    override fun renderOverlay(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        val player = NonNullContext.current?.player ?: return

        val scale = this.scale.toFloat()
        val slotSize = SLOT_SIZE * scale
        val gap = SLOT_GAP * scale
        val padding = PADDING * scale

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotIndex = 9 + row * 9 + col
                val stack = player.inventory.getItem(slotIndex)
                if (!stack.isEmpty) {
                    val slotX = x + padding + col * (slotSize + gap)
                    val slotY = y + padding + row * (slotSize + gap)
                    drawItem(graphics, stack, slotX, slotY, scale)
                }
            }
        }
    }

    private fun drawItem(graphics: GuiGraphicsExtractor, stack: ItemStack, slotX: Float, slotY: Float, scale: Float) {
        graphics.pose().pushMatrix()
        graphics.pose().translate(slotX + scale, slotY + scale)
        graphics.pose().scale(scale, scale)
        graphics.item(stack, 0, 0)
        if (showCount && stack.count > 1) {
            graphics.itemDecorations(mc.font, stack, 0, 0, stack.count.toString())
        }
        graphics.pose().popMatrix()
    }

}
