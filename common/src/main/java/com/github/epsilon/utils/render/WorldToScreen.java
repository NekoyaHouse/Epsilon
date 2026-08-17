package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import static com.github.epsilon.Constants.mc;

public class WorldToScreen {

    private static final float REFERENCE_PIXELS_PER_WORLD_UNIT = 20.0f;

    private static final int[][] AABB_EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private WorldToScreen() {
    }

    /**
     * 将世界坐标原始投影到 Lumin 坐标系。
     *
     * @param pos 目标位置
     * @return 未执行深度剔除的屏幕坐标
     */
    public static Vector3f calcWorld2ScreenRaw(Vec3 pos) {
        CameraRenderState cameraState = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
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

    public static float[] calcScreenBounds(AABB box) {
        Vec3[] vertices = new Vec3[8];
        Vector3f[] projectedVertices = new Vector3f[8];
        float[] bounds = {
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };

        for (int vertex = 0; vertex < 8; vertex++) {
            Vec3 worldVertex = new Vec3(
                    (vertex & 1) == 0 ? box.minX : box.maxX,
                    (vertex & 2) == 0 ? box.minY : box.maxY,
                    (vertex & 4) == 0 ? box.minZ : box.maxZ
            );
            Vector3f projected = calcWorld2ScreenRaw(worldVertex);
            vertices[vertex] = worldVertex;
            projectedVertices[vertex] = projected;

            if (projected.z >= Camera.PROJECTION_Z_NEAR) {
                includeProjected(bounds, projected);
            }
        }

        for (int[] edge : AABB_EDGES) {
            Vector3f firstProjected = projectedVertices[edge[0]];
            Vector3f secondProjected = projectedVertices[edge[1]];
            boolean firstVisible = firstProjected.z >= Camera.PROJECTION_Z_NEAR;
            boolean secondVisible = secondProjected.z >= Camera.PROJECTION_Z_NEAR;
            if (firstVisible == secondVisible) continue;

            float deltaDepth = secondProjected.z - firstProjected.z;
            if (deltaDepth == 0.0f) continue;

            double progress = (Camera.PROJECTION_Z_NEAR - firstProjected.z) / deltaDepth;
            Vec3 clippedVertex = vertices[edge[0]].lerp(vertices[edge[1]], progress);
            includeProjected(bounds, calcWorld2ScreenRaw(clippedVertex));
        }

        return Float.isFinite(bounds[0]) ? bounds : null;
    }

    private static void includeProjected(float[] bounds, Vector3f projected) {
        if (!Float.isFinite(projected.x) || !Float.isFinite(projected.y)) return;

        bounds[0] = Math.min(bounds[0], projected.x);
        bounds[1] = Math.min(bounds[1], projected.y);
        bounds[2] = Math.max(bounds[2], projected.x);
        bounds[3] = Math.max(bounds[3], projected.y);
    }

    /**
     * 计算世界坐标处的透视 UI 缩放。
     *
     * @param pos 目标位置
     * @return 透视 UI 缩放；无效深度返回 0
     */
    public static float calcScale(Vec3 pos) {
        CameraRenderState cameraState = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        Vector3f cameraRelativePos = pos.subtract(cameraState.pos).toVector3f();
        float depth = -cameraState.viewRotationMatrix.transformPosition(cameraRelativePos, new Vector3f()).z;
        if (depth < Camera.PROJECTION_Z_NEAR) return 0.0f;

        return LuminRenderSystem.getScaledHeight() * cameraState.projectionMatrix.m11()
                / (2.0f * depth * REFERENCE_PIXELS_PER_WORLD_UNIT);
    }

}
