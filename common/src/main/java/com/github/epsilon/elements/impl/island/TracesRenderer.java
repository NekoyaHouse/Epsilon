package com.github.epsilon.elements.impl.island;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.utils.render.WorldToScreen;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class TracesRenderer {

    private static final float SCREEN_EDGE_MARGIN = 6.0f;
    private static final float ISLAND_EDGE_GAP = 3.0f;
    private static final float TARGET_HEIGHT_FACTOR = 0.5f;

    private TracesRenderer() {
    }

    public static int render(RectRenderer renderer, float islandX, float islandY, float islandWidth, float islandHeight, double maxDistance, float lineWidth, Color lineColor) {
        float screenWidth = LuminRenderSystem.getScaledWidth();
        float screenHeight = LuminRenderSystem.getScaledHeight();
        if (screenWidth <= 1.0f || screenHeight <= 1.0f) return 0;

        float centerX = islandX + islandWidth * 0.5f;
        float centerY = islandY + islandHeight * 0.5f;
        float halfWidth = Math.max(0.0f, islandWidth * 0.5f);
        float halfHeight = Math.max(0.0f, islandHeight * 0.5f);
        double maxDistanceSquared = Math.max(0.0, maxDistance * maxDistance);
        float partialTick = mc.level.tickRateManager().isFrozen() ? 1.0f : mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        int rendered = 0;

        for (Player player : mc.level.players()) {
            if (!shouldRender(player, maxDistanceSquared)) continue;

            Vec3 position = player.getPosition(partialTick).add(0.0, player.getBbHeight() * TARGET_HEIGHT_FACTOR, 0.0);
            Vector3f projected = WorldToScreen.calcWorld2ScreenRaw(position);
            ScreenPoint target = resolveTarget(projected, screenWidth, screenHeight);
            if (target == null || isInsideIsland(target.x(), target.y(), centerX, centerY, halfWidth, halfHeight)) {
                continue;
            }

            float[] start = resolveIslandEdge(centerX, centerY, halfWidth, halfHeight, target.x(), target.y());
            float lineLength = distance(start[0], start[1], target.x(), target.y());
            if (!Float.isFinite(lineLength) || lineLength <= ISLAND_EDGE_GAP) continue;

            renderer.addLine(start[0], start[1], target.x(), target.y(), lineWidth, lineColor);
            rendered++;
        }

        return rendered;
    }

    private static boolean shouldRender(Player player, double maxDistanceSquared) {
        if (player == null || !player.isAlive() || player.isRemoved() || player.isSpectator()) return false;
        if (player == mc.player) return false;
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || !connection.getOnlinePlayerIds().contains(player.getUUID())) return false;
        return mc.player.distanceToSqr(player) <= maxDistanceSquared;
    }

    private static ScreenPoint resolveTarget(Vector3f projected, float screenWidth, float screenHeight) {
        if (projected == null || !Float.isFinite(projected.x) || !Float.isFinite(projected.y) || !Float.isFinite(projected.z))
            return null;
        boolean behind = projected.z < Camera.PROJECTION_Z_NEAR;
        boolean outside = projected.x < SCREEN_EDGE_MARGIN || projected.x > screenWidth - SCREEN_EDGE_MARGIN || projected.y < SCREEN_EDGE_MARGIN || projected.y > screenHeight - SCREEN_EDGE_MARGIN;
        if (!behind && !outside) return new ScreenPoint(projected.x, projected.y);
        return null;
    }

    private static boolean isInsideIsland(float x, float y, float centerX, float centerY, float halfWidth, float halfHeight) {
        return Math.abs(x - centerX) <= halfWidth && Math.abs(y - centerY) <= halfHeight;
    }

    private static float[] resolveIslandEdge(float centerX, float centerY, float halfWidth, float halfHeight, float targetX, float targetY) {
        float directionX = targetX - centerX;
        float directionY = targetY - centerY;
        float length = distance(0.0f, 0.0f, directionX, directionY);
        if (length <= 0.001f) return new float[]{centerX, centerY};
        float unitX = directionX / length;
        float unitY = directionY / length;
        float edgeDistanceX = Math.abs(unitX) <= 0.001f ? Float.POSITIVE_INFINITY : halfWidth / Math.abs(unitX);
        float edgeDistanceY = Math.abs(unitY) <= 0.001f ? Float.POSITIVE_INFINITY : halfHeight / Math.abs(unitY);
        float edgeDistance = Math.min(edgeDistanceX, edgeDistanceY) + ISLAND_EDGE_GAP;
        return new float[]{centerX + unitX * edgeDistance, centerY + unitY * edgeDistance};
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        return (float) Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
    }

    private record ScreenPoint(float x, float y) {
    }

}
