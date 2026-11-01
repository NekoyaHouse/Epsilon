package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import kotlin.math.exp

object CameraClip : Module("Camera Clip", Category.RENDER) {

    val distance by doubleSetting("Distance", 3.5, 1.0..20.0, 0.5)
    val action by boolSetting("Action", true, onChanged = { _ -> resetCameraPos() })
    val actionSmoothness by doubleSetting("Action Smoothness", 0.3, 0.1..0.95, 0.01, { action })
    val actionMaxDistance by doubleSetting("Action Max Distance", 20.0, 1.0..50.0, 0.5, { action })
    val betterBobView by boolSetting("Better Bob View", true, { mc.options.bobView().get() })

    private var cameraPos: Vec3? = null

    init {
        onEnable {
            cameraPos = null
        }

        onDisable {
            cameraPos = null
        }
    }

    fun updateActionCamera(targetPos: Vec3) {
        val current = cameraPos
        if (current == null) {
            cameraPos = targetPos
            return
        }

        val distanceToTarget = current.distanceTo(targetPos)
        val maxDistance = actionMaxDistance
        if (distanceToTarget > maxDistance) {
            cameraPos = targetPos
            return
        }

        val smoothFactor = actionSmoothness * (1.0 - exp(-distanceToTarget / maxDistance))

        cameraPos = Vec3(
            Mth.lerp(smoothFactor, current.x, targetPos.x),
            Mth.lerp(smoothFactor, current.y, targetPos.y),
            Mth.lerp(smoothFactor, current.z, targetPos.z)
        )
    }

    fun resetCameraPos() {
        cameraPos = null
    }

    fun getCameraPos(): Vec3? = cameraPos

}
