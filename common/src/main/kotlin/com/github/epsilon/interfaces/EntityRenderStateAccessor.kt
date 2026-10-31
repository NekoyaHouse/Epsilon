package com.github.epsilon.interfaces

import net.minecraft.world.entity.Entity

interface EntityRenderStateAccessor {

    fun `epsilon$getEntity`(): Entity

    fun `epsilon$setEntity`(entity: Entity)

}
