package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.safety.runSafe
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import java.awt.Color

object BlockHighlight : Module("Block Highlight", Category.RENDER) {

    init {
        handler<Render3DEvent> { onRender3D(it) }
    }

    private enum class Mode {
        Both,
        BothSide,
        Fill,
        FilledSide,
        Outline,
        OutlinedSide
    }

    private val mode by enumSetting("Mode", Mode.Outline)
    private val sideColor by colorSetting(
        "Color", Color(255, 255, 255, 100),
        true,
        {
            mode == Mode.Both || mode == Mode.BothSide || mode == Mode.Fill || mode == Mode.FilledSide
        }
    )
    private val lineColor by colorSetting(
        "Line Color", Color(255, 255, 255, 255),
        true,
        {
            mode == Mode.Both || mode == Mode.BothSide || mode == Mode.Outline || mode == Mode.OutlinedSide
        }
    )
    private val lineWidth by doubleSetting("Line Width", 2.0, 0.0..5.0, 0.5)
    private val blur by boolSetting("Blur", true)
    private val blurStrength by doubleSetting(
        "Blur Strength", 5.0, 0.0..16.0, 0.5,
        {
            blur && (mode == Mode.Both || mode == Mode.BothSide || mode == Mode.Fill)
        }
    )

    private fun onRender3D(event: Render3DEvent) {
        val hitResult = mc.hitResult
        if (hitResult == null || hitResult.type != HitResult.Type.BLOCK || hitResult !is BlockHitResult) {
            return
        }

        runSafe {
            val thickness = lineWidth.toFloat()
            val box = AABB(hitResult.blockPos)
            val fillColor = sideColor.rgb
            val outlineColor = lineColor.rgb
            val direction = hitResult.direction

            when (mode) {
                Mode.Both -> {
                    drawBlur(box)
                    Render3DScheduler.addFilledBox(box, fillColor)
                    Render3DScheduler.addOutlineBox(box, outlineColor, thickness)
                }

                Mode.BothSide -> {
                    drawBlur(box)
                    Render3DScheduler.addSideOutline(box, outlineColor, thickness, direction)
                    Render3DScheduler.addFilledSide(box, fillColor, direction)
                }

                Mode.Fill -> {
                    drawBlur(box)
                    Render3DScheduler.addFilledBox(box, fillColor)
                }

                Mode.FilledSide -> {
                    Render3DScheduler.addFilledSide(box, fillColor, direction)
                }

                Mode.Outline -> {
                    Render3DScheduler.addOutlineBox(box, outlineColor, thickness)
                }

                Mode.OutlinedSide -> {
                    Render3DScheduler.addSideOutline(box, outlineColor, thickness, direction)
                }
            }
        }
    }

    private fun drawBlur(aabb: AABB) {
        if (blur) Render3DScheduler.addBlurredBox(aabb, blurStrength)
    }

}
