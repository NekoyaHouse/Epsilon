package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.render.WireframeEntityRenderer
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import java.awt.Color

object CrystalChams : Module("Crystal Chams", Category.RENDER) {

    private val sideColor by colorSetting("Side Color", Color(160, 120, 255, 70), true)
    private val lineColor by colorSetting("Line Color", Color(230, 220, 255, 220), true)
    private val scale by doubleSetting("Scale", 1.0, 0.1..3.0, 0.05)
    private val lineWidth by doubleSetting("Line Width", 2.0, 0.5..5.0, 0.5)

    init {
        handler<Render3DEvent> { onRender3D(it) }
    }

    private fun onRender3D(event: Render3DEvent) {
        runSafe {
            val cameraState = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
            val cameraPos = cameraState.pos
            val candidates = mutableListOf<CrystalCandidate>()

            for (entity in level.entitiesForRendering()) {
                if (entity !is EndCrystal) continue
                val partialTick =
                    mc.deltaTracker.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity))
                if (entity.beamTarget == null && (!entity.shouldRender(
                        cameraPos.x,
                        cameraPos.y,
                        cameraPos.z
                    ) || !cameraState.cullFrustum.isVisible(
                        entity.getInterpolatedBoundingBox(partialTick).inflate(0.5)
                    ))
                ) continue

                candidates.add(CrystalCandidate(entity, entity.distanceToSqr(cameraPos)))
            }

            if (candidates.isEmpty()) return@runSafe

            WireframeEntityRenderer.beginBatch(event.poseStack)
            try {
                for ((entity) in candidates.sortedWith(NEAREST_FIRST)) {
                    WireframeEntityRenderer.render(
                        event.poseStack, entity, scale, sideColor, lineColor, lineWidth.toFloat()
                    )
                }
            } finally {
                WireframeEntityRenderer.endBatch()
            }
        }
    }

    private data class CrystalCandidate(val entity: EndCrystal, val distanceSquared: Double)

    private val NEAREST_FIRST = compareBy<CrystalCandidate> { it.distanceSquared }.thenBy { it.entity.id }

}
