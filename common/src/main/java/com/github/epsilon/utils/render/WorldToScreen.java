package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import static com.github.epsilon.Constants.mc;

public final class WorldToScreen {

    private static final float REFERENCE_PIXELS_PER_WORLD_UNIT = 20.0f;

    private WorldToScreen() {
    }

    /**
     * 将世界坐标原始投影到 Lumin 坐标系。
     *
     * @param pos 目标位置
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
     * 将世界坐标投影到 Lumin 坐标系并执行近裁面剔除。
     *
     * @param pos 目标位置
     * @return 屏幕坐标；位于近裁面内或摄像机后方时返回 null
     */
    public static Vector3f calcWorld2Screen(Vec3 pos) {
        Vector3f projected = calcWorld2ScreenRaw(pos);
        return projected.z < Camera.PROJECTION_Z_NEAR ? null : projected;
    }

    /**
     * 计算世界坐标处的透视 UI 缩放。
     *
     * @param pos 目标位置
     * @return 透视 UI 缩放；无效深度返回 0
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
