package com.github.epsilon.utils.render

import com.github.epsilon.Constants
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.modules.impl.render.CameraClip
import com.github.epsilon.utils.math.toRadians
import net.minecraft.client.Camera
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

object WorldToScreen {

    private const val REFERENCE_PIXELS_PER_WORLD_UNIT = 20.0f
    private val STABLE_VIEW_MATRIX = Matrix4f()
    private val WORLD_PROJECTION_MATRIX = Matrix4f()
    private val VIEW_BOBBING_MATRIX = Matrix4f()

    /**
     * 将世界坐标原始投影到 Lumin 坐标系。
     *
     * @param pos 目标位置
     * @return 未执行深度剔除的屏幕坐标
     */
    fun calcWorld2ScreenRaw(pos: Vec3): Vector3f {
        val camera = Constants.client.gameRenderer.mainCamera()
        val cameraState = Constants.client.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
        val cameraRelativePos = pos.subtract(camera.position()).toVector3f()
        val viewPos = camera.getViewRotationMatrix(STABLE_VIEW_MATRIX).transformPosition(cameraRelativePos, Vector3f())
        val projected = worldProjection(cameraState).transformProject(viewPos, Vector3f())

        val width = LuminRenderSystem.getScaledWidth()
        val height = LuminRenderSystem.getScaledHeight()
        return projected.set(
            (projected.x + 1.0f) * 0.5f * width,
            (1.0f - projected.y) * 0.5f * height,
            -viewPos.z
        )
    }

    /**
     * 原版将 bobView 乘进世界投影矩阵，而不是相机视图矩阵。这里使用同一变换，
     * 避免标签与已经摇晃的实体模型产生反向相对位移。
     */
    private fun worldProjection(cameraState: CameraRenderState): Matrix4f {
        WORLD_PROJECTION_MATRIX.set(cameraState.projectionMatrix)
        VIEW_BOBBING_MATRIX.identity()

        if (Constants.client.gameRenderer.gameRenderState().optionsRenderState.bobView && cameraState.entityRenderState.isPlayer) {
            val walkDistance = cameraState.entityRenderState.backwardsInterpolatedWalkDistance
            val bob = cameraState.entityRenderState.bob
            val sin = Mth.sin((walkDistance * PI.toFloat()).toDouble())
            val cos = Mth.cos((walkDistance * PI.toFloat()).toDouble())

            val cameraClip = CameraClip
            if (!cameraClip.isEnabled || !cameraClip.betterBobView) {
                VIEW_BOBBING_MATRIX.translate(sin * bob * 0.5f, -abs(cos * bob), 0.0f)
            }
            VIEW_BOBBING_MATRIX.rotateZ(((sin * bob * 3.0f).toDouble()).toRadians().toFloat())
            VIEW_BOBBING_MATRIX.rotateX(
                ((abs(Mth.cos((walkDistance * PI.toFloat() - 0.2f).toDouble()) * bob) * 5.0f).toDouble()).toRadians().toFloat()
            )
            WORLD_PROJECTION_MATRIX.mul(VIEW_BOBBING_MATRIX)
        }

        return WORLD_PROJECTION_MATRIX
    }

    /**
     * 将世界坐标投影到 Lumin 坐标系并执行近裁面剔除。
     *
     * @param pos 目标位置
     * @return 屏幕坐标；位于近裁面内或摄像机后方时返回 null
     */
    fun calcWorld2Screen(pos: Vec3): Vector3f? {
        val projected = calcWorld2ScreenRaw(pos)
        return if (projected.z < Camera.PROJECTION_Z_NEAR) null else projected
    }

    /**
     * 计算世界坐标处的透视 UI 缩放。
     *
     * @param pos 目标位置
     * @return 透视 UI 缩放；无效深度返回 0
     */
    fun calcScale(pos: Vec3): Float {
        val camera = Constants.client.gameRenderer.mainCamera()
        val cameraRelativePos = pos.subtract(camera.position()).toVector3f()
        val depth = -camera.getViewRotationMatrix(STABLE_VIEW_MATRIX).transformPosition(cameraRelativePos, Vector3f()).z
        if (depth < Camera.PROJECTION_Z_NEAR) return 0.0f

        val projectionYScale = 1.0f / tan((camera.fov.toDouble()).toRadians() * 0.5).toFloat()
        return LuminRenderSystem.getScaledHeight() * projectionYScale / (2.0f * depth * REFERENCE_PIXELS_PER_WORLD_UNIT)
    }

}
