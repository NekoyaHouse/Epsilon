package com.github.epsilon.elements.impl

import com.github.epsilon.elements.HudModule
import com.github.epsilon.graphics.shaders.BlurShader
import com.github.epsilon.gui.lib.RoundRect
import com.github.epsilon.gui.lib.Shadow
import com.github.epsilon.gui.lib.UiScope
import com.github.epsilon.gui.lib.Item
import net.minecraft.client.DeltaTracker
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

    override fun measure(deltaTracker: DeltaTracker) {
        val scale = this.scale.toFloat()
        val slotSize = SLOT_SIZE * scale
        val gap = SLOT_GAP * scale
        val padding = PADDING * scale
        val totalWidth = padding * 2f + 9 * slotSize + (9 - 1) * gap
        val totalHeight = padding * 2f + 3 * slotSize + (3 - 1) * gap
        setBounds(totalWidth, totalHeight)
    }

    override fun UiScope.drawUi(deltaTracker: DeltaTracker) {
        val scale = this@Inventory.scale.toFloat()
        val slotSize = SLOT_SIZE * scale
        val gap = SLOT_GAP * scale
        val padding = PADDING * scale
        val radius = cornerRadius.toFloat() * scale
        val slotRadius = 2.0f * scale
        val totalWidth = width
        val totalHeight = height

        if (backgroundBlur) {
            // 背景模糊是直接绘制到屏幕的，必须用绝对坐标（作用域原点 + 相对坐标）
            BlurShader.render(origin.x, origin.y, totalWidth, totalHeight, radius, blurStrength.toFloat())
        }

        if (drawShadow) {
            Shadow(
                0.0f, 0.0f, totalWidth, totalHeight, radius,
                shadowBlur.toFloat() * scale, shadowColor,
            )
        }
        RoundRect(0.0f, 0.0f, totalWidth, totalHeight, radius, backgroundColor)

        for (row in 0 until 3) {
            for (col in 0 until 9) {
                val slotX = padding + col * (slotSize + gap)
                val slotY = padding + row * (slotSize + gap)
                RoundRect(slotX, slotY, slotSize, slotSize, slotRadius, slotColor)
                val stack = mc.player?.inventory?.getItem(9 + row * 9 + col) ?: continue
                Item(
                    stack, slotX + scale, slotY + scale, 16f * scale,
                    decorations = showCount && stack.count > 1
                )
            }
        }
    }

}
