package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.settings.getValue
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.utils.render.WireframeEntityRenderer
import com.github.epsilon.utils.runSafe
import com.github.epsilon.utils.world
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import java.awt.Color

object CrystalChams : Module("Crystal Chams", Category.RENDER) {

    private val sideColor by colorSetting("Side Color", Color(160, 120, 255, 70), true)
    private val lineColor by colorSetting("Line Color", Color(230, 220, 255, 220), true)
    private val scale by doubleSetting("Scale", 1.0, 0.1, 3.0, 0.05)
    private val lineWidth by doubleSetting("Line Width", 2.0, 0.5, 5.0, 0.5)

    init {
        handler<Render3DEvent> { onRender3D(it) }
    }

    private fun onRender3D(event: Render3DEvent) {
        runSafe {
            var batching = false

            for (entity in world.entitiesForRendering()) {
                if (entity !is EndCrystal) continue
                if (!batching) {
                    WireframeEntityRenderer.beginBatch(event.poseStack)
                    batching = true
                }

                WireframeEntityRenderer.render(
                    event.poseStack, entity, scale, sideColor, lineColor, lineWidth.toFloat()
                )
            }

            if (batching) {
                WireframeEntityRenderer.endBatch()
            }
        }
    }

}
