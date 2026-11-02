package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.render.WireframeEntityRenderer
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import java.awt.Color
import java.util.*

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
            val candidates = PriorityQueue(MAX_RENDERED_CRYSTALS, FARTHEST_FIRST)

            for (entity in level.entitiesForRendering()) {
                if (entity !is EndCrystal) continue
                val partialTick =
                    mc.deltaTracker.getGameTimeDeltaPartialTick(!level.tickRateManager().isEntityFrozen(entity))
                if (!mc.entityRenderDispatcher.shouldRender(
                        entity, cameraState.cullFrustum, cameraPos.x, cameraPos.y, cameraPos.z, partialTick
                    )
                ) continue

                val distanceSquared = entity.distanceToSqr(cameraPos)
                if (candidates.size < MAX_RENDERED_CRYSTALS) {
                    candidates.add(CrystalCandidate(entity, distanceSquared))
                } else {
                    val farthest = candidates.peek()
                    if (distanceSquared > farthest.distanceSquared ||
                        distanceSquared == farthest.distanceSquared && entity.id >= farthest.entity.id
                    ) continue
                    candidates.poll()
                    candidates.add(CrystalCandidate(entity, distanceSquared))
                }
            }

            if (candidates.isEmpty()) return@runSafe

            WireframeEntityRenderer.beginBatch(event.poseStack)
            try {
                for ((entity) in candidates.sortedWith(FARTHEST_FIRST.reversed())) {
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

    private const val MAX_RENDERED_CRYSTALS = 256

    // 密集实体场景下限制每帧的模型重建与描边顶点数量，优先保留镜头附近的水晶。
    private val FARTHEST_FIRST =
        compareByDescending<CrystalCandidate> { it.distanceSquared }.thenByDescending { it.entity.id }

}
