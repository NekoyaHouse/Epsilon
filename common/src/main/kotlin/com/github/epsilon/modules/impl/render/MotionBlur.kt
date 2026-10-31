package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.api.handler
import com.github.epsilon.events.api.nonNullHandler
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.settings.getValue
import com.github.epsilon.events.impl.AfterRender3DEvent
import com.github.epsilon.events.impl.GameJoinedEvent
import com.github.epsilon.events.impl.GameLeftEvent
import com.github.epsilon.events.impl.LevelUpdateEvent
import com.github.epsilon.graphics.shaders.MotionBlurShader
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.settings.impl.EnumSetting
import com.github.epsilon.settings.impl.IntSetting
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Matrix4fc

object MotionBlur : Module("Motion Blur", Category.RENDER) {

    enum class Algorithm {
        Backwards,
        Centered
    }

    private val strength by doubleSetting("Strength", 1.0, 0.0, 5.0, 0.05)
    private val samples by intSetting("Samples", 100, 4, 256, 4)
    private val algorithm by enumSetting("Algorithm", Algorithm.Centered)
    private val depthBlur by boolSetting("Depth Blur", true)
    private val renderThirdPerson by boolSetting("Third Person", true)
    private val refreshRateScaling by boolSetting("Refresh Rate Scaling", true)

    private val currentView = Matrix4f()
    private val currentProjection = Matrix4f()
    private var currentCameraPosition: Vec3 = Vec3.ZERO
    private var frameCaptured = false

    init {
        handler<AfterRender3DEvent>(EventPriority.HIGHEST) { onAfterRender3D(it) }
        handler<GameJoinedEvent> { onGameJoined(it) }
        handler<GameLeftEvent> { onGameLeft(it) }
        nonNullHandler<LevelUpdateEvent> { onLevelUpdate(it) }
    }

    fun captureFrame(cameraState: CameraRenderState, modelViewMatrix: Matrix4fc) {
        if (!isEnabled) return
        currentView.set(modelViewMatrix)
        currentProjection.set(cameraState.projectionMatrix)
        currentCameraPosition = cameraState.pos
        frameCaptured = true
    }

    private fun onAfterRender3D(event: AfterRender3DEvent) {
        if (!frameCaptured) return
        frameCaptured = false

        val cameraAllowed = mc.options.cameraType.isFirstPerson || renderThirdPerson
        MotionBlurShader.render(
            mc.gameRenderer.mainRenderTarget(),
            currentView,
            currentProjection,
            currentCameraPosition,
            MotionBlurShader.Settings(
                strength.toFloat(),
                samples,
                algorithm.ordinal,
                depthBlur,
                refreshRateScaling,
                cameraAllowed
            )
        )
    }

    private fun onGameJoined(event: GameJoinedEvent) {
        resetHistory()
    }

    private fun onGameLeft(event: GameLeftEvent) {
        resetHistory()
    }

    private fun onLevelUpdate(event: LevelUpdateEvent) {
        resetHistory()
    }

    override fun onEnable() {
        resetHistory()
    }

    override fun onDisable() {
        resetHistory()
    }

    private fun resetHistory() {
        frameCaptured = false
        MotionBlurShader.resetHistory()
    }

}
