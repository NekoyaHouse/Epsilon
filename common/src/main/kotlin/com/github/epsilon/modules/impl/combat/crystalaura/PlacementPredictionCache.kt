package com.github.epsilon.modules.impl.combat.crystalaura

import net.minecraft.world.phys.AABB

internal class PlacementPredictionCache<K : Any>(
    private val ticks: Int,
    private val initialBounds: (K) -> AABB,
    private val advance: (K, AABB) -> AABB?
) {
    private val paths = mutableMapOf<K, List<AABB>>()

    init {
        require(ticks >= 0)
    }

    private fun path(entity: K): List<AABB> = paths.getOrPut(entity) {
        val result = ArrayList<AABB>(ticks + 1)
        var box = initialBounds(entity)
        result.add(box)
        for (tick in 1..ticks) {
            box = advance(entity, box) ?: break
            result.add(box)
        }
        result
    }

    fun bounds(entity: K): AABB = path(entity).last()

    fun intersects(entity: K, box: AABB): Boolean {
        val path = path(entity)
        return if (path.size == 1) path[0].intersects(box)
        else (1 until path.size).any { path[it].intersects(box) }
    }
}