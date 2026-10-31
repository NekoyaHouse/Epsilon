package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.impl.Render3DEvent
import com.github.epsilon.kernel.jvm.runSafe
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.utils.render.SkeletonRenderer
import java.awt.Color

object Skeleton : Module("Skeleton", Category.RENDER) {

    private val friends by boolSetting("Friends", true)
    private val self by boolSetting("Self", false)
    private val invisibles by boolSetting("Invisibles", true)
    private val distance by doubleSetting("Distance", 128.0, 8.0..256.0, 8.0)
    private val lineWidth by doubleSetting("Line Width", 2.0, 0.5..5.0, 0.5)
    private val color by colorSetting("Color", Color.WHITE)
    private val friendColor by colorSetting("Friend Color", Color(0x30FF00), true, { friends })

    init {
        handler<Render3DEvent> { onRender3D(it) }
    }

    private fun onRender3D(event: Render3DEvent) {
        runSafe {
            for (candidate in level.players()) {
                if (!candidate.isAlive || candidate.isSpectator) continue
                if (candidate.isInvisible && !invisibles) continue
                if (candidate.position()
                        .distanceToSqr(mc.gameRenderer.mainCamera().position()) > distance * distance
                ) continue
                if (candidate === player && !self) continue
                if (candidate === mc.cameraEntity && mc.options.cameraType.isFirstPerson) continue

                val friend = candidate !== player &&
                        (FriendManager.isFriend(candidate) || TargetManager.isSameTeam(candidate))
                if (friend && !friends) continue

                SkeletonRenderer.render(
                    candidate,
                    if (friend) friendColor else color,
                    lineWidth.toFloat()
                )
            }
        }
    }

}
