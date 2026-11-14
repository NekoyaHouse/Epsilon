package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.safeEventHandler
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.kernel.render.WireframeEntityRenderer
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import java.awt.Color

object CrystalChams : Module("Crystal Chams", Category.RENDER) {

    private val throughWalls by boolSetting("Through Walls", false)
    private val scale by doubleSetting("Scale", 1.0, 0.1..3.0, 0.05)
    private val spinSpeed by doubleSetting("Spin Speed", 1.0, 0.0..5.0, 0.1)
    private val floatSpeed by doubleSetting("Float Speed", 1.0, 0.0..5.0, 0.1)
    private val lineWidth by doubleSetting("Line Width", 2.0, 0.5..5.0, 0.5)
    private val sideColor by colorSetting("Side Color", Color(133, 255, 200, 63))
    private val lineColor by colorSetting("Line Color", Color(133, 255, 200, 200))

    init {
        safeEventHandler<Render3DEvent> { event ->
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

            if (candidates.isEmpty()) return@safeEventHandler

            WireframeEntityRenderer.beginBatch(event.poseStack, throughWalls)
            try {
                for ((entity) in candidates.sortedWith(NEAREST_FIRST)) {
                    WireframeEntityRenderer.render(
                        event.poseStack,
                        entity,
                        scale,
                        sideColor,
                        lineColor,
                        lineWidth.toFloat(),
                        spinSpeed.toFloat(),
                        floatSpeed.toFloat(),
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
