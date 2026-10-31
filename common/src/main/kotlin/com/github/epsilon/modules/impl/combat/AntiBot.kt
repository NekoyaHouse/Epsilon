package com.github.epsilon.modules.impl.combat

import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import net.minecraft.world.entity.Entity

object AntiBot : Module("Anti Bot", Category.COMBAT) {

    fun isBot(entity: Entity): Boolean {
        return isEnabled && mc.connection?.onlinePlayerIds?.contains(entity.uuid) == false
    }

}
