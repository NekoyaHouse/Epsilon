package com.github.epsilon.managers

import com.github.epsilon.Constants
import com.github.epsilon.events.api.handler
import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.bus.EventPriority
import com.github.epsilon.events.impl.ClientTickEvent
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.ArrayList
import java.util.HashMap

object ExtrapolationManager {

    private var motions: Map<AbstractClientPlayer, List<Vec3>> = HashMap()

    init {
        EventBus.handler<ClientTickEvent.Post>(this, EventPriority.HIGHEST, alwaysListening = true) { onClientTick() }
    }

    private fun onClientTick() {
        val level = Constants.client.level
        if (Constants.client.player == null || level == null || level.players().isEmpty()) return

        val newMotions = HashMap<AbstractClientPlayer, List<Vec3>>()

        for (player in level.players()) {
            val vec = player.position().subtract(player.xo, player.yo, player.zo)

            val existing = motions[player]
            if (existing == null) {
                val v = ArrayList<Vec3>()
                v.add(vec)
                newMotions[player] = v
                continue
            }

            val v = ArrayList(existing)
            v.add(0, vec)

            if (v.size > 20) {
                v.subList(20, v.size).clear()
            }

            newMotions[player] = v
        }

        motions = newMotions
    }

    fun extrapolateMap(
        old: MutableMap<AbstractClientPlayer, AABB>,
        extrapolation: EpicInterface<AbstractClientPlayer, Int>,
        smoothening: EpicInterface<AbstractClientPlayer, Int>,
    ) {
        old.clear()

        for (entry in motions.entries) {
            val player = entry.key
            val m = entry.value
            old[player] = extrapolate(player, m, extrapolation.get(player), smoothening.get(player))
        }
    }

    fun extrapolate(player: AbstractClientPlayer, extrapolation: Int, smoothening: Int): AABB? {
        val m = motions[player] ?: return null
        return extrapolate(player, m, extrapolation, smoothening)
    }

    fun extrapolate(player: AbstractClientPlayer, m: List<Vec3>, extrapolation: Int, smoothening: Int): AABB {
        val motion = getMotion(m, smoothening)

        var x = motion.x
        var y = motion.y
        var z = motion.z

        val stepHeight = 0.6

        var box = AABB(
            player.x - 0.3, player.y, player.z - 0.3,
            player.x + 0.3, player.y + (player.boundingBox.maxY - player.boundingBox.minY), player.z + 0.3
        )
        var onGround = inside(player, box.move(0.0, -0.04, 0.0))

        for (i in 0 until extrapolation) {
            val level = Constants.client.level!!
            val list: List<VoxelShape> = level.getEntityCollisions(player, box.contract(x, y, z))
            val movement = Vec3(x, y, z)
            var vec3d = if (movement.lengthSqr() == 0.0) {
                movement
            } else {
                Entity.collideBoundingBox(player, movement, box, level, list)
            }

            val canStep = (onGround || (y < 0 && vec3d.y != y)) && (vec3d.x != x || vec3d.z != z)

            if (canStep) {
                var vec3d2 = Entity.collideBoundingBox(player, Vec3(x, stepHeight, z), box, level, list)
                val vec3d3 = Entity.collideBoundingBox(
                    player, Vec3(0.0, stepHeight, 0.0), box.contract(x, 0.0, z), level, list
                )
                if (vec3d3.y < stepHeight) {
                    val vec3d4 = Entity.collideBoundingBox(
                        player, Vec3(movement.x, 0.0, movement.z), box.move(vec3d3), level, list
                    ).add(vec3d3)
                    if (vec3d4.horizontalDistanceSqr() > vec3d2.horizontalDistanceSqr()) {
                        vec3d2 = vec3d4
                    }
                }

                if (vec3d2.horizontalDistanceSqr() > vec3d.horizontalDistanceSqr()) {
                    val vec = vec3d2.add(
                        Entity.collideBoundingBox(
                            player, Vec3(0.0, -vec3d2.y + movement.y, 0.0), box.move(vec3d2), level, list
                        )
                    )
                    box = box.move(vec)
                    onGround = true
                    continue
                }
            }

            box = box.move(vec3d)
            onGround = inside(player, box.move(0.0, -0.04, 0.0))

            if (onGround) y = 0.0
            y = (y - 0.08) * 0.98
        }

        return box
    }

    private fun inside(player: Player, box: AABB): Boolean {
        return Constants.client.level!!.getBlockCollisions(player, box).iterator().hasNext()
    }

    private fun getMotion(vecs: List<Vec3>, max: Int): Vec3 {
        var avg = Vec3(0.0, (vecs.first().y - 0.08) * 0.98, 0.0)

        val s = Math.min(vecs.size, max)
        for (i in 0 until s) {
            avg = avg.add(vecs[i].x, 0.0, vecs[i].z)
        }

        return avg.multiply(1.0 / s.toFloat().toDouble(), 1.0, 1.0 / s.toFloat().toDouble())
    }

    fun interface EpicInterface<T, E> {
        fun get(t: T): E
    }

}
