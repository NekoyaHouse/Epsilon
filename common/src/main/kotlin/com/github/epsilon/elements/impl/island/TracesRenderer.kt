package com.github.epsilon.elements.impl.island

import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.RectRenderer
import com.github.epsilon.kernel.jvm.NonNullContext
import com.github.epsilon.utils.render.WorldToScreen
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.world.entity.player.Player
import org.joml.Vector3f
import java.awt.Color
import kotlin.math.abs
import kotlin.math.sqrt

object TracesRenderer {

    private const val SCREEN_EDGE_MARGIN = 6.0f
    private const val ISLAND_EDGE_GAP = 3.0f
    private const val TARGET_HEIGHT_FACTOR = 0.5f

    context(nn: NonNullContext)
    fun render(
        renderer: RectRenderer,
        islandX: Float,
        islandY: Float,
        islandWidth: Float,
        islandHeight: Float,
        maxDistance: Double,
        lineWidth: Float,
        lineColor: Color,
    ): Int {
        val screenWidth = LuminRenderSystem.getScaledWidth()
        val screenHeight = LuminRenderSystem.getScaledHeight()
        if (screenWidth <= 1.0f || screenHeight <= 1.0f) return 0

        val self = nn.player
        val connection = nn.connection

        val centerX = islandX + islandWidth * 0.5f
        val centerY = islandY + islandHeight * 0.5f
        val halfWidth = maxOf(0.0f, islandWidth * 0.5f)
        val halfHeight = maxOf(0.0f, islandHeight * 0.5f)
        val maxDistanceSquared = maxOf(0.0, maxDistance * maxDistance)
        val partialTick = if (nn.level.tickRateManager().isFrozen) 1.0f
        else nn.mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        var rendered = 0

        for (player in nn.level.players()) {
            if (!shouldRender(player, maxDistanceSquared, self, connection)) continue

            val position =
                player.getPosition(partialTick).add(0.0, (player.bbHeight * TARGET_HEIGHT_FACTOR).toDouble(), 0.0)
            val projected = WorldToScreen.calcWorld2ScreenRaw(position)
            val target = resolveTarget(projected, screenWidth, screenHeight)
            if (target == null || isInsideIsland(target.x, target.y, centerX, centerY, halfWidth, halfHeight)) {
                continue
            }

            val start = resolveIslandEdge(centerX, centerY, halfWidth, halfHeight, target.x, target.y)
            val lineLength = distance(start[0], start[1], target.x, target.y)
            if (!lineLength.isFinite() || lineLength <= ISLAND_EDGE_GAP) continue

            renderer.addLine(start[0], start[1], target.x, target.y, lineWidth, lineColor)
            rendered++
        }

        return rendered
    }

    private fun shouldRender(
        player: Player?,
        maxDistanceSquared: Double,
        self: Player,
        connection: ClientPacketListener?
    ): Boolean {
        if (player == null || !player.isAlive || player.isRemoved || player.isSpectator) return false
        if (player === self) return false
        if (connection == null || !connection.onlinePlayerIds.contains(player.uuid)) return false
        return self.distanceToSqr(player) <= maxDistanceSquared
    }

    private fun resolveTarget(projected: Vector3f?, screenWidth: Float, screenHeight: Float): ScreenPoint? {
        if (projected == null || !projected.x.isFinite() || !projected.y.isFinite() || !projected.z.isFinite()) {
            return null
        }
        val behind = projected.z < Camera.PROJECTION_Z_NEAR
        val outside = projected.x < SCREEN_EDGE_MARGIN || projected.x > screenWidth - SCREEN_EDGE_MARGIN ||
                projected.y < SCREEN_EDGE_MARGIN || projected.y > screenHeight - SCREEN_EDGE_MARGIN
        if (!behind && !outside) return ScreenPoint(projected.x, projected.y)
        return null
    }

    private fun isInsideIsland(
        x: Float,
        y: Float,
        centerX: Float,
        centerY: Float,
        halfWidth: Float,
        halfHeight: Float
    ): Boolean {
        return abs(x - centerX) <= halfWidth && abs(y - centerY) <= halfHeight
    }

    private fun resolveIslandEdge(
        centerX: Float,
        centerY: Float,
        halfWidth: Float,
        halfHeight: Float,
        targetX: Float,
        targetY: Float,
    ): FloatArray {
        val directionX = targetX - centerX
        val directionY = targetY - centerY
        val length = distance(0.0f, 0.0f, directionX, directionY)
        if (length <= 0.001f) return floatArrayOf(centerX, centerY)
        val unitX = directionX / length
        val unitY = directionY / length
        val edgeDistanceX = if (abs(unitX) <= 0.001f) Float.POSITIVE_INFINITY else halfWidth / abs(unitX)
        val edgeDistanceY = if (abs(unitY) <= 0.001f) Float.POSITIVE_INFINITY else halfHeight / abs(unitY)
        val edgeDistance = minOf(edgeDistanceX, edgeDistanceY) + ISLAND_EDGE_GAP
        return floatArrayOf(centerX + unitX * edgeDistance, centerY + unitY * edgeDistance)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).toDouble()).toFloat()
    }

    private data class ScreenPoint(val x: Float, val y: Float)

}
