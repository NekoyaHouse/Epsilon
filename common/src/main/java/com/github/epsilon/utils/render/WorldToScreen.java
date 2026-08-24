package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.modules.impl.render.CameraClip;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static com.github.epsilon.Constants.mc;

public class WorldToScreen {

    private static final float REFERENCE_PIXELS_PER_WORLD_UNIT = 20.0f;
    private static final Matrix4f STABLE_VIEW_MATRIX = new Matrix4f();
    private static final Matrix4f WORLD_PROJECTION_MATRIX = new Matrix4f();
    private static final Matrix4f VIEW_BOBBING_MATRIX = new Matrix4f();

    private WorldToScreen() {
    }

    /**
     * 将世界坐标原始投影到 Lumin 坐标系。
     *
     * @param pos 目标位置
     * @return 未执行深度剔除的屏幕坐标
     */
    public static Vector3f calcWorld2ScreenRaw(Vec3 pos) {
        Camera camera = mc.gameRenderer.mainCamera();
        CameraRenderState cameraState = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        Vector3f cameraRelativePos = pos.subtract(camera.position()).toVector3f();
        Vector3f viewPos = camera.getViewRotationMatrix(STABLE_VIEW_MATRIX).transformPosition(cameraRelativePos, new Vector3f());
        Vector3f projected = worldProjection(cameraState).transformProject(viewPos, new Vector3f());

        float width = LuminRenderSystem.getScaledWidth();
        float height = LuminRenderSystem.getScaledHeight();
        return projected.set(
                (projected.x + 1.0f) * 0.5f * width,
                (1.0f - projected.y) * 0.5f * height,
                -viewPos.z
        );
    }

    /**
     * 原版将 bobView 乘进世界投影矩阵，而不是相机视图矩阵。这里使用同一变换，
     * 避免标签与已经摇晃的实体模型产生反向相对位移。
     */
    private static Matrix4f worldProjection(CameraRenderState cameraState) {
        WORLD_PROJECTION_MATRIX.set(cameraState.projectionMatrix);
        VIEW_BOBBING_MATRIX.identity();

        if (mc.gameRenderer.gameRenderState().optionsRenderState.bobView && cameraState.entityRenderState.isPlayer) {
            float walkDistance = cameraState.entityRenderState.backwardsInterpolatedWalkDistance;
            float bob = cameraState.entityRenderState.bob;
            float sin = Mth.sin(walkDistance * (float) Math.PI);
            float cos = Mth.cos(walkDistance * (float) Math.PI);

            CameraClip cameraClip = CameraClip.INSTANCE;
            if (!cameraClip.isEnabled() || !cameraClip.betterBobView.getValue()) {
                VIEW_BOBBING_MATRIX.translate(sin * bob * 0.5f, -Math.abs(cos * bob), 0.0f);
            }
            VIEW_BOBBING_MATRIX.rotateZ((float) Math.toRadians(sin * bob * 3.0f));
            VIEW_BOBBING_MATRIX.rotateX((float) Math.toRadians(Math.abs(Mth.cos(walkDistance * (float) Math.PI - 0.2f) * bob) * 5.0f));
            WORLD_PROJECTION_MATRIX.mul(VIEW_BOBBING_MATRIX);
        }

        return WORLD_PROJECTION_MATRIX;
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
        Camera camera = mc.gameRenderer.mainCamera();
        Vector3f cameraRelativePos = pos.subtract(camera.position()).toVector3f();
        float depth = -camera.getViewRotationMatrix(STABLE_VIEW_MATRIX).transformPosition(cameraRelativePos, new Vector3f()).z;
        if (depth < Camera.PROJECTION_Z_NEAR) return 0.0f;

        float projectionYScale = 1.0f / (float) Math.tan(Math.toRadians(camera.getFov()) * 0.5);
        return LuminRenderSystem.getScaledHeight() * projectionYScale / (2.0f * depth * REFERENCE_PIXELS_PER_WORLD_UNIT);
    }

}
