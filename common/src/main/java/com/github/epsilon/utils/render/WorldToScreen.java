package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

import static com.github.epsilon.Constants.mc;

public final class WorldToScreen {

    private static final float REFERENCE_PIXELS_PER_WORLD_UNIT = 20.0f;

    private WorldToScreen() {
    }

    /**
     * 原始投影的 x/y 使用 Lumin 坐标，z 使用世界单位的视图空间前向深度。
     *
     * @param pos 世界空间中的绝对坐标
     * @return 未执行深度剔除的屏幕坐标
     */
    public static Vector3f calcWorld2ScreenRaw(Vec3 pos) {
        CameraRenderState cameraState = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Vector3f cameraRelativePos = pos.subtract(cameraState.pos).toVector3f();
        Vector3f viewPos = cameraState.viewRotationMatrix.transformPosition(cameraRelativePos, new Vector3f());
        Vector3f projected = cameraState.projectionMatrix.transformProject(viewPos, new Vector3f());

        float width = LuminRenderSystem.getScaledWidth();
        float height = LuminRenderSystem.getScaledHeight();
        return projected.set(
                (projected.x + 1.0f) * 0.5f * width,
                (1.0f - projected.y) * 0.5f * height,
                -viewPos.z
        );
    }

    /**
     * 将世界坐标投影到 Lumin Render Scale 坐标系，并剔除摄像机后方及近裁面内的点。
     *
     * @param pos 世界空间中的绝对坐标
     * @return 屏幕坐标；前向深度小于固定近裁面时返回 {@code null}
     */
    @Nullable
    public static Vector3f calcWorld2Screen(Vec3 pos) {
        Vector3f projected = calcWorld2ScreenRaw(pos);
        return projected.z < Camera.PROJECTION_Z_NEAR ? null : projected;
    }

    /**
     * 计算世界坐标处的透视 UI 缩放，以每世界单位投影为 20 个 Lumin 像素时作为 1.0。
     *
     * @param pos 世界空间中的绝对坐标
     * @return 该位置的 UI 缩放；位于摄像机后方或近裁面内时返回 0
     */
    public static float calcScale(Vec3 pos) {
        CameraRenderState cameraState = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Vector3f cameraRelativePos = pos.subtract(cameraState.pos).toVector3f();
        float depth = -cameraState.viewRotationMatrix.transformPosition(cameraRelativePos, new Vector3f()).z;
        if (depth < Camera.PROJECTION_Z_NEAR) return 0.0f;

        return LuminRenderSystem.getScaledHeight() * cameraState.projectionMatrix.m11()
                / (2.0f * depth * REFERENCE_PIXELS_PER_WORLD_UNIT);
    }

}
