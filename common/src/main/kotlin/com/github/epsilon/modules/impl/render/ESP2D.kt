package com.github.epsilon.modules.impl.render

import com.github.epsilon.events.impl.Render2DEvent
import com.github.epsilon.graphics.LuminRenderSystem
import com.github.epsilon.graphics.renderers.RectRenderer
import com.github.epsilon.managers.FriendManager
import com.github.epsilon.managers.HealthManager
import com.github.epsilon.managers.target.TargetManager
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.settings.Setting
import com.github.epsilon.settings.getValue
import com.github.epsilon.settings.impl.BoolSetting
import com.github.epsilon.settings.impl.ColorSetting
import com.github.epsilon.settings.impl.DoubleSetting
import com.github.epsilon.utils.render.WorldToScreen
import com.github.epsilon.utils.runSafe
import com.github.epsilon.utils.world
import com.google.common.base.Suppliers
import net.minecraft.client.Camera
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.awt.Color
import java.util.function.Supplier

object ESP2D : Module("ESP 2D", Category.RENDER) {

    private val AABB_EDGES = arrayOf(
        intArrayOf(0, 1), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(6, 7),
        intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7),
        intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
    )

    private val players by boolSetting("Players", true)
    private val friends by boolSetting("Friends", true)
    private val creatures by boolSetting("Creatures", false)
    private val monsters by boolSetting("Monsters", false)
    private val ambients by boolSetting("Ambients", false)
    private val others by boolSetting("Others", false)
    private val renderHealth by boolSetting("Render Health", true)
    private val healthBarWidth by doubleSetting(
        "Health Bar Width", 2.0, 0.5, 6.0, 0.5, Setting.Dependency { renderHealth }
    )
    private val healthBarOutline by boolSetting(
        "Health Bar Outline", true, Setting.Dependency { renderHealth }
    )
    private val healthBarOutlineWidth by doubleSetting(
        "Health Bar Outline Width", 1.0, 0.5, 3.0, 0.5,
        Setting.Dependency { renderHealth && healthBarOutline }
    )
    private val renderBox by boolSetting("Render Box", true)
    private val boxOutline by boolSetting("Box Outline", true, Setting.Dependency { renderBox })

    private val playersColor by colorSetting("Players Color", Color(0xFF9200), false)
    private val friendsColor by colorSetting("Friends Color", Color(0x30FF00), false)
    private val creaturesColor by colorSetting("Creatures Color", Color(0xA0A4A6), false)
    private val monstersColor by colorSetting("Monsters Color", Color(0xFF0000), false)
    private val ambientsColor by colorSetting("Ambients Color", Color(0x7B00FF), false)
    private val othersColor by colorSetting("Others Color", Color(0xFF0062), false)
    private val healthColor by colorSetting(
        "Health Color", Color(0x2FFF00), false, Setting.Dependency { renderHealth }
    )

    private val rectRendererSupplier: Supplier<RectRenderer> = Suppliers.memoize { RectRenderer.create() }

    init {
        handler<Render2DEvent.Level> { onRender2D() }
    }

    private fun onRender2D() {
        runSafe {
            val rectRenderer = rectRendererSupplier.get()
            val partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)
            val screenWidth = LuminRenderSystem.getScaledWidth()
            val screenHeight = LuminRenderSystem.getScaledHeight()

            for (entity in world.entitiesForRendering()) {
                if (entity !is LivingEntity || !shouldRender(entity)) continue

                val renderPosition = entity.getPosition(partialTick)
                val box = entity.boundingBox.move(renderPosition.subtract(entity.position()))
                val screenBounds = projectBox(box, screenWidth, screenHeight)
                if (screenBounds == null) continue

                val x = screenBounds[0]
                val y = screenBounds[1]
                val endX = screenBounds[2]
                val endY = screenBounds[3]

                if (renderBox) {
                    if (boxOutline) {
                        val black = Color.BLACK
                        rectRenderer.addRect(x - 1.0f, y, 1.5f, endY - y + 0.5f, black)
                        rectRenderer.addRect(x - 1.0f, y - 0.5f, endX - x + 1.5f, 1.0f, black)
                        rectRenderer.addRect(endX - 1.0f, y, 1.5f, endY - y + 0.5f, black)
                        rectRenderer.addRect(x - 1.0f, endY - 1.0f, endX - x + 1.5f, 1.5f, black)
                    }

                    val color = getEntityColor(entity)
                    drawSolidBox(rectRenderer, x, y, endX, endY, color)
                }

                if (renderHealth) {
                    drawHealthBar(rectRenderer, entity, x, y, endY)
                }
            }

            rectRenderer.drawAndClear()
        }
    }

    private fun projectBox(box: AABB, screenWidth: Float, screenHeight: Float): FloatArray? {
        val vertices = arrayOfNulls<Vec3>(8)
        val projectedVertices = arrayOfNulls<Vector3f>(8)
        val bounds = floatArrayOf(
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        )

        for (vertex in 0 until 8) {
            val worldVertex = Vec3(
                if (vertex and 1 == 0) box.minX else box.maxX,
                if (vertex and 2 == 0) box.minY else box.maxY,
                if (vertex and 4 == 0) box.minZ else box.maxZ
            )
            val projected = WorldToScreen.calcWorld2ScreenRaw(worldVertex)
            vertices[vertex] = worldVertex
            projectedVertices[vertex] = projected

            if (projected.z >= Camera.PROJECTION_Z_NEAR) {
                includeProjected(bounds, projected)
            }
        }

        // 贴近实体时 AABB 会跨过近裁剪面，需要把边与近裁剪面的交点也纳入屏幕边界。
        for (edge in AABB_EDGES) {
            val firstProjected = projectedVertices[edge[0]]!!
            val secondProjected = projectedVertices[edge[1]]!!
            val firstVisible = firstProjected.z >= Camera.PROJECTION_Z_NEAR
            val secondVisible = secondProjected.z >= Camera.PROJECTION_Z_NEAR
            if (firstVisible == secondVisible) continue

            val deltaDepth = secondProjected.z - firstProjected.z
            if (deltaDepth == 0.0f) continue

            val progress = ((Camera.PROJECTION_Z_NEAR - firstProjected.z) / deltaDepth).toDouble()
            val clippedVertex = vertices[edge[0]]!!.lerp(vertices[edge[1]]!!, progress)
            includeProjected(bounds, WorldToScreen.calcWorld2ScreenRaw(clippedVertex))
        }

        if (!bounds[0].isFinite() ||
            bounds[2] < 0.0f || bounds[3] < 0.0f ||
            bounds[0] > screenWidth || bounds[1] > screenHeight
        ) {
            return null
        }

        bounds[0] = Mth.clamp(bounds[0], 0.0f, screenWidth)
        bounds[1] = Mth.clamp(bounds[1], 0.0f, screenHeight)
        bounds[2] = Mth.clamp(bounds[2], 0.0f, screenWidth)
        bounds[3] = Mth.clamp(bounds[3], 0.0f, screenHeight)
        return bounds
    }

    private fun includeProjected(bounds: FloatArray, projected: Vector3f) {
        if (!projected.x.isFinite() || !projected.y.isFinite()) return

        bounds[0] = Math.min(bounds[0], projected.x)
        bounds[1] = Math.min(bounds[1], projected.y)
        bounds[2] = Math.max(bounds[2], projected.x)
        bounds[3] = Math.max(bounds[3], projected.y)
    }

    private fun shouldRender(entity: Entity): Boolean {
        if (!entity.isAlive || entity.isSpectator) return false

        if (entity is Player) {
            if (entity === mc.player) return false
            if (FriendManager.isFriend(entity) || TargetManager.INSTANCE.isSameTeam(entity))
                return friends
            return players
        }

        return when (entity.type.category) {
            MobCategory.CREATURE, MobCategory.WATER_CREATURE, MobCategory.AXOLOTLS,
            MobCategory.UNDERGROUND_WATER_CREATURE -> creatures

            MobCategory.MONSTER -> monsters
            MobCategory.AMBIENT, MobCategory.WATER_AMBIENT -> ambients
            else -> others
        }
    }

    private fun getEntityColor(entity: LivingEntity): Color {
        if (entity is Player) {
            if (FriendManager.isFriend(entity)) return friendsColor
            return playersColor
        }

        return when (entity.type.category) {
            MobCategory.CREATURE, MobCategory.WATER_CREATURE, MobCategory.AXOLOTLS,
            MobCategory.UNDERGROUND_WATER_CREATURE -> creaturesColor

            MobCategory.MONSTER -> monstersColor
            MobCategory.AMBIENT, MobCategory.WATER_AMBIENT -> ambientsColor
            else -> othersColor
        }
    }

    private fun drawSolidBox(rectRenderer: RectRenderer, x: Float, y: Float, endX: Float, endY: Float, color: Color) {
        rectRenderer.addRect(x - 0.5f, y, 0.5f, endY - y, color)
        rectRenderer.addRect(x, endY - 0.5f, endX - x, 0.5f, color)
        rectRenderer.addRect(x - 0.5f, y, endX - x + 0.5f, 0.5f, color)
        rectRenderer.addRect(endX - 0.5f, y, 0.5f, endY - y, color)
    }

    private fun drawHealthBar(rectRenderer: RectRenderer, entity: LivingEntity, x: Float, y: Float, endY: Float) {
        val height = endY - y
        if (height <= 0.0f) return

        val health = HealthManager.INSTANCE.getHealth(entity)
        val maxHealth = Math.max(1.0f, entity.maxHealth + Math.max(0.0f, entity.absorptionAmount))
        val healthRatio = Mth.clamp(health / maxHealth, 0.0f, 1.0f)
        val fillY = endY - height * healthRatio

        val distanceScale = height / 45.0f
        val width = healthBarWidth.toFloat() * distanceScale
        val gap = 3.0f * distanceScale
        val outlineWidth = if (healthBarOutline) healthBarOutlineWidth.toFloat() * distanceScale else 0.0f
        val barX = x - gap - outlineWidth - width

        if (healthBarOutline) {
            rectRenderer.addRect(
                barX - outlineWidth, y - outlineWidth,
                width + outlineWidth * 2.0f, height + outlineWidth * 2.0f, Color.BLACK
            )
        } else {
            rectRenderer.addRect(barX, y, width, height, Color.BLACK)
        }

        rectRenderer.addRect(barX, fillY, width, endY - fillY, healthColor)
    }

}
