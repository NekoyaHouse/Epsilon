package com.github.epsilon.modules.impl.render

import com.github.epsilon.modules.Category
import com.github.epsilon.settings.getValue
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.DoubleSetting
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3

object CameraClip : Module("Camera Clip", Category.RENDER) {

    val distance by doubleSetting("Distance", 3.5, 1.0, 20.0, 0.5)
    val action by boolSetting("Action", true) { _ -> resetCameraPos() }
    val actionSmoothness by doubleSetting("Action Smoothness", 0.3, 0.1, 0.95, 0.01, Setting.Dependency { action })
    val actionMaxDistance by doubleSetting("Action Max Distance", 20.0, 1.0, 50.0, 0.5, Setting.Dependency { action })
    val betterBobView by boolSetting("Better Bob View", true, Setting.Dependency { mc.options.bobView().get() })

    private var cameraPos: Vec3? = null

    override fun onEnable() {
        cameraPos = null
    }

    override fun onDisable() {
        cameraPos = null
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

        val smoothFactor = actionSmoothness * (1.0 - Math.exp(-distanceToTarget / maxDistance))

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
